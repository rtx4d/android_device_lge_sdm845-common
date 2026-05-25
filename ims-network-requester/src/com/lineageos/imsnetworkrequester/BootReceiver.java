// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0

package com.lineageos.imsnetworkrequester;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "ImsNetReq";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "BootReceiver received: " + intent.getAction());
        Intent svc = new Intent(context, ImsNetworkRequesterService.class);
        context.startService(svc);
    }
}
