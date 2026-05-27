// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
//
// MainActivity — UI for per-SIM IMS feature override.
//
// Replicates the role of stock LG HiddenMenu's "Activate Vo Service" screen:
// shows a card per active subscription with checkboxes for VoLTE, ViLTE,
// VoWiFi, ViWiFi, RCS. Apply persists the choice keyed by ICCID; Reset
// drops the override and reverts to defaults (everything except RCS).
//
// The Activity does not depend on the LgeImsConfigBridgeService running:
// it writes to the same SharedPreferences directly and triggers the
// service via startService() to apply.

package com.lineageos.lgeimsconfigbridge;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import com.lineageos.lgeimsconfigbridge.LgeImsConfigBridgeService.VoConfig;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onResume() {
        super.onResume();
        rebuild();
    }

    private void rebuild() {
        LinearLayout container = findViewById(R.id.slots_container);
        TextView hint = findViewById(R.id.hint);
        container.removeAllViews();

        SubscriptionManager sm = getSystemService(SubscriptionManager.class);
        TelephonyManager tm = getSystemService(TelephonyManager.class);
        // Must match the Service's storage choice (DE) so both processes
        // read/write the same shared_prefs file. Service is directBootAware
        // and uses createDeviceProtectedStorageContext(); we mirror that.
        SharedPreferences prefs = createDeviceProtectedStorageContext()
                .getSharedPreferences(LgeImsConfigBridgeService.PREFS_NAME, MODE_PRIVATE);

        List<SubscriptionInfo> subs = null;
        if (sm != null) {
            try {
                subs = sm.getActiveSubscriptionInfoList();
            } catch (SecurityException ignored) { }
        }

        if (subs == null || subs.isEmpty()) {
            hint.setText(R.string.hint_no_sims);
            return;
        }
        hint.setText(R.string.hint_default);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (SubscriptionInfo info : subs) {
            View card = inflater.inflate(R.layout.slot_card, container, false);
            bindCard(card, info, tm, prefs);
            container.addView(card);
        }
    }

    private void bindCard(View card, SubscriptionInfo info, TelephonyManager tm,
                          SharedPreferences prefs) {
        int slot = info.getSimSlotIndex();
        String iccid = info.getIccId();
        String mccmnc = nullToEmpty(info.getMccString())
                + nullToEmpty(info.getMncString());
        String operator = "";
        if (tm != null) {
            try {
                TelephonyManager perSub = tm.createForSubscriptionId(info.getSubscriptionId());
                if (perSub != null) operator = nullToEmpty(perSub.getSimOperatorName());
            } catch (SecurityException ignored) { }
        }
        if (TextUtils.isEmpty(operator)) {
            CharSequence carrier = info.getCarrierName();
            operator = carrier != null ? carrier.toString() : getString(R.string.operator_unknown);
        }

        TextView title  = card.findViewById(R.id.slot_title);
        TextView icc    = card.findViewById(R.id.iccid);
        TextView mm     = card.findViewById(R.id.mccmnc);
        CheckBox cbVolte  = card.findViewById(R.id.cb_volte);
        CheckBox cbVilte  = card.findViewById(R.id.cb_vilte);
        CheckBox cbVowifi = card.findViewById(R.id.cb_vowifi);
        CheckBox cbViwifi = card.findViewById(R.id.cb_viwifi);
        CheckBox cbRcs    = card.findViewById(R.id.cb_rcs);
        Button btnApply = card.findViewById(R.id.btn_apply);
        Button btnReset = card.findViewById(R.id.btn_reset);

        title.setText(getString(R.string.slot_label, slot, operator));
        icc.setText(getString(R.string.iccid_label,
                TextUtils.isEmpty(iccid) ? getString(R.string.iccid_unknown) : iccid));
        mm.setText(getString(R.string.mccmnc_label,
                TextUtils.isEmpty(mccmnc) ? "?" : mccmnc));

        VoConfig current = readEffective(prefs, iccid);
        cbVolte.setChecked(current.volte);
        cbVilte.setChecked(current.vilte);
        cbVowifi.setChecked(current.vowifi);
        cbViwifi.setChecked(current.viwifi);
        cbRcs.setChecked(current.rcs);

        // ICCID can be empty if the SIM didn't expose it yet — disable the
        // buttons rather than persist under a blank key.
        boolean haveIccid = !TextUtils.isEmpty(iccid);
        btnApply.setEnabled(haveIccid);
        btnReset.setEnabled(haveIccid);

        btnApply.setOnClickListener(v -> {
            VoConfig cfg = new VoConfig();
            cfg.volte  = cbVolte.isChecked();
            cfg.vilte  = cbVilte.isChecked();
            cfg.vowifi = cbVowifi.isChecked();
            cfg.viwifi = cbViwifi.isChecked();
            cfg.rcs    = cbRcs.isChecked();
            prefs.edit().putInt(
                    LgeImsConfigBridgeService.PREF_KEY_PREFIX + iccid,
                    cfg.toBits()).apply();
            kickService();
            Toast.makeText(this, R.string.toast_applied, Toast.LENGTH_SHORT).show();
        });

        btnReset.setOnClickListener(v -> {
            prefs.edit().remove(
                    LgeImsConfigBridgeService.PREF_KEY_PREFIX + iccid).apply();
            VoConfig def = VoConfig.defaults();
            cbVolte.setChecked(def.volte);
            cbVilte.setChecked(def.vilte);
            cbVowifi.setChecked(def.vowifi);
            cbViwifi.setChecked(def.viwifi);
            cbRcs.setChecked(def.rcs);
            kickService();
            Toast.makeText(this, R.string.toast_reset, Toast.LENGTH_SHORT).show();
        });
    }

    private VoConfig readEffective(SharedPreferences prefs, String iccid) {
        if (!TextUtils.isEmpty(iccid)) {
            String key = LgeImsConfigBridgeService.PREF_KEY_PREFIX + iccid;
            if (prefs.contains(key)) {
                int bits = prefs.getInt(key, -1);
                if (bits >= 0) return VoConfig.fromBits(bits);
            }
        }
        return VoConfig.defaults();
    }

    private void kickService() {
        try {
            startService(new Intent(this, LgeImsConfigBridgeService.class));
        } catch (Exception e) {
            // Service is android:persistent and runs as system uid; this should
            // not throw, but if a future Android tightens fg-service rules and
            // this becomes restricted, the user reboots and the bridge picks
            // up the override on its next syncAllSlots(). Override is already
            // in SharedPreferences at this point.
        }
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
