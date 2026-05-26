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
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.TelephonyNetworkSpecifier;
import android.net.Uri;
import android.os.IBinder;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import android.util.Log;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ImsNetworkRequesterService extends Service {
    private static final String TAG = "ImsNetReq";

    private ConnectivityManager mCm;
    private SubscriptionManager mSm;
    private final Map<Integer, ConnectivityManager.NetworkCallback> mPerSubCallbacks =
            new HashMap<>();
    private SubscriptionManager.OnSubscriptionsChangedListener mSubsListener;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Service onCreate");
        mCm = getSystemService(ConnectivityManager.class);
        mSm = getSystemService(SubscriptionManager.class);

        // Watch for SIM hot-swaps. Stock LG fires the IMS requestNetwork
        // from com.lge.lgdataphone.dataconnection.ApnManager which also
        // re-fires it when the active subscription changes. Without this
        // listener Ims6 stays bound to the old subId and never re-registers
        // until reboot.
        if (mSm != null) {
            mSubsListener = new SubscriptionManager.OnSubscriptionsChangedListener() {
                @Override
                public void onSubscriptionsChanged() {
                    syncRequestsToActiveSubs();
                }
            };
            mSm.addOnSubscriptionsChangedListener(getMainExecutor(), mSubsListener);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Service onStartCommand");
        syncRequestsToActiveSubs();
        // STICKY: if killed, init reruns onStartCommand without intent.
        return START_STICKY;
    }

    private synchronized void syncRequestsToActiveSubs() {
        if (mSm == null) {
            Log.e(TAG, "SubscriptionManager unavailable");
            return;
        }

        List<SubscriptionInfo> subs;
        try {
            subs = mSm.getActiveSubscriptionInfoList();
        } catch (SecurityException e) {
            Log.e(TAG, "Cannot read active subs: " + e.getMessage());
            return;
        }

        Set<Integer> activeIds = new HashSet<>();
        if (subs != null) {
            for (SubscriptionInfo info : subs) {
                activeIds.add(info.getSubscriptionId());
            }
        }

        // Drop callbacks for subs that are no longer active (SIM removed
        // or replaced — old subId now stale).
        Set<Integer> stale = new HashSet<>(mPerSubCallbacks.keySet());
        stale.removeAll(activeIds);
        for (Integer subId : stale) {
            ConnectivityManager.NetworkCallback cb = mPerSubCallbacks.remove(subId);
            if (cb != null) {
                try {
                    mCm.unregisterNetworkCallback(cb);
                    Log.i(TAG, "[sub=" + subId + "] removed (no longer active)");
                } catch (IllegalArgumentException ignored) { }
            }
        }

        // Add callbacks for new subs (boot, SIM inserted, hot-swap to new
        // ICCID with a fresh subId).
        for (Integer subId : activeIds) {
            if (!mPerSubCallbacks.containsKey(subId)) {
                SubscriptionInfo info = findSubInfo(subs, subId);
                ensureImsApnForSub(info);
                requestImsForSub(subId);
            }
        }

        if (activeIds.isEmpty()) {
            Log.w(TAG, "No active subscriptions; nothing to request");
        }
    }

    private static SubscriptionInfo findSubInfo(List<SubscriptionInfo> subs, int subId) {
        if (subs == null) return null;
        for (SubscriptionInfo info : subs) {
            if (info.getSubscriptionId() == subId) return info;
        }
        return null;
    }

    // Seed an apn=ims, type=ims row in telephony.db for this carrier if none
    // exists. Works around an AOSP DataProfileManager race in DSDS: the
    // synthetic "DEFAULT IMS" profile is only injected when mSimState ==
    // SIM_STATE_LOADED, but mSimState is updated via this::post and loses
    // the race against the synchronous onCarrierConfigUpdated → updateDataProfiles
    // path on the second slot. Hidden on slot 0 because the carrier usually
    // already has a CARRIER_EDITED ims row from a prior single-SIM boot.
    //
    // Inserting the row via ContentResolver fires APN_DATABASE_CHANGED, which
    // DataProfileManager observes and re-runs updateDataProfiles() — this time
    // the real ims-typed row is present so NO_SUITABLE_DATA_PROFILE is gone.
    // Inserted rows get edited=CARRIER_EDITED and survive reboots and
    // apns-conf.xml reloads.
    private void ensureImsApnForSub(SubscriptionInfo info) {
        if (info == null) return;
        String mcc = info.getMccString();
        String mnc = info.getMncString();
        if (TextUtils.isEmpty(mcc) || TextUtils.isEmpty(mnc)) {
            Log.w(TAG, "[sub=" + info.getSubscriptionId() + "] no MCC/MNC, skipping IMS APN seed");
            return;
        }
        String numeric = mcc + mnc;

        Uri carriers = Uri.parse("content://telephony/carriers");
        ContentResolver cr = getContentResolver();

        // Look for any existing row that satisfies an IMS data profile for
        // this PLMN. canSatisfy(NET_CAPABILITY_IMS) requires the type column
        // to contain "ims" (comma-separated list).
        //
        // We also need IPV4V6 protocol — observed (2026-05-27) that an old
        // apns-conf.xml row "Beeline ims" with protocol=IP (IPv4-only) caused
        // DPM to mark the IMS profile permanently failed because the modem
        // cannot bring up an IPv4-only PDN on Beeline RU's IMS APN (it serves
        // IPv6). DPM logs `The suitable data profiles are all in permanent
        // failed state.` and IMS never registers. Force-upgrade such rows.
        try (Cursor c = cr.query(carriers,
                new String[]{"_id", "type", "protocol", "roaming_protocol"},
                "numeric=? AND type LIKE ?",
                new String[]{numeric, "%ims%"},
                null)) {
            if (c != null && c.moveToFirst()) {
                long id = c.getLong(0);
                String proto = c.getString(2);
                String rproto = c.getString(3);
                boolean needsUpgrade =
                        !"IPV4V6".equalsIgnoreCase(proto) || !"IPV4V6".equalsIgnoreCase(rproto);
                if (!needsUpgrade) {
                    Log.i(TAG, "[sub=" + info.getSubscriptionId()
                            + "] IMS APN already present and IPV4V6 for " + numeric);
                    return;
                }
                ContentValues u = new ContentValues();
                u.put("protocol", "IPV4V6");
                u.put("roaming_protocol", "IPV4V6");
                int rows = cr.update(carriers, u, "_id=?",
                        new String[]{Long.toString(id)});
                Log.i(TAG, "[sub=" + info.getSubscriptionId()
                        + "] upgraded IMS APN protocol IP/" + proto + "->IPV4V6 for "
                        + numeric + " (id=" + id + ", updated=" + rows + ")");
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "Query/update telephony/carriers failed: " + e.getMessage());
            return;
        }

        ContentValues v = new ContentValues();
        v.put("name", "IMS (auto)");
        v.put("numeric", numeric);
        v.put("mcc", mcc);
        v.put("mnc", mnc);
        v.put("apn", "ims");
        v.put("type", "ims");
        v.put("protocol", "IPV4V6");
        v.put("roaming_protocol", "IPV4V6");
        v.put("carrier_enabled", 1);
        v.put("authtype", -1);
        v.put("current", 1);

        try {
            Uri inserted = cr.insert(carriers, v);
            Log.i(TAG, "[sub=" + info.getSubscriptionId() + "] inserted IMS APN for "
                    + numeric + " -> " + inserted);
        } catch (Exception e) {
            Log.e(TAG, "[sub=" + info.getSubscriptionId() + "] IMS APN insert failed: "
                    + e.getMessage());
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
            mPerSubCallbacks.put(subId, cb);
            Log.i(TAG, "[sub=" + subId + "] requestNetwork(IMS) issued");
        } catch (SecurityException e) {
            Log.e(TAG, "[sub=" + subId + "] requestNetwork failed: "
                    + e.getMessage());
        }
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
        for (ConnectivityManager.NetworkCallback cb : mPerSubCallbacks.values()) {
            try {
                mCm.unregisterNetworkCallback(cb);
            } catch (IllegalArgumentException ignored) { }
        }
        mPerSubCallbacks.clear();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
