// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
//
// VoConfigUpdateReceiver — passive interop with stock LG's
// com.lge.action.ACTION_VO_CONFIG_UPDATE protocol.
//
// Stock LG's "Activate Vo Service" pipeline applies its choices by:
//   1. Writing /data/shared/cust/config/vo_config.xml
//   2. sendStickyBroadcast("com.lge.action.ACTION_VO_CONFIG_UPDATE",
//                          extra Sender="enabler")
//
// On stock, that broadcast is consumed by ImsRadioConfigManager in
// telephony-common.jar, which re-parses the XML and writes per-slot
// sysprops. LineageOS doesn't have ImsRadioConfigManager, so the
// broadcast is normally lost. This receiver picks it up and forwards
// to LgeImsConfigBridgeService, which imports the XML, persists each
// SIM's choice as a per-ICCID override in SharedPreferences, and
// re-applies via the normal sync path.
//
// This is purely passive: we accept any caller that follows the
// protocol. We do not depend on or trigger any specific producer.

package com.lineageos.lgeimsconfigbridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class VoConfigUpdateReceiver extends BroadcastReceiver {
    private static final String TAG = "LgeImsCfg";

    static final String ACTION_VO_CONFIG_UPDATE = "com.lge.action.ACTION_VO_CONFIG_UPDATE";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        String sender = intent.getStringExtra("Sender");
        Log.i(TAG, "VoConfigUpdateReceiver: action=" + action + " sender=" + sender);
        // Forward to the service. Action is preserved so the service knows
        // to re-import the XML rather than just re-emit current state.
        Intent svc = new Intent(context, LgeImsConfigBridgeService.class);
        svc.setAction(action);
        try {
            context.startService(svc);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start service: " + e.getMessage());
        }
    }
}
