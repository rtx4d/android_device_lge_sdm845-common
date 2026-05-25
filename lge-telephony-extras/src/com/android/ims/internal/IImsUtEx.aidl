// SPDX-FileCopyrightText: 2018 LG Electronics
// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
//
// Reconstructed from Ims6's ImsUtImpl$ImsUtExImpl smali
// (com/lge/ims/server/ims/ImsUtImpl$ImsUtExImpl.smali).
//
// LG-specific extension over AOSP IImsUt for XCAP/UT supplementary
// services. Adds *ForLine variants (multi-line operator support) and
// extended call-barring transactions. Without this AIDL Ims6's
// `ImsCallApp.getUtInterface()` throws NoClassDefFoundError on
// `IImsUtEx$Stub` and the entire MMTel feature fails to start —
// AOSP framework never receives notifyFeatureState(STATE_READY)
// nor capabilities VOICE_OVER_LTE.
//
// Method signatures lifted verbatim from smali:
//   isServiceBlockedByNetwork()Z
//   queryCLIPForLine(Ljava/lang/String;)I
//   queryCLIRForLine(Ljava/lang/String;)I
//   queryCOLPForLine(Ljava/lang/String;)I
//   queryCOLRForLine(Ljava/lang/String;)I
//   queryCallBarringForLine(Ljava/lang/String;II)I
//   queryCallForwardForLine(Ljava/lang/String;ILjava/lang/String;I)I
//   queryCallWaitingForLine(Ljava/lang/String;)I
//   transactForLine(Ljava/lang/String;Landroid/os/Bundle;)I
//   updateCLIPForLine(Ljava/lang/String;Z)I
//   updateCLIRForLine(Ljava/lang/String;I)I
//   updateCOLPForLine(Ljava/lang/String;Z)I
//   updateCOLRForLine(Ljava/lang/String;I)I
//   updateCallBarringForLine(Ljava/lang/String;II[Ljava/lang/String;)I
//   updateCallForwardForLine(Ljava/lang/String;IILjava/lang/String;II)I
//   updateCallWaitingForLine(Ljava/lang/String;ZI)I
//   updateExtendCallBarring(ILandroid/os/Bundle;)I
//   updateExtendCallBarringForLine(Ljava/lang/String;ILandroid/os/Bundle;)I

package com.android.ims.internal;

import android.os.Bundle;

interface IImsUtEx {
    boolean isServiceBlockedByNetwork();

    int queryCLIPForLine(String line);
    int queryCLIRForLine(String line);
    int queryCOLPForLine(String line);
    int queryCOLRForLine(String line);
    int queryCallBarringForLine(String line, int cbType, int serviceClass);
    int queryCallForwardForLine(String line, int condition, String number, int serviceClass);
    int queryCallWaitingForLine(String line);

    int transactForLine(String line, in Bundle params);

    int updateCLIPForLine(String line, boolean enable);
    int updateCLIRForLine(String line, int clirMode);
    int updateCOLPForLine(String line, boolean enable);
    int updateCOLRForLine(String line, int colrMode);
    int updateCallBarringForLine(String line, int cbType, int action, in String[] barrList);
    int updateCallForwardForLine(String line, int action, int condition, String number,
                                 int timeSeconds, int serviceClass);
    int updateCallWaitingForLine(String line, boolean enable, int serviceClass);

    int updateExtendCallBarring(int reason, in Bundle params);
    int updateExtendCallBarringForLine(String line, int reason, in Bundle params);
}
