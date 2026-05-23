// SPDX-FileCopyrightText: 2018 LG Electronics
// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
package com.android.internal.telephony;

interface ILGImsVoNRCallback {
    oneway void onCallReady(int slot);
    oneway void onHandoffInformation(int slot, int rat, int band, int bw, int rsrp);
    oneway void onNrRegistrationInformation(int slot, int regState);
    oneway void onUacBarredAlleviation(int slot);
    oneway void onUacResponse(int slot, int category, int status, int retryAfter);
}
