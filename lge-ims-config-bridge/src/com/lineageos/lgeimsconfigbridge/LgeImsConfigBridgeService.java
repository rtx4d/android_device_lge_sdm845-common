// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
//
// LgeImsConfigBridgeService — applies per-SIM IMS feature toggles.
//
// Empirical evidence on this device (2026-05-27 LineageOS, post-stock):
//   * The per-slot persist.product.lge.support{volte,vt,vowifi,viwifi}[.sim2]
//     sysprops are NOT consulted by the running LG IMS stack at runtime.
//     Setting them to "0" did not stop active IMS, even after airplane
//     toggle + full reboot. They appear to be informational hints only,
//     useful (maybe) on stock framework but not on our porting surface.
//   * The com.lge.action.VOLTE_CHANGED_INFO sticky broadcast IS received by
//     Ims6.app.StateInfoChangedReceiver (verified in logs), but Ims6 treats
//     it as a policy hint and does not deregister on it.
//   * The actual per-subscription gate that matters is the AOSP MmTel
//     provisioning API (ImsMmTelManager.setVoLteSettingEnabled etc.),
//     plus Settings.Global VOLTE_VT_ENABLED / WFC_IMS_ENABLED for legacy
//     consumers. These are what Settings.app and the Phone app toggle
//     when the user flips the carrier-side switches.
//
// So this service:
//   1. Sets globals persist.product.lge.ims.volte_open / dualvolte once.
//      (Empirically required for the LG stack to start at all.)
//   2. For each active SIM, applies the user's per-ICCID override (or
//      defaults: all-on except RCS) via ImsMmTelManager + Settings.Global
//      writes. THIS is what actually gates per-SIM behavior.
//   3. Sends sticky com.lge.action.VOLTE_CHANGED_INFO so any LG receiver
//      that listens (Ims6 does) sees a policy update. Cosmetic on this
//      port but harmless and matches stock wire format.
//
// The per-slot persist.product.lge.support* sysprops are NO LONGER
// written. They were noise.

