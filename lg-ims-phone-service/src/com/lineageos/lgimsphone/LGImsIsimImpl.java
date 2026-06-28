// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
//
// LGImsIsimImpl — backs ILGImsIsim binder that Ims6's ImsPhoneProxyManager
// fetches per slot via getIsimInterface(slot). On stock LG firmware that
// binder is served by the Phone process and reads ISIM EFs through LG's
// extended UICC API. On LineageOS we route through AOSP TelephonyManager;
// the values it returns are functionally identical because they ultimately
// come from the same 3GPP-formatted ISIM applet on the SIM card.
//
// Why this matters: native libims.lge.so AoSSubscriber requires non-null
// IMPI/IMPU/Domain to clear the SUBSCRIBERINCOMPLETED flag. Without that
// flag clearing, AoSServiceAvailable refuses to mark IMS available even
// when VoPS=on, RAT=available, SIM=loaded. The ILGImsIsim binder is the
// path Ims6 already wires up — see ImsIsim.java and ISIMAgent.java in the
// decompiled Ims6 — so all we need to do is publish a working stub.
//
// AOSP API surface used:
//   TelephonyManager.createForSubscriptionId(subId).getIsimImpi()       (@hide)
//   TelephonyManager.createForSubscriptionId(subId).getIsimImpu()       (@hide)
//   TelephonyManager.createForSubscriptionId(subId).getIsimDomain()     (@SystemApi)
//   TelephonyManager.createForSubscriptionId(subId).getIsimPcscf()      (@hide)
//   TelephonyManager.createForSubscriptionId(subId).getIsimIst()        (@SystemApi)
//   TelephonyManager.createForSubscriptionId(subId).getIccAuthentication(
//       APPTYPE_ISIM, AUTHTYPE_EAP_AKA, base64(rand|autn))               (@SystemApi)
//
// All five getters require READ_PRIVILEGED_PHONE_STATE per
// PhoneSubInfoController. We have it via privapp allowlist.
//
// GBA bootstrapping (ILGImsIsim.setGbaBootstrappingParameters /
// getGbaBootstrappingResponse / getNafExternalKey / getRand / getBTid /
// getKeyLifetime / isGbaSupported) is intentionally a no-op. GBA is
// orthogonal to the SUBSCRIBERINCOMPLETED gate and Beeline/T2 IMS doesn't
// require it for VoLTE; if a future operator does we'll wire it up then.

package com.lineageos.lgimsphone;

import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.ILGImsIsim;
import com.android.internal.telephony.ILGImsIsimCallback;

public class LGImsIsimImpl extends ILGImsIsim.Stub {
    private static final String TAG = "LGImsIsim";

    private final Context mContext;
    private final int mSlotId;
    private volatile ILGImsIsimCallback mCallback;

    public LGImsIsimImpl(Context context, int slotId) {
        mContext = context;
        mSlotId = slotId;
    }

    /** Resolve current subscription ID for our slot. -1 if no SIM. */
    private int resolveSubId() {
        SubscriptionManager sm = mContext.getSystemService(SubscriptionManager.class);
        if (sm == null) return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        int[] subs = sm.getSubscriptionIds(mSlotId);
        if (subs == null || subs.length == 0) return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        return subs[0];
    }

    private TelephonyManager telephonyForSub() {
        int subId = resolveSubId();
        if (!SubscriptionManager.isValidSubscriptionId(subId)) return null;
        TelephonyManager tm = mContext.getSystemService(TelephonyManager.class);
        if (tm == null) return null;
        return tm.createForSubscriptionId(subId);
    }

    @Override
    public String getImpi() {
        TelephonyManager tm = telephonyForSub();
        if (tm == null) return null;
        try {
            String impi = tm.getIsimImpi();
            Log.i(TAG, "getImpi[" + mSlotId + "] -> " + (impi == null ? "null" : "<set>"));
            return impi;
        } catch (Throwable t) {
            Log.w(TAG, "getImpi[" + mSlotId + "] threw: " + t);
            return null;
        }
    }

