// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
//
// Service that publishes the binder "com.lge.ims.phone" expected by Ims6's
// ImsPhoneProxyManager. On stock LG firmware that binder is registered
// from inside the Phone process by LGImsPhoneService.create() — that init
// point doesn't exist in LineageOS, so without this service Ims6 sees
// mService=null in ImsPhoneProxyManager.bindService() and every per-slot
// ImsPhoneProxy ends up wrapping a null binder. Result: getPcscfAddress()
// always returns null, AoSPCSCF::IsConfigured stays false, AoSRegistration
// never fires SendREGISTER.
//
// Lifetime:
//   - BootReceiver fires us on LOCKED_BOOT_COMPLETED + BOOT_COMPLETED.
//   - We publish the binder via ServiceManager.addService once, then send
//     the broadcast com.lge.ims.action.IMS_PHONE_STARTED so any
//     already-running ImsPhoneProxyManager re-binds without waiting for
//     its 1s retry timer.
//   - START_STICKY keeps us alive; if we're killed init brings us back
//     and re-publishes the binder.

package com.lineageos.lgimsphone;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.ILGImsIsim;
import com.android.internal.telephony.ILGImsPhoneProxy;
import com.android.internal.telephony.ILGImsPhoneService;
import com.android.internal.telephony.ILGImsVoNR;

public class LGImsPhoneServiceImpl extends Service {
    private static final String TAG = "LGImsPhoneSvc";
    private static final String SERVICE_NAME = "com.lge.ims.phone";
    private static final String ACTION_IMS_PHONE_STARTED =
            "com.lge.ims.action.IMS_PHONE_STARTED";

    private boolean mPublished = false;
    private LGImsPhoneProxyImpl[] mProxies;

    private final ILGImsPhoneService.Stub mBinder = new ILGImsPhoneService.Stub() {
        @Override
        public ILGImsPhoneProxy getPhoneProxy(int slot) {
            LGImsPhoneProxyImpl[] proxies = mProxies;
            if (proxies == null || slot < 0 || slot >= proxies.length) return null;
            return proxies[slot];
        }

        @Override
        public ILGImsIsim getIsimInterface(int slot) {
            // ImsPhoneProxyManager.ProxyComponent.setBinder() handles a
            // null binder cleanly — see ImsIsim.setBinder(null) being a
            // no-op. We don't implement ISIM provisioning here.
            return null;
        }

        @Override
        public ILGImsVoNR getVoNRInterface(int slot) {
            // Same null-tolerance pattern as ISIM. VoNR is unrelated to
            // VoLTE bring-up on Beeline RU LTE.
            return null;
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Service onCreate");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Service onStartCommand");
        if (!mPublished) publishBinder();
        return START_STICKY;
    }

    private void publishBinder() {
        int slotCount = activeModemCount();
        mProxies = new LGImsPhoneProxyImpl[slotCount];
        for (int i = 0; i < slotCount; i++) {
            mProxies[i] = new LGImsPhoneProxyImpl(getApplicationContext(), i);
        }

        try {
            ServiceManager.addService(SERVICE_NAME, mBinder);
            mPublished = true;
            Log.i(TAG, "addService(\"" + SERVICE_NAME + "\") OK; slots=" + slotCount);
        } catch (Throwable t) {
            // ServiceManager.addService throws SecurityException for non-system
            // uids; we should be uid=system thanks to sharedUserId, but log it
            // anyway so failures show up in dmesg-adjacent logs.
            Log.e(TAG, "addService failed: " + t, t);
            return;
        }

        // ImsPhoneProxyManager listens for this broadcast as the cue to
        // re-attempt bindService(). Without it, if Ims6 booted before us,
        // it's stuck in the post-3-retries idle state until process restart.
        Intent started = new Intent(ACTION_IMS_PHONE_STARTED);
        started.setFlags(Intent.FLAG_RECEIVER_FOREGROUND
                | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
        sendBroadcast(started);
        Log.i(TAG, "sendBroadcast(" + ACTION_IMS_PHONE_STARTED + ")");
    }

    private int activeModemCount() {
        TelephonyManager tm = getSystemService(TelephonyManager.class);
        if (tm == null) return 1;
        try {
            int n = tm.getActiveModemCount();
            return n > 0 ? n : 1;
        } catch (Throwable t) {
            return 1;
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
