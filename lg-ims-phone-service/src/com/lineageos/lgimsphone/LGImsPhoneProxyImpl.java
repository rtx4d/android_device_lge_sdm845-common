// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
//
// Implementation of ILGImsPhoneProxy that resolves only the methods Ims6
// actually relies on for VoLTE bring-up. Everything else is a no-op
// returning a benign default. The decisive method is getPcscfAddress(),
// which the LG IMS native stack (AoSPCSCF::IsConfigured) calls through
// LGExtApi.Data.getPcscfAddress -> ImsPhoneProxyManager.getPhoneProxy ->
// ILGImsPhoneProxy.getPcscfAddress on multi-sim builds.
//
// The resolution strategy:
//   1. Look up subId for the slot via SubscriptionManager.
//   2. Walk ConnectivityManager.getAllNetworks(); pick the network whose
//      NetworkCapabilities advertise TRANSPORT_CELLULAR + the requested
//      apn capability AND whose TelephonyNetworkSpecifier matches subId.
//   3. Return LinkProperties.getPcscfServers() formatted as String[].
//
// We can't rely on the request fired by Ims6 itself because that callback
// path returns LinkProperties only to Java; the native PCO read happens on
// a different thread that polls this binder once after Connection_Connected.

package com.lineageos.lgimsphone;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkSpecifier;
import android.net.TelephonyNetworkSpecifier;
import android.os.Bundle;
import android.os.RemoteException;
import android.telephony.CellInfo;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.telephony.DataSpecificRegistrationInfo;
import android.telephony.LteVopsSupportInfo;
import android.util.Log;