package com.lineageos.lgeimsconfigbridge;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.SystemProperties;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.ProvisioningManager;
import android.telephony.ims.feature.MmTelFeature;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.text.TextUtils;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class LgeImsConfigBridgeService extends Service {
    private static final String TAG = "LgeImsCfg";

    // Globals required by the LG IMS stack to start at all. Set on every
    // boot regardless of per-SIM config; not user-configurable. These two
    // are the ones empirically required — the stack will not enter the
    // VoLTE-capable state without them. Stock LG ships them in
    // /vendor/build.prop / /product/build.prop persistently; we set them
    // dynamically so the module remains self-contained.
    private static final String PROP_VOLTE_OPEN = "persist.product.lge.ims.volte_open";
    private static final String PROP_DUALVOLTE  = "persist.vendor.lge.ims.dualvolte";

    // ---------- broadcast wire format ----------
    static final String ACTION_VOLTE_CHANGED_INFO = "com.lge.action.VOLTE_CHANGED_INFO";
    static final String ACTION_RCS_CHANGED_INFO   = "com.lge.action.RCS_CHANGED_INFO";

    // Stock IRCM uses LGDataPhoneEvents.EVENT_DATA_RESERVED_04 (0x40000000) |
    // 0x01000000 (FLAG_RECEIVER_INCLUDE_BACKGROUND). Same bit pattern as
    // FLAG_RECEIVER_FOREGROUND | FLAG_RECEIVER_INCLUDE_BACKGROUND.
    private static final int BROADCAST_FLAGS =
            Intent.FLAG_RECEIVER_FOREGROUND | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND;

    // ---------- shared prefs (UI override) ----------
    static final String PREFS_NAME = "ims_overrides";
    // Per-ICCID key prefix; stored value is bit-packed VoConfig.toBits().
    static final String PREF_KEY_PREFIX = "iccid_";

    // ---------- state ----------
    private SubscriptionManager mSm;
    private TelephonyManager mTm;
    private SubscriptionManager.OnSubscriptionsChangedListener mSubsListener;
    private SharedPreferences mPrefs;

    private final Map<Integer, VoConfig> mLastEmitted = new HashMap<>();
    /** Lazy modem-NV writer (binds to QcRilMsgTunnelService on first use). */
    private LgeModemNvWriter mModemNv;
    /**
     * True once BOOT_COMPLETED arrived. QcrilMsgTunnelService is NOT
     * directBootAware, so any bindService() before user-unlock fails
     * silently and the singleton inside QcRilHook gets stuck with
     * mBound=false forever. Gate modem-NV writes behind this flag and
     * pull the wave back through syncAllSlots() once the flag flips.
     */
    private boolean mBootCompleted;
    /**
     * Per-slot record of the VoConfig we have actually pushed into the
     * modem via OEM_HOOK. Distinct from mLastEmitted (which tracks the
     * last *Java/Telephony* state) because modem-NV writes are gated
     * on BOOT_COMPLETED — the first applyForSlot() at LOCKED_BOOT
     * succeeds for MmTel/sysprops/sticky-broadcasts but is skipped for
     * the modem path; without a separate ledger the second pass would
     * see "no change vs mLastEmitted" and skip the modem too, leaving
     * the modem state perpetually unsynced.
     */
    private final Map<Integer, VoConfig> mLastModemNv = new HashMap<>();
    /**
     * Single-thread executor for modem-NV writes. Must NOT be the main
     * thread because LgSvcCmd's underlying QcRilHook signals readiness
     * via a ServiceConnection callback dispatched on the main thread —
     * blocking the main thread to poll for that readiness creates a
     * deadlock that takes ~16s to time out (8s × two writes) before
     * onServiceConnected finally lands.
     */
    private final java.util.concurrent.ExecutorService mModemNvExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    // ---------- model ----------

    static final class VoConfig {
        boolean volte;
        boolean vilte;
        boolean vowifi;
        boolean viwifi;
        boolean rcs;

        static VoConfig defaults() {
            VoConfig c = new VoConfig();
            // Default-on policy: enable everything except RCS. RCS requires a
            // separate carrier provisioning channel and is rarely useful by
            // default; turning it on without provisioning produces noisy logs.
            c.volte  = true;
            c.vilte  = true;
            c.vowifi = true;
            c.viwifi = true;
            c.rcs    = false;
            return c;
        }

        int toBits() {
            return (volte ? 1 : 0) | (vilte ? 2 : 0) | (vowifi ? 4 : 0)
                    | (viwifi ? 8 : 0) | (rcs ? 16 : 0);
        }

        static VoConfig fromBits(int bits) {
            VoConfig c = new VoConfig();
            c.volte  = (bits & 1)  != 0;
            c.vilte  = (bits & 2)  != 0;
            c.vowifi = (bits & 4)  != 0;
            c.viwifi = (bits & 8)  != 0;
            c.rcs    = (bits & 16) != 0;
            return c;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof VoConfig)) return false;
            VoConfig v = (VoConfig) o;
            return volte == v.volte && vilte == v.vilte && vowifi == v.vowifi
                    && viwifi == v.viwifi && rcs == v.rcs;
        }

        @Override
        public int hashCode() { return toBits(); }

        @Override
        public String toString() {
            return "{volte=" + b(volte) + " vilte=" + b(vilte)
                    + " vowifi=" + b(vowifi) + " viwifi=" + b(viwifi)
                    + " rcs=" + b(rcs) + "}";
        }

        private static String b(boolean v) { return v ? "1" : "0"; }
    }

    // ---------- service lifecycle ----------

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Service onCreate");
        mSm = getSystemService(SubscriptionManager.class);
        mTm = getSystemService(TelephonyManager.class);
        // The service is directBootAware: it starts BEFORE the user unlocks
        // their device, when only Device Encrypted (DE) storage is available.
        // Default getSharedPreferences uses Credential Encrypted (CE) storage,
        // which throws IllegalStateException pre-unlock. We must explicitly
        // use a Context tied to DE storage. Storage path becomes
        // /data/user_de/0/<pkg>/shared_prefs/ instead of /data/user/0/<pkg>/.
        Context de = createDeviceProtectedStorageContext();
        mPrefs = de.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Globals: must be set on every fresh boot regardless of SIMs.
        // Without these the LG IMS stack stays disabled.
        setProp(PROP_VOLTE_OPEN, "1");
        setProp(PROP_DUALVOLTE, "1");

        if (mSm != null) {
            mSubsListener = new SubscriptionManager.OnSubscriptionsChangedListener() {
                @Override
                public void onSubscriptionsChanged() {
                    syncAllSlots();
                }
            };
            mSm.addOnSubscriptionsChangedListener(getMainExecutor(), mSubsListener);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        Log.i(TAG, "Service onStartCommand action=" + action);
        // VoConfigUpdateReceiver forwards stock HiddenMenu's broadcast here.
        // Re-import the XML before the regular sync so any per-SIM choice
        // the user made via HiddenMenu lands in our SharedPreferences.
        if (VoConfigUpdateReceiver.ACTION_VO_CONFIG_UPDATE.equals(action)) {
            importVoConfigXml();
        }
        // BootReceiver tags the BOOT_COMPLETED kick. We use that as our
        // signal that user-unlock happened and CE-encrypted services like
        // QcrilMsgTunnelService can finally serve binds; until then,
        // applyModemNv() bails out so we don't waste 8 seconds polling
        // a tunnel that can't possibly answer.
        if (intent != null && intent.getBooleanExtra("boot_completed", false)) {
            mBootCompleted = true;
            Log.i(TAG, "Boot completed - modem-NV writes now permitted");
        }
        syncAllSlots();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Service onDestroy");
        if (mSm != null && mSubsListener != null) {
            try {
                mSm.removeOnSubscriptionsChangedListener(mSubsListener);
            } catch (Exception ignored) { }
            mSubsListener = null;
        }
        mModemNvExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ---------- core ----------

    private synchronized void syncAllSlots() {
        if (mTm == null) {
            Log.e(TAG, "TelephonyManager unavailable");
            return;
        }
        int slotCount = mTm.getActiveModemCount();
        Set<Integer> active = new HashSet<>();

        List<SubscriptionInfo> subs = null;
        if (mSm != null) {
            try {
                subs = mSm.getActiveSubscriptionInfoList();
            } catch (SecurityException e) {
                Log.e(TAG, "Cannot read subs: " + e.getMessage());
            }
        }

        if (subs != null) {
            for (SubscriptionInfo info : subs) {
                int slot = info.getSimSlotIndex();
                if (slot < 0 || slot >= slotCount) continue;
                active.add(slot);
                applyForSlot(slot, info);
            }
        }

        // Empty slots: no per-sub MmTel call possible (needs valid subId).
        // Settings.Global keys keyed by subId of the previous SIM remain
        // until the next time that subId becomes active again. This mirrors
        // AOSP behavior (Settings.app does the same).
        for (int slot = 0; slot < slotCount; slot++) {
            if (!active.contains(slot)) {
                Log.i(TAG, "[slot " + slot + "] no SIM");
            }
        }
    }

    private void applyForSlot(int slot, SubscriptionInfo info) {
        int subId = info.getSubscriptionId();
        String iccid = nullToEmpty(info.getIccId());
        String mccmnc = nullToEmpty(info.getMccString())
                + nullToEmpty(info.getMncString());

        VoConfig cfg = resolveConfig(iccid);
        Log.i(TAG, "[slot " + slot + " sub=" + subId + "] iccid=" + redact(iccid)
                + " mccmnc=" + mccmnc + " -> " + cfg);

        applyMmTelSettings(subId, cfg);
        applyModemNv(slot, cfg);
        broadcastVolteChanged(slot, cfg);
        VoConfig prev = mLastEmitted.get(slot);
        if (prev == null || prev.rcs != cfg.rcs) {
            broadcastRcsChanged(slot, cfg);
        }
        broadcastSimStateLoaded(slot, subId);
        mLastEmitted.put(slot, cfg);
    }

    /**
     * Push VoLTE/VoWiFi master switches into modem NV via the stock
     * QcRilHook OEM_HOOK channel. This is the modem-side counterpart
     * to applyMmTelSettings(): the latter flips Java/Telephony toggles
     * but does not touch what the modem itself believes about VoLTE
     * support for this subscription. On a fresh ICCID the modem
     * decides whether to advertise VoPS=on and accept the apn=ims
     * dedicated bearer based on what is in modemst1/modemst2 (which
     * survives factory reset and OS reflash). HiddenMenu's Apply on
     * stock LG firmware ultimately routes through this same channel
     * via stock framework.jar's ImsRadioConfigManager — that class is
     * absent in LineageOS so without this call our modem stays at the
     * factory-default-from-the-day-the-phone-was-built state.
     *
     * Runs on a worker thread because LgSvcCmd's underlying QcRilHook
     * binds to QcrilMsgTunnelService asynchronously and signals readiness
     * via a ServiceConnection callback delivered on the *main* thread —
     * if we polled getIQcrilMsgTunnelServiceStatus() from the main
     * thread we'd block the very dispatch loop that needs to run
     * onServiceConnected, and the bind would only complete *after*
     * our timeout had already given up.
     */
    private void applyModemNv(int slot, VoConfig cfg) {
        // Re-enabled 2026-05-29 after the QcRilHook OEM-hook write path
        // was confirmed working with reqId=QCRILHOOK_CMD_SET=593925
        // (legacy variant — see LgeModemNvWriter for the reasoning).
        // Earlier crashes were caused by reqId=QCRILHOOK_NEW_CMD_SET=
        // 593943 which our extracted qcrild has no handler for and
        // wedged the binder transact, eventually leaving modemst1/2 in
        // an inconsistent state across reboots. With the legacy ID the
        // modem replies cleanly (rc=4) and survives reboot.
        if (!mBootCompleted) {
            // Pre-BOOT_COMPLETED: QcrilMsgTunnelService cannot bind yet
            // (it is not directBootAware). The BOOT_COMPLETED kick from
            // BootReceiver will re-trigger syncAllSlots(), and because
            // mLastModemNv (not mLastEmitted) gates modem writes, the
            // delta check below will detect "never written" and push
            // the value through.
            Log.d(TAG, "[slot " + slot + "] applyModemNv deferred: pre-BOOT_COMPLETED");
            return;
        }
        VoConfig prev = mLastModemNv.get(slot);
        boolean volteChanged  = prev == null || prev.volte  != cfg.volte;
        boolean vowifiChanged = prev == null || prev.vowifi != cfg.vowifi;
        if (!volteChanged && !vowifiChanged) {
            return;
        }
        // Snapshot first; cfg may be mutated by the caller before the
        // worker runs, and our delta check above already established
        // intent.
        VoConfig snap = new VoConfig();
        snap.volte = cfg.volte;
        snap.vilte = cfg.vilte;
        snap.vowifi = cfg.vowifi;
        snap.viwifi = cfg.viwifi;
        snap.rcs = cfg.rcs;
        mLastModemNv.put(slot, snap);

        final boolean writeVolte  = volteChanged;
        final boolean writeVowifi = vowifiChanged;
        mModemNvExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (mModemNv == null) {
                    mModemNv = new LgeModemNvWriter(LgeImsConfigBridgeService.this);
                }
                // Note: we don't bracket the writes with getItem reads
                // because QCRILHOOK_CMD_GET on these CMD_IMS_* items
                // returns GENERIC_FAILURE on this qcrild — the items
                // appear to be write-only via that legacy reqId. The
                // setCmdValue path itself responds with rc=4 which we
                // log; trust that and move on.
                if (writeVolte) {
                    mModemNv.setVoLteEnabled(snap.volte);
                }
                if (writeVowifi) {
                    mModemNv.setVoWiFiEnabled(snap.vowifi);
                }
            }
        });
    }

    /**
     * Re-emit the legacy {@code android.intent.action.SIM_STATE_CHANGED}
     * broadcast that Ims6's {@code SIMStateAgent.SIMStateReceiverListener}
     * still expects. AOSP TelephonyRegistry on Android 15 stopped sending
     * it (replaced by ACTION_SIM_CARD_STATE_CHANGED + TelephonyCallback),
     * but Ims6 was decompiled from Android-12-era stock framework and
     * never updated. Without this, SIMStateAgent.mIccState stays
     * "NOT_READY" forever, which trips ApnIms.connect's "apn is blocked,
     * sim is not loaded" gate before it can even consider VoLTE.
     *
     * Extras format matches the pre-13 broadcast (see
     * com.android.internal.telephony.IccCardConstants):
     *   ss = "LOADED" | "READY" | "ABSENT" | "NOT_READY" | ...
     *   subId / slot_index
     */
    private void broadcastSimStateLoaded(int slot, int subId) {
        Intent i = new Intent("android.intent.action.SIM_STATE_CHANGED");
        i.putExtra("ss", "LOADED");
        i.putExtra("android.telephony.extra.SUBSCRIPTION_INDEX", subId);
        i.putExtra("android.telephony.extra.SLOT_INDEX", slot);
        i.putExtra("phoneName", "Phone");          // legacy IccCardConstants
        i.putExtra("subscription", subId);          // older alias
        i.putExtra("slot", slot);                   // older alias
        i.putExtra("phone", slot);                  // older alias
        i.setFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND
                | Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        try {
            sendBroadcast(i);
            Log.i(TAG, "[slot " + slot + "] sent SIM_STATE_CHANGED ss=LOADED sub=" + subId);
        } catch (SecurityException e) {
            Log.e(TAG, "sendBroadcast(SIM_STATE_CHANGED) failed: " + e.getMessage());
        }
    }

    /**
     * Resolve the effective VoConfig for a given SIM:
     *   1. If the user has saved an override for this ICCID, use it.
     *   2. Otherwise return defaults (everything except RCS enabled).
     */
    VoConfig resolveConfig(String iccid) {
        if (!TextUtils.isEmpty(iccid)) {
            String key = PREF_KEY_PREFIX + iccid;
            if (mPrefs.contains(key)) {
                int bits = mPrefs.getInt(key, -1);
                if (bits >= 0) return VoConfig.fromBits(bits);
            }
        }
        return VoConfig.defaults();
    }

    /** Save user override for a SIM and re-apply now. */
    synchronized void saveOverrideAndApply(String iccid, VoConfig cfg) {
        if (TextUtils.isEmpty(iccid)) return;
        mPrefs.edit().putInt(PREF_KEY_PREFIX + iccid, cfg.toBits()).apply();
        Log.i(TAG, "Override saved for " + redact(iccid) + " -> " + cfg);
        syncAllSlots();
    }

    /** Drop user override for a SIM (revert to defaults) and re-apply. */
    synchronized void clearOverrideAndApply(String iccid) {
        if (TextUtils.isEmpty(iccid)) return;
        mPrefs.edit().remove(PREF_KEY_PREFIX + iccid).apply();
        Log.i(TAG, "Override cleared for " + redact(iccid));
        syncAllSlots();
    }

    // ---------- MmTel settings + Settings.Global writes ----------

    /**
     * Apply per-subscription IMS feature toggles via the AOSP MmTel API
     * AND the legacy Settings.Global keys.
     *
     * The MmTel API (ImsMmTelManager.setVoLteSettingEnabled etc.) writes
     * to provisioning storage that the IMS service consults on every
     * registration evaluation. This is THE per-SIM gate on Android 11+.
     *
     * The Settings.Global keys (volte_vt_enabled, wfc_ims_enabled,
     * enhanced_4g_lte_mode_enabled, suffixed by subId) are the legacy
     * gate that older code paths and OEM stacks still consult. We write
     * both for maximum coverage.
     */
    private void applyMmTelSettings(int subId, VoConfig cfg) {
        // *** PROVISIONING + USER SETTINGS BOTH MATTER ***
        //
        // Two layers gate per-subscription IMS feature:
        //   1. Provisioning  (carrier-controlled)  → ProvisioningManager
        //                                            .setProvisioningStatusForCapability
        //      Visible in `*#*#4636#*#*` → RadioInfo as "VoLTE/VT/WFC Provisioned" toggles.
        //   2. User setting  (user-controlled)     → ImsMmTelManager.set*SettingEnabled
        //      Visible in Settings → Network → SIM → "Enhanced 4G LTE Mode".
        //
        // Both must be ON for the feature to be active. Both must be OFF for
        // it to be cleanly disabled (otherwise the LG stack may consult one
        // and ignore the other — depends on which check path runs first).
        //
        // We write both. Provisioning first, then user setting, so the
        // capability change cascade evaluates the final state.

        try {
            ProvisioningManager pm = ProvisioningManager.createForSubscriptionId(subId);
            if (pm != null) {
                // Capabilities: VOICE=1, VIDEO=2, UT=3, SMS=4 (MmTelFeature.MmTelCapabilities)
                // Techs: LTE=0, IWLAN=1, NR=2 (ImsRegistrationImplBase)
                try { pm.setProvisioningStatusForCapability(
                        MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE,
                        ImsRegistrationImplBase.REGISTRATION_TECH_LTE,
                        cfg.volte); } catch (Exception ignored) { }
                try { pm.setProvisioningStatusForCapability(
                        MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VIDEO,
                        ImsRegistrationImplBase.REGISTRATION_TECH_LTE,
                        cfg.vilte); } catch (Exception ignored) { }
                try { pm.setProvisioningStatusForCapability(
                        MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE,
                        ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN,
                        cfg.vowifi); } catch (Exception ignored) { }
                try { pm.setProvisioningStatusForCapability(
                        MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VIDEO,
                        ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN,
                        cfg.viwifi); } catch (Exception ignored) { }
                Log.i(TAG, "[sub=" + subId + "] Provisioning: voice/lte=" + cfg.volte
                        + " video/lte=" + cfg.vilte + " voice/iwlan=" + cfg.vowifi
                        + " video/iwlan=" + cfg.viwifi);
            }
        } catch (Exception e) {
            Log.w(TAG, "[sub=" + subId + "] Provisioning apply failed: " + e.getMessage());
        }

        // User setting layer (capability change cascade trigger).
        try {
            ImsManager im = getSystemService(ImsManager.class);
            if (im != null) {
                ImsMmTelManager mm = im.getImsMmTelManager(subId);
                if (mm != null) {
                    try { mm.setVtSettingEnabled(cfg.vilte); } catch (Exception ignored) { }
                    try { mm.setVoWiFiSettingEnabled(cfg.vowifi); } catch (Exception ignored) { }
                    try { mm.setVoWiFiRoamingSettingEnabled(cfg.vowifi); } catch (Exception ignored) { }
                    Log.i(TAG, "[sub=" + subId + "] MmTel: vilte=" + cfg.vilte
                            + " vowifi=" + cfg.vowifi);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "[sub=" + subId + "] MmTel apply failed: " + e.getMessage());
        }
    }

    private static void setProp(String key, String value) {
        try {
            String old = SystemProperties.get(key, "");
            if (!value.equals(old)) {
                SystemProperties.set(key, value);
                Log.i(TAG, "setprop " + key + "=" + value + " (was '" + old + "')");
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "setprop " + key + "=" + value + " failed: " + e.getMessage());
        }
    }

    // ---------- broadcasts ----------

    private void broadcastVolteChanged(int slot, VoConfig cfg) {
        Intent i = new Intent(ACTION_VOLTE_CHANGED_INFO);
        i.setFlags(BROADCAST_FLAGS);
        i.putExtra("smartca", 0);
        if (slot == 0) {
            i.putExtra("volte",  bs(cfg.volte));
            i.putExtra("vilte",  bs(cfg.vilte));
            i.putExtra("vowifi", bs(cfg.vowifi));
            i.putExtra("rcs",    bs(cfg.rcs));
        } else if (slot == 1) {
            i.putExtra("volte2",  bs(cfg.volte));
            i.putExtra("vilte2",  bs(cfg.vilte));
            i.putExtra("vowifi2", bs(cfg.vowifi));
            i.putExtra("rcs2",    bs(cfg.rcs));
        } else {
            return;
        }
        try {
            sendStickyBroadcast(i);
            Log.i(TAG, "[slot " + slot + "] sticky " + ACTION_VOLTE_CHANGED_INFO + " sent " + cfg);
        } catch (SecurityException e) {
            Log.e(TAG, "sendStickyBroadcast failed: " + e.getMessage());
        }
    }

    private void broadcastRcsChanged(int slot, VoConfig cfg) {
        Intent i = new Intent(ACTION_RCS_CHANGED_INFO);
        i.setFlags(BROADCAST_FLAGS);
        i.setPackage("com.lge.ims");
        if (slot == 0)      i.putExtra("rcs",  bs(cfg.rcs));
        else if (slot == 1) i.putExtra("rcs2", bs(cfg.rcs));
        else                return;
        try {
            sendStickyBroadcast(i);
        } catch (SecurityException e) {
            Log.e(TAG, "sendStickyBroadcast(rcs) failed: " + e.getMessage());
        }
    }

    private static String bs(boolean v) { return v ? "1" : "0"; }

    // ---------- vo_config.xml import (HiddenMenu compatibility) ----------
    //
    // Stock LG HiddenMenu's "Activate Vo Service" applies its choices by
    // writing /data/shared/cust/config/vo_config.xml then sending sticky
    // ACTION_VO_CONFIG_UPDATE. Stock framework ImsRadioConfigManager re-parses
    // the XML to write per-slot sysprops. LineageOS lacks IRCM, so we do it
    // ourselves here: parse the XML, match each active SIM to the best
    // <info> entry, and persist the resulting VoConfig as a per-ICCID
    // override. The next syncAllSlots() picks them up via resolveConfig().

    private static final String VO_CONFIG_PATH = "/data/shared/cust/config/vo_config.xml";

    private static final class XmlEntry {
        String mcc = "", mnc = "", gid = "", spn = "", imsi = "";
        VoConfig cfg = new VoConfig();
    }

    /**
     * Re-parse /data/shared/cust/config/vo_config.xml (stock HiddenMenu
     * format) and persist per-ICCID overrides for every active SIM that
     * matches an entry. Caller should follow up with syncAllSlots().
     *
     * Unlike stock VoConfigParser.getVoConfInfoList we do NOT enforce
     * ro.vendor.lge.build.target_operator/country match — that guard is
     * defensive on stock for ROM/XML mismatch detection, not gating logic.
     * On LineageOS we trust whatever the user writes.
     */
    private synchronized void importVoConfigXml() {
        File f = new File(VO_CONFIG_PATH);
        if (!f.exists()) {
            Log.i(TAG, "importVoConfigXml: " + VO_CONFIG_PATH + " not present, nothing to import");
            return;
        }
        List<XmlEntry> entries;
        try {
            entries = parseVoConfig(f);
        } catch (Exception e) {
            Log.e(TAG, "importVoConfigXml: parse failed: " + e.getMessage());
            return;
        }
        Log.i(TAG, "importVoConfigXml: " + entries.size() + " entries parsed");

        if (mSm == null) return;
        List<SubscriptionInfo> subs;
        try {
            subs = mSm.getActiveSubscriptionInfoList();
        } catch (SecurityException e) {
            Log.e(TAG, "importVoConfigXml: cannot read subs: " + e.getMessage());
            return;
        }
        if (subs == null) return;

        SharedPreferences.Editor edit = mPrefs.edit();
        for (SubscriptionInfo info : subs) {
            String iccid = nullToEmpty(info.getIccId());
            if (TextUtils.isEmpty(iccid)) continue;

            String mcc = nullToEmpty(info.getMccString());
            String mnc = nullToEmpty(info.getMncString());
            String spn = "", imsi = "", gid = "";
            if (mTm != null) {
                try {
                    TelephonyManager perSub =
                            mTm.createForSubscriptionId(info.getSubscriptionId());
                    if (perSub != null) {
                        spn  = nullToEmpty(perSub.getSimOperatorName());
                        imsi = nullToEmpty(perSub.getSubscriberId());
                        gid  = nullToEmpty(perSub.getGroupIdLevel1());
                    }
                } catch (SecurityException ignored) { }
            }

            XmlEntry best = findBestMatch(entries, mcc, mnc, gid, spn, imsi);
            if (best == null) {
                Log.i(TAG, "importVoConfigXml: " + redact(iccid)
                        + " (mcc=" + mcc + " mnc=" + mnc
                        + ") — no XML entry, leaving prefs untouched");
                continue;
            }
            edit.putInt(PREF_KEY_PREFIX + iccid, best.cfg.toBits());
            Log.i(TAG, "importVoConfigXml: " + redact(iccid) + " -> " + best.cfg
                    + " (matched mcc=" + best.mcc + " mnc=" + best.mnc
                    + " gid=" + best.gid + " spn=" + best.spn
                    + " imsi=" + redact(best.imsi) + ")");
        }
        edit.apply();
    }

    /**
     * Parse vo_config.xml. Tolerates the two formats we know:
     *   New: <profiles><profile op=.. country=..><info ...><prop .../></info></profile></profiles>
     *   Old: <profiles><volte><info .../></volte><vowifi>... </vowifi></profiles>
     * We only consume the <info>+<prop> pairs and don't care which parent
     * tag they live under — sufficient for HiddenMenu output.
     */
    private static List<XmlEntry> parseVoConfig(File f) throws Exception {
        List<XmlEntry> list = new ArrayList<>();
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader(f));
            XmlPullParser p = XmlPullParserFactory.newInstance().newPullParser();
            p.setInput(r);
            XmlEntry curr = null;
            int ev = p.getEventType();
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    String name = p.getName();
                    if ("info".equals(name)) {
                        curr = new XmlEntry();
                        curr.mcc  = nullToEmpty(p.getAttributeValue(null, "mcc"));
                        curr.mnc  = nullToEmpty(p.getAttributeValue(null, "mnc"));
                        curr.gid  = nullToEmpty(p.getAttributeValue(null, "gid"));
                        curr.spn  = nullToEmpty(p.getAttributeValue(null, "spn"));
                        curr.imsi = nullToEmpty(p.getAttributeValue(null, "imsi"));
                    } else if ("prop".equals(name) && curr != null) {
                        curr.cfg.volte  = "1".equals(p.getAttributeValue(null, "support_volte"));
                        curr.cfg.vilte  = "1".equals(p.getAttributeValue(null, "support_vilte"));
                        curr.cfg.vowifi = "1".equals(p.getAttributeValue(null, "support_vowifi"));
                        curr.cfg.viwifi = "1".equals(p.getAttributeValue(null, "support_viwifi"));
                        curr.cfg.rcs    = "1".equals(p.getAttributeValue(null, "support_rcs"));
                        list.add(curr);
                    }
                } else if (ev == XmlPullParser.END_TAG) {
                    if ("info".equals(p.getName())) curr = null;
                }
                ev = p.next();
            }
        } finally {
            if (r != null) try { r.close(); } catch (Exception ignored) {}
        }
        return list;
    }

    /**
     * Mirror of stock Utils.findBestMatchedSimInfo / VoConfigParser priority
     * table (8 tiers). Lower tier number = closer match. Returns the
     * entry with the lowest tier; null if no entry's mcc matches at all.
     *
     * Tier 0: mcc + mnc + gid + spn + imsi all match (exact)
     * Tier 1: mcc + mnc + gid + imsi, spn empty in entry
     * Tier 2: mcc + mnc + spn + imsi, gid empty in entry
     * Tier 3: mcc + mnc + imsi, gid + spn empty in entry
     * Tier 4: mcc + mnc + gid + spn, imsi empty in entry
     * Tier 5: mcc + mnc + gid, spn + imsi empty in entry
     * Tier 6: mcc + mnc + spn, gid + imsi empty in entry
     * Tier 7: mcc + mnc only, gid + spn + imsi all empty in entry
     * Tier 8: mcc only, all others empty in entry (catch-all per country)
     */
    private static XmlEntry findBestMatch(List<XmlEntry> entries,
            String mcc, String mnc, String gid, String spn, String imsi) {
        String gidLow = gid != null ? gid.toLowerCase(Locale.ROOT) : "";
        XmlEntry best = null;
        int bestTier = Integer.MAX_VALUE;
        for (XmlEntry e : entries) {
            if (TextUtils.isEmpty(e.mcc) || !e.mcc.equals(mcc)) continue;

            boolean mncMatch  = !e.mnc.isEmpty() && e.mnc.equals(mnc);
            boolean gidSet    = !e.gid.isEmpty();
            boolean spnSet    = !e.spn.isEmpty();
            boolean imsiSet   = !e.imsi.isEmpty();
            boolean gidMatch  = gidSet  && gidLow.startsWith(e.gid.toLowerCase(Locale.ROOT));
            boolean spnMatch  = spnSet  && spn.equals(e.spn);
            boolean imsiMatch = imsiSet && matchImsi(imsi, e.imsi);

            // Reject entries whose constraints don't match the SIM at all.
            if (gidSet  && !gidMatch)  continue;
            if (spnSet  && !spnMatch)  continue;
            if (imsiSet && !imsiMatch) continue;

            int tier;
            if (mncMatch && gidSet && spnSet && imsiSet)        tier = 0;
            else if (mncMatch && gidSet && !spnSet && imsiSet)  tier = 1;
            else if (mncMatch && !gidSet && spnSet && imsiSet)  tier = 2;
            else if (mncMatch && !gidSet && !spnSet && imsiSet) tier = 3;
            else if (mncMatch && gidSet && spnSet && !imsiSet)  tier = 4;
            else if (mncMatch && gidSet && !spnSet && !imsiSet) tier = 5;
            else if (mncMatch && !gidSet && spnSet && !imsiSet) tier = 6;
            else if (mncMatch && !gidSet && !spnSet && !imsiSet) tier = 7;
            else if (!mncMatch && e.mnc.isEmpty()
                    && !gidSet && !spnSet && !imsiSet)          tier = 8;
            else continue;

            if (tier < bestTier) {
                bestTier = tier;
                best = e;
            }
        }
        return best;
    }

    private static boolean matchImsi(String simImsi, String pattern) {
        // 'x'/'X' wildcards per stock VoConfigParser.
        if (pattern == null || simImsi == null) return false;
        if (pattern.length() > simImsi.length()) return false;
        for (int i = 0; i < pattern.length(); i++) {
            char p = pattern.charAt(i);
            if (p == 'x' || p == 'X') continue;
            if (p != simImsi.charAt(i)) return false;
        }
        return true;
    }

    // ---------- helpers ----------

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    private static String redact(String iccid) {
        if (iccid == null || iccid.length() < 8) return iccid;
        return iccid.substring(0, 4) + "…" + iccid.substring(iccid.length() - 4);
    }
}
