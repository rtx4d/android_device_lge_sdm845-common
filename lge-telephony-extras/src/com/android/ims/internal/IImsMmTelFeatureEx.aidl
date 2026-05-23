// SPDX-FileCopyrightText: 2018 LG Electronics
// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
package com.android.ims.internal;

import android.telephony.ims.ImsCallProfile;
import com.android.ims.LGImsECallConfig;
import com.android.ims.internal.IImsSipEvent;

interface IImsMmTelFeatureEx {
    IImsSipEvent getSipEvent();
    LGImsECallConfig getEmergencyCallConfig();
    boolean isEmergencyCallAvailableOverWfc();
    boolean isEcbmSupported();
    ImsCallProfile getIncomingCallInfo();
}