import com.android.internal.telephony.ILGImsInfoCallback;
import com.android.internal.telephony.ILGImsNetworkInfoCallback;
import com.android.internal.telephony.ILGImsPhoneProxy;
import com.android.internal.telephony.ILGImsPhoneProxyCallback;
import com.android.internal.telephony.LGImsCellInfo;
import com.android.internal.telephony.LGImsEnvelope;
import com.android.internal.telephony.LGImsHVoLtePreference;
import com.android.internal.telephony.LGImsNetworkFeature;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class LGImsPhoneProxyImpl extends ILGImsPhoneProxy.Stub {
    private static final String TAG = "LGImsPhoneSvc";

    // Mirrors EApnType in com.lge.ims.common.agents.dcmif.
    // Strings are what DCApn passes after EApnType.getPhoneApnTypeFromType().
    private static final int CAP_INTERNET = NetworkCapabilities.NET_CAPABILITY_INTERNET;
    private static final int CAP_IMS = NetworkCapabilities.NET_CAPABILITY_IMS;
    private static final int CAP_EIMS = NetworkCapabilities.NET_CAPABILITY_EIMS;
    private static final int CAP_XCAP = NetworkCapabilities.NET_CAPABILITY_XCAP;

    private final Context mContext;
    private final int mSlotId;

    // Cached LGImsNetworkFeature snapshot. getNetworkFeature() returns this;
    // ServiceStateListener updates it from AOSP DataSpecificRegistrationInfo
    // and pushes onNetworkFeatureChanged with the changed-bit mask.
    private final LGImsNetworkFeature mFeature = new LGImsNetworkFeature();
    private final Object mFeatureLock = new Object();

    // Single Ims6-side callback. setCallback overrides any previous one.
    private volatile ILGImsPhoneProxyCallback mCallback;

    // TelephonyCallback registered once (when mCallback first arrives) on the
    // per-sub TelephonyManager for this slot. Held for unregister.
    private volatile VopsListener mVopsListener;
    private volatile int mRegisteredSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private final Executor mCallbackExecutor = Executors.newSingleThreadExecutor();

    LGImsPhoneProxyImpl(Context context, int slotId) {
        mContext = context;
        mSlotId = slotId;
    }

    @Override
    public String[] getPcscfAddress(String apnType) {
        int cap = mapApnTypeToCapability(apnType);
        if (cap < 0) {
            Log.w(TAG, "getPcscfAddress[" + mSlotId + "] unknown apnType=" + apnType);
            return null;
        }
        int subId = subIdForSlot();
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            Log.w(TAG, "getPcscfAddress[" + mSlotId + "] no subId for slot");
            return null;
        }

        ConnectivityManager cm = mContext.getSystemService(ConnectivityManager.class);
        if (cm == null) return null;

        Network[] networks = cm.getAllNetworks();
        if (networks == null) return null;

        for (Network n : networks) {
            NetworkCapabilities nc = cm.getNetworkCapabilities(n);
            if (nc == null) continue;
            if (!nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) continue;
            if (!nc.hasCapability(cap)) continue;
            if (!matchesSubId(nc, subId)) continue;

            LinkProperties lp = cm.getLinkProperties(n);
            if (lp == null) continue;

            List<InetAddress> pcscf = lp.getPcscfServers();
            if (pcscf == null || pcscf.isEmpty()) continue;

            String[] out = new String[pcscf.size()];
            for (int i = 0; i < pcscf.size(); i++) {
                // Ims6 expects bare numeric form: "10.32.6.190"
                out[i] = pcscf.get(i).getHostAddress();
            }
            Log.i(TAG, "getPcscfAddress[" + mSlotId + "] apnType=" + apnType
                    + " -> " + java.util.Arrays.toString(out));
            return out;
        }

        Log.w(TAG, "getPcscfAddress[" + mSlotId + "] no IMS network found yet");
        return null;
    }

    private int subIdForSlot() {
        SubscriptionManager sm = mContext.getSystemService(SubscriptionManager.class);
        if (sm == null) return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        int[] subs = sm.getSubscriptionIds(mSlotId);
        if (subs == null || subs.length == 0) {
            return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }
        return subs[0];
    }

    private static boolean matchesSubId(NetworkCapabilities nc, int subId) {
        NetworkSpecifier spec = nc.getNetworkSpecifier();
        if (spec instanceof TelephonyNetworkSpecifier) {
            return ((TelephonyNetworkSpecifier) spec).getSubscriptionId() == subId;
        }
        // Some Android 15 builds drop the specifier on satisfied networks.
        // Fall back to subId list inside NetworkCapabilities itself.
        try {
            int[] subIds = nc.getSubscriptionIds().stream().mapToInt(Integer::intValue).toArray();
            for (int s : subIds) if (s == subId) return true;
        } catch (Throwable ignored) { }
        return false;
    }

    private static int mapApnTypeToCapability(String apnType) {
        if (apnType == null) return -1;
        switch (apnType) {
            case "ims": return CAP_IMS;
            case "emergency": return CAP_EIMS;
            case "xcap": return CAP_XCAP;
            case "default": return CAP_INTERNET;
            default: return -1;
        }
    }

    // -------------------------------------------------------------------
    // Below: no-op stubs for the remaining ILGImsPhoneProxy AIDL surface.
    // These are called by Ims6 during steady-state operation (cell-info
    // refresh, modem-info caches, FDN list, network feature broadcasts,
    // emergency-call state, etc). The VoLTE registration path does not
    // depend on any of them; returning sensible defaults keeps Ims6 from
    // crashing when it tries.
    // -------------------------------------------------------------------

    // ---- network-feature callback wiring (VoPS delivery to Ims6) ----
    //
    // Stock LG framework surfaces VoPS via a private RIL_UNSOL_VOPS_INFO event
    // through ImsPhoneProxy, which fires onNetworkFeatureChanged on the
    // Ims6-side callback. Ims6's IIMSPhoneGov.IImsPhone listens for this and
    // notifies its mLteVoPSChangedRegistrants — DCNetWatcher then forwards it
    // to ApnImsGlobal as EVENT_VOPS_CHANGED, which is the gate IMS uses to
    // start registration.
    //
    // We don't have the LG RIL event channel on LineageOS, but AOSP RILJ
    // already surfaces the same VoPS bit in DataSpecificRegistrationInfo via
    // TelephonyCallback.ServiceStateListener (verified live: t2 mobile in
    // slot 0 reports mVopsSupport=2 in service state, but ApnImsGlobal[0]
    // shows vops=off because nothing translates AOSP -> LG callback).
    //
    // This implementation listens on the AOSP service-state stream for our
    // slot's sub, extracts LteVopsSupportInfo, and pushes the VoPS bit into
    // mFeature + onNetworkFeatureChanged when it changes.

    @Override
    public void setCallback(ILGImsPhoneProxyCallback cb) {
        Log.i(TAG, "setCallback[" + mSlotId + "] cb=" + (cb != null));
        mCallback = cb;
        if (cb != null) {
            ensureVopsListenerRegistered();
            // If we already have a cached feature, fire one synthetic event so
            // the freshly-bound callback gets the current state immediately.
            LGImsNetworkFeature snapshot;
            synchronized (mFeatureLock) { snapshot = copyFeature(mFeature); }
            try { cb.onNetworkFeatureChanged(snapshot, LGImsNetworkFeature.FEATURE_VOPS); }
            catch (RemoteException ignored) { }
        } else {
            unregisterVopsListener();
        }
    }

    @Override
    public LGImsNetworkFeature getNetworkFeature() {
        synchronized (mFeatureLock) { return copyFeature(mFeature); }
    }

    private LGImsNetworkFeature copyFeature(LGImsNetworkFeature src) {
        LGImsNetworkFeature dst = new LGImsNetworkFeature();
        dst.copyFrom(src);
        return dst;
    }

    private synchronized void ensureVopsListenerRegistered() {
        int subId = subIdForSlot();
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            Log.w(TAG, "ensureVopsListener[" + mSlotId + "] no subId yet");
            return;
        }
        if (mVopsListener != null && mRegisteredSubId == subId) return;
        unregisterVopsListener();

        TelephonyManager tm = mContext.getSystemService(TelephonyManager.class);
        if (tm == null) return;
        final TelephonyManager perSub = tm.createForSubscriptionId(subId);
        if (perSub == null) return;

        final VopsListener listener = new VopsListener();
        try {
            perSub.registerTelephonyCallback(mCallbackExecutor, listener);
            mVopsListener = listener;
            mRegisteredSubId = subId;
            Log.i(TAG, "VopsListener registered[" + mSlotId + "] sub=" + subId);
        } catch (Exception e) {
            Log.e(TAG, "registerTelephonyCallback failed: " + e.getMessage());
            mVopsListener = null;
            return;
        }

        // registerTelephonyCallback in Android 15 only fires on subsequent
        // changes — it does NOT replay current state. If service state was
        // already stable before we registered (we register late, after
        // setCallback comes from Ims6 which itself binds slowly), we need
        // to feed the current ServiceState explicitly. Otherwise we sit
        // forever waiting for a change that won't come until handover.
        mCallbackExecutor.execute(() -> {
            try {
                ServiceState ss = perSub.getServiceState();
                if (ss != null) listener.onServiceStateChanged(ss);
            } catch (Exception e) {
                Log.w(TAG, "initial getServiceState failed: " + e.getMessage());
            }
        });
    }

    private synchronized void unregisterVopsListener() {
        if (mVopsListener == null) return;
        TelephonyManager tm = mContext.getSystemService(TelephonyManager.class);
        if (tm != null && mRegisteredSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            try {
                tm.createForSubscriptionId(mRegisteredSubId)
                        .unregisterTelephonyCallback(mVopsListener);
            } catch (Exception ignored) { }
        }
        mVopsListener = null;
        mRegisteredSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    private final class VopsListener extends TelephonyCallback
            implements TelephonyCallback.ServiceStateListener {
        @Override
        public void onServiceStateChanged(ServiceState ss) {
            if (ss == null) return;
            // Extract LteVopsSupportInfo. If anything along the chain is null,
            // or AOSP reports LTE_STATUS_NOT_AVAILABLE (modem hasn't answered
            // yet), we DON'T touch the cached VoPS. The LG IMS stack treats
            // a vops transition true→false as a hard reason to drop the
            // registration attempt; transient ServiceState updates during
            // attach/handover would otherwise flap us off-on-off and prevent
            // the stack from ever stabilizing.
            //
            // Only an explicit LTE_STATUS_SUPPORTED (→ENABLED) or
            // LTE_STATUS_NOT_SUPPORTED (→DISABLED) updates the cache.
            NetworkRegistrationInfo nri = ss.getNetworkRegistrationInfo(
                    NetworkRegistrationInfo.DOMAIN_PS,
                    android.telephony.AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
            if (nri == null) return;
            DataSpecificRegistrationInfo dsri = nri.getDataSpecificInfo();
            if (dsri == null) return;
            LteVopsSupportInfo vops = dsri.getLteVopsSupportInfo();
            if (vops == null) return;

            int vopsStatus = vops.getVopsSupport();
            int emcStatus  = vops.getEmcBearerSupport();
            int newVoPS;
            int newEmcBs;
            switch (vopsStatus) {
                case LteVopsSupportInfo.LTE_STATUS_SUPPORTED:
                    newVoPS = LGImsNetworkFeature.ENABLED; break;
                case LteVopsSupportInfo.LTE_STATUS_NOT_SUPPORTED:
                    newVoPS = LGImsNetworkFeature.DISABLED; break;
                default:
                    // LTE_STATUS_NOT_AVAILABLE (=1) — modem hasn't reported
                    // yet. Keep last known good state.
                    return;
            }
            switch (emcStatus) {
                case LteVopsSupportInfo.LTE_STATUS_SUPPORTED:
                    newEmcBs = LGImsNetworkFeature.ENABLED; break;
                case LteVopsSupportInfo.LTE_STATUS_NOT_SUPPORTED:
                    newEmcBs = LGImsNetworkFeature.DISABLED; break;
                default:
                    // Use the existing cached value if EmcBs not yet known.
                    synchronized (mFeatureLock) { newEmcBs = mFeature.getEmcBs(); }
                    break;
            }

            int changed;
            ILGImsPhoneProxyCallback cb = mCallback;
            LGImsNetworkFeature snapshot;
            synchronized (mFeatureLock) {
                int c1 = mFeature.updateVoPS(newVoPS);
                int c2 = mFeature.updateEmcBs(newEmcBs);
                changed = c1 | c2;
                snapshot = copyFeature(mFeature);
            }
            if (changed == 0 || cb == null) return;
            try {
                cb.onNetworkFeatureChanged(snapshot, changed);
                Log.i(TAG, "onNetworkFeatureChanged[" + mSlotId + "] "
                        + snapshot + " changed=0x" + Integer.toHexString(changed));
            } catch (RemoteException e) {
                Log.w(TAG, "callback.onNetworkFeatureChanged failed: " + e.getMessage());
            }
        }
    }
    @Override public void start() { ensureVopsListenerRegistered(); }
    @Override public void stop() { unregisterVopsListener(); }
    @Override public void requestNetworkInfo(boolean enable) {
        if (enable) ensureVopsListenerRegistered(); else unregisterVopsListener();
    }
    @Override public LGImsCellInfo getAccessNetworkInfo(int slot) { return new LGImsCellInfo(0); }
    @Override public void clearLastCellInfoRequestTime() { }
    @Override public List<CellInfo> getAllCellInfo() { return Collections.emptyList(); }
    @Override public String getApn(String apn) { return null; }
    @Override public int getCsCallState() { return 0; }
    @Override public int getSignalStrength(int slot) { return -1; }
    @Override public boolean isFdnEnabled() { return false; }
    @Override public boolean isFdnAvailable() { return false; }
    @Override public List<String> getFdnList(String apn) { return new ArrayList<>(); }
    @Override public void setRegistrationState(boolean state) { }
    @Override public boolean isInEmergencyCall() { return false; }
    @Override public int getModemInfo(int type, int slot) { return 0; }
    @Override public int getEfRecord(int efId, boolean isSimulated) { return 0; }
    @Override public int getNrNetworkMode(int slot) { return -1; }
    @Override public int getUeCapabilityVoNr(int slot) { return -1; }
    @Override public void sendEnvelope(LGImsEnvelope envelope) { }
    @Override public void setModemInfo(int type, int slot, int value, String extra) { }
    @Override public void setImsRegistrationStatus(int slot, int status, int rat,
                                                   int regState, int regRat) {
        // Stock LG framework forwarded this to RIL_REQUEST_SET_IMS_REGISTRATION_STATUS
        // (=391) which qcrild's VssCommonModule consumes and bridges to the modem
        // QMI service. The modem uses it as the "IMS-stack ready" signal that
        // lets it switch the LTE attach into combined CS+IMS mode (mLteAttachResultType=1)
        // and accept the apn=ims dedicated bearer. Without it the modem stays
        // at attach-type-0 and AOSP DataNetworkController never fires
        // SETUP_DATA_CALL apn=ims (we observe "Enabling data connectivity(mobile_ims)
        // failed" with no on-modem follow-up).
        //
        // Direct RIL_REQUEST=391 from a vendor daemon SIGSEGVs qcrild because the
        // dispatch-table slot is only populated when a stock LG Java framework
        // does its init RIL round-trip — which we don't do. We log args here as
        // diagnostics so we can size the right OEM_HOOK frame next; real bridging
        // is wired in via the QcRilHook channel (see lge-ims-config-bridge).
        Log.i(TAG, "setImsRegistrationStatus[" + slot + "] status=" + status
                + " rat=" + rat + " regState=" + regState + " regRat=" + regRat);
    }
    @Override public void setImsCallStatus(int slot, int callId, int callState, int callType,
                                           int callMode, int callDir, int callNumber,
                                           int callName, int callNamePresentation) { }
    @Override public void setEmergencyCallState(int state) { }
    @Override public void setEmergencyCallStateForGps(int state, int type) { }
    @Override public void setTrm(int value) { }
    @Override public void setScmMode(int slot, int mode, int value) { }
    @Override public int setScmModeEx(int slot, int mode, int value,
                                      ILGImsInfoCallback cb, boolean isAsync) { return 0; }
    @Override public void setCellInfoListRate(int rate) { }
    @Override public void enableImsDataFlush() { }
    @Override public void exitVoLteEmergencyMode() { }
    @Override public LGImsHVoLtePreference getHVoLtePreference() { return new LGImsHVoLtePreference(); }
    @Override public void setVoiceDomainPreferenceForHVoLte(int preference) { }
    @Override public void setImsCallStateForMSim(int slot, int callId, int callState,
                                                 int callType, int callMode) { }
    @Override public int setImsCallStateExForMSim(int slot, int callId, int callState,
                                                  int callType, int callMode,
                                                  ILGImsInfoCallback cb, boolean isAsync) { return 0; }
    @Override public void setPeerSimSuspend(boolean suspend) { }
    @Override public void setImsConfig(int[] config) { }
    @Override public boolean isNetworkInfoNotificationEnabled() { return false; }
    @Override public LGImsCellInfo requestCellInfo(int slot, ILGImsNetworkInfoCallback cb,
                                                   boolean isAsync) { return new LGImsCellInfo(0); }
    @Override public void addNetworkInfoCallback(ILGImsNetworkInfoCallback cb) { }
    @Override public void removeNetworkInfoCallback(ILGImsNetworkInfoCallback cb) { }
    @Override public void setMobileQualityInfo(int slot, int quality, String extra) { }
    @Override public void setSecureCallInfo(int slot, int type, String extra) { }
    @Override public Bundle call(String method, String arg, Bundle extras) { return null; }
    @Override public int getNrUeCapability(int slot) { return Integer.MIN_VALUE; }
}