    @Override
    public String[] getImpu() {
        TelephonyManager tm = telephonyForSub();
        if (tm == null) return null;
        try {
            String[] impu = tm.getIsimImpu();
            Log.i(TAG, "getImpu[" + mSlotId + "] -> "
                    + (impu == null ? "null" : ("len=" + impu.length)));
            return impu;
        } catch (Throwable t) {
            Log.w(TAG, "getImpu[" + mSlotId + "] threw: " + t);
            return null;
        }
    }

    @Override
    public String getDomain() {
        TelephonyManager tm = telephonyForSub();
        if (tm == null) return null;
        try {
            String dom = tm.getIsimDomain();
            Log.i(TAG, "getDomain[" + mSlotId + "] -> " + (dom == null ? "null" : dom));
            return dom;
        } catch (Throwable t) {
            Log.w(TAG, "getDomain[" + mSlotId + "] threw: " + t);
            return null;
        }
    }

    @Override
    public String[] getPcscfAddress() {
        TelephonyManager tm = telephonyForSub();
        if (tm == null) return null;
        try {
            String[] p = tm.getIsimPcscf();
            Log.i(TAG, "getPcscfAddress[" + mSlotId + "] -> "
                    + (p == null ? "null" : ("len=" + p.length)));
            return p;
        } catch (Throwable t) {
            Log.w(TAG, "getPcscfAddress[" + mSlotId + "] threw: " + t);
            return null;
        }
    }

    @Override
    public String getIst() {
        TelephonyManager tm = telephonyForSub();
        if (tm == null) return null;
        try {
            return tm.getIsimIst();
        } catch (Throwable t) {
            Log.w(TAG, "getIst[" + mSlotId + "] threw: " + t);
            return null;
        }
    }

    @Override
    public String getState() {
        // Ims6's ImsIsim.getState() defaults to "NOT_READY" on RemoteException.
        // We report "LOADED" once IMPI is non-null — that's what Ims6 cares
        // about (downstream SIMStateAgent.IsimState).
        return getImpi() != null ? "LOADED" : "NOT_READY";
    }

    @Override
    public String getChallengeResponse(String challenge) {
        TelephonyManager tm = telephonyForSub();
        if (tm == null) return null;
        try {
            // APPTYPE_ISIM = 5, AUTHTYPE_EAP_AKA = 129. The challenge string
            // from caller is base64(RAND||AUTN); pass through verbatim.
            return tm.getIccAuthentication(
                    TelephonyManager.APPTYPE_ISIM,
                    TelephonyManager.AUTHTYPE_EAP_AKA,
                    challenge);
        } catch (Throwable t) {
            Log.w(TAG, "getChallengeResponse[" + mSlotId + "] threw: " + t);
            return null;
        }
    }

    @Override
    public void setCallback(ILGImsIsimCallback callback) {
        mCallback = callback;
        // Best-effort initial replay so Ims6 doesn't sit idle waiting for a
        // change event that already happened. Ims6's IsimCallback.onIsimStateChanged
        // doesn't carry slot info — single-line state notify.
        if (callback != null) {
            try {
                callback.onIsimStateChanged(getState());
            } catch (RemoteException e) {
                Log.w(TAG, "setCallback[" + mSlotId + "] initial replay failed: " + e);
            }
        }
    }

    /**
     * Called externally (e.g., from a SubscriptionsChangedListener in the
     * service) to push state to Ims6 when SIM/ISIM transitions.
     */
    void notifyStateChanged(String state) {
        ILGImsIsimCallback cb = mCallback;
        if (cb == null) return;
        try {
            cb.onIsimStateChanged(state);
        } catch (RemoteException e) {
            Log.w(TAG, "notifyStateChanged[" + mSlotId + "] failed: " + e);
        }
    }

    // ---------- GBA: not implemented (no-op stubs) ----------
    // Ims6 falls back gracefully when these return null/false; GBA is
    // unrelated to the VoLTE registration gate.

    @Override public byte[] getRand() { return null; }
    @Override public String getBTid() { return null; }
    @Override public String getKeyLifetime() { return null; }
    @Override public boolean isGbaSupported() { return false; }
    @Override public void setGbaBootstrappingParameters(byte[] rand, String autn, String naf) { }
    @Override public Bundle getGbaBootstrappingResponse(byte[] rand, byte[] autn) { return null; }
    @Override public byte[] getNafExternalKey(byte[] naf) { return null; }
}
