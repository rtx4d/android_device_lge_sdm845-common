// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0

package com.lineageos.lgeimsconfigbridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "LgeImsCfg";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.i(TAG, "BootReceiver received: " + action);
        Intent svc = new Intent(context, LgeImsConfigBridgeService.class);
        // Forward the boot phase so the service knows whether the
        // CE-encrypted side of the system is up. QcrilMsgTunnelService
        // is NOT directBootAware, so it cannot serve binds until after
        // BOOT_COMPLETED (post-user-unlock). We still want our service
        // to do its synchronous work (sysprops, sticky broadcasts,
        // SIM_STATE replay, MmTel toggles) at LOCKED_BOOT_COMPLETED;
        // modem-NV writes are deferred until the BOOT_COMPLETED kick.
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            svc.putExtra("boot_completed", true);
        }
        context.startService(svc);
    }
}
