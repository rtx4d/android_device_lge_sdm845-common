// SPDX-FileCopyrightText: 2018 LG Electronics
// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
package com.android.internal.telephony;

import com.android.internal.telephony.ILGImsVoNRCallback;

interface ILGImsVoNR {
    boolean notifyCallState(int slot, int callId, int callState, int callType);
    boolean requestCallPreference(int slot, int preference);
    boolean setImsSession(int slot, int sessionId);
    boolean setImsSignalingForUAC(int slot, String signaling);
    boolean setImsVoice(boolean enabled, int slot);
    boolean setUacCheck(int slot, int category);
    boolean setVoice(boolean enabled, boolean isVoNr);
    void setCallback(ILGImsVoNRCallback callback);
}
