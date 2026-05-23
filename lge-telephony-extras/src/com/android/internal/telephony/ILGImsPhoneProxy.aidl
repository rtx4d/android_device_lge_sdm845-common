// SPDX-FileCopyrightText: 2018 LG Electronics
// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
package com.android.internal.telephony;

import com.android.internal.telephony.ILGImsInfoCallback;
import com.android.internal.telephony.ILGImsNetworkInfoCallback;
import com.android.internal.telephony.ILGImsPhoneProxyCallback;
import com.android.internal.telephony.LGImsCellInfo;
import com.android.internal.telephony.LGImsEnvelope;
import com.android.internal.telephony.LGImsHVoLtePreference;
import com.android.internal.telephony.LGImsNetworkFeature;
import android.os.Bundle;
import android.telephony.CellInfo;

interface ILGImsPhoneProxy {
    void setCallback(ILGImsPhoneProxyCallback callback);
    void start();
    void stop();
    void requestNetworkInfo(boolean enable);
    LGImsCellInfo getAccessNetworkInfo(int slot);
    LGImsNetworkFeature getNetworkFeature();
    void clearLastCellInfoRequestTime();
    List<CellInfo> getAllCellInfo();
    String[] getPcscfAddress(String apn);
    String getApn(String apn);
    int getCsCallState();
    int getSignalStrength(int slot);
    boolean isFdnEnabled();
    boolean isFdnAvailable();
    List<String> getFdnList(String apn);
    void setRegistrationState(boolean state);
    boolean isInEmergencyCall();
    int getModemInfo(int type, int slot);
    int getEfRecord(int efId, boolean isSimulated);
    int getNrNetworkMode(int slot);
    int getUeCapabilityVoNr(int slot);
    void sendEnvelope(in LGImsEnvelope envelope);
    void setModemInfo(int type, int slot, int value, String extra);
    void setImsRegistrationStatus(int slot, int status, int rat, int regState, int regRat);
    void setImsCallStatus(int slot, int callId, int callState, int callType, int callMode, int callDir, int callNumber, int callName, int callNamePresentation);
    void setEmergencyCallState(int state);
    void setEmergencyCallStateForGps(int state, int type);
    void setTrm(int value);
    void setScmMode(int slot, int mode, int value);
    int setScmModeEx(int slot, int mode, int value, ILGImsInfoCallback callback, boolean isAsync);
    void setCellInfoListRate(int rate);
    void enableImsDataFlush();
    void exitVoLteEmergencyMode();
    LGImsHVoLtePreference getHVoLtePreference();
    void setVoiceDomainPreferenceForHVoLte(int preference);
    void setImsCallStateForMSim(int slot, int callId, int callState, int callType, int callMode);
    int setImsCallStateExForMSim(int slot, int callId, int callState, int callType, int callMode, ILGImsInfoCallback callback, boolean isAsync);
    void setPeerSimSuspend(boolean suspend);
    void setImsConfig(in int[] config);
    boolean isNetworkInfoNotificationEnabled();
    LGImsCellInfo requestCellInfo(int slot, ILGImsNetworkInfoCallback callback, boolean isAsync);
    void addNetworkInfoCallback(ILGImsNetworkInfoCallback callback);
    void removeNetworkInfoCallback(ILGImsNetworkInfoCallback callback);
    void setMobileQualityInfo(int slot, int quality, String extra);
    void setSecureCallInfo(int slot, int type, String extra);
    Bundle call(String method, String arg, in Bundle extras);
    int getNrUeCapability(int slot);
}
