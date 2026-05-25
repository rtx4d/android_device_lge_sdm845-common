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
import android.telephony.SubscriptionManager;
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

    @Override public void setCallback(ILGImsPhoneProxyCallback cb) { }
    @Override public void start() { }
    @Override public void stop() { }
    @Override public void requestNetworkInfo(boolean enable) { }
    @Override public LGImsCellInfo getAccessNetworkInfo(int slot) { return new LGImsCellInfo(0); }
    @Override public LGImsNetworkFeature getNetworkFeature() { return new LGImsNetworkFeature(); }
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
                                                   int regState, int regRat) { }
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
