// SPDX-FileCopyrightText: 2018 LG Electronics
// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
//
// LG-specific extension over AOSP IImsCallSession. Adds explicit-call-transfer
// (XCT, RFC 5589) and conference-state query.
//
// Reconstructed from Ims6 smali at
//   com/lge/ims/server/ims/ImsCallSessionImpl$ImsCallSessionExImpl.smali
//   .super Lcom/android/ims/internal/IImsCallSessionEx$Stub;
//   methods:
//     explicitCallTransfer()V
//     getConferenceState()Landroid/telephony/ims/ImsConferenceState;
//
// Without this AIDL `MmTelFeature.createCallSession()` throws
// NoClassDefFoundError trying to instantiate ImsCallSessionExImpl, the
// AOSP framework receives a RemoteException, ImsCall.start() never gets
// a real ImsCallSession (callId=[UNINITIALIZED]), and outgoing calls
// hang in DIALING until Telecom watchdog kills them at STATE_TIMEOUT.

package com.android.ims.internal;

import android.telephony.ims.ImsConferenceState;

interface IImsCallSessionEx {
    void explicitCallTransfer();
    ImsConferenceState getConferenceState();
}
