// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
//
// ImsNetworkRequesterService — issues ConnectivityManager.requestNetwork()
// for NET_CAPABILITY_IMS on every active subscription, then keeps the
// callbacks alive forever so the request stays active across the lifetime
// of the device.
//
// On stock LG firmware, com.lge.lgdataphone.dataconnection.ApnManager fires
// the same request as part of its boot init. LineageOS doesn't import that
// LG framework class; we replicate the one effective call here.
//
// The request is scoped per-subscription via TelephonyNetworkSpecifier.
// Without that scope, AOSP DataNetworkController routes the request to a
// default subId and may not match the per-slot Beeline ims APN. With it,
// each slot gets its own SETUP_DATA_CALL with apn=ims that carries P-CSCF
// back to Ims6 in PCO.

package com.lineageos.imsnetworkrequester;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.TelephonyNetworkSpecifier;
import android.os.IBinder;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class ImsNetworkRequesterService extends Service {
    private static final String TAG = "ImsNetReq";

    private ConnectivityManager mCm;
    private final List<ConnectivityManager.NetworkCallback> mActiveCallbacks =
            new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Service onCreate");
        mCm = getSystemService(ConnectivityManager.class);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Service onStartCommand");
        requestImsForAllActiveSubs();
        // STICKY: if killed, init reruns onStartCommand without intent.
        return START_STICKY;
    }

    private void requestImsForAllActiveSubs() {
        SubscriptionManager sm = getSystemService(SubscriptionManager.class);
        if (sm == null) {
            Log.e(TAG, "SubscriptionManager unavailable");
            return;
        }

        List<SubscriptionInfo> subs;
        try {
            subs = sm.getActiveSubscriptionInfoList();
        } catch (SecurityException e) {
            Log.e(TAG, "Cannot read active subs: " + e.getMessage());
            return;
        }
        if (subs == null || subs.isEmpty()) {
            // No SIM yet — DataNetworkController has nothing to satisfy the
            // request anyway. We rely on BootReceiver to fire us once. If
            // SIM hot-swap happens we currently miss it — TODO: register a
            // SubscriptionManager.OnSubscriptionsChangedListener.
            Log.w(TAG, "No active subscriptions; nothing to request");
            return;
        }

        for (SubscriptionInfo info : subs) {
            int subId = info.getSubscriptionId();
            requestImsForSub(subId);
        }
    }

    private void requestImsForSub(int subId) {
        NetworkRequest.Builder b = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_IMS)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .setNetworkSpecifier(new TelephonyNetworkSpecifier.Builder()
                        .setSubscriptionId(subId)
                        .build());
        NetworkRequest req = b.build();

        ConnectivityManager.NetworkCallback cb =
                new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                Log.i(TAG, "[sub=" + subId + "] IMS network onAvailable: " + network);
            }

            @Override
            public void onCapabilitiesChanged(Network network,
                                              NetworkCapabilities caps) {
                Log.i(TAG, "[sub=" + subId + "] capabilities=" + caps);
            }

            @Override
            public void onLinkPropertiesChanged(Network network,
                                                LinkProperties lp) {
                Log.i(TAG, "[sub=" + subId + "] linkProperties: " + lp);
            }

            @Override
            public void onLost(Network network) {
                Log.i(TAG, "[sub=" + subId + "] IMS network lost");
            }

            @Override
            public void onUnavailable() {
                Log.i(TAG, "[sub=" + subId + "] IMS network onUnavailable");
            }
        };

        try {
            mCm.requestNetwork(req, cb);
            mActiveCallbacks.add(cb);
            Log.i(TAG, "[sub=" + subId + "] requestNetwork(IMS) issued");
        } catch (SecurityException e) {
            Log.e(TAG, "[sub=" + subId + "] requestNetwork failed: "
                    + e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Service onDestroy");
        for (ConnectivityManager.NetworkCallback cb : mActiveCallbacks) {
            try {
                mCm.unregisterNetworkCallback(cb);
            } catch (IllegalArgumentException ignored) { }
        }
        mActiveCallbacks.clear();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
