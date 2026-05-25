// SPDX-FileCopyrightText: 2018 LG Electronics
// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
//
// Callback path from Ims6 video session back to InCallUI. Reconstructed
// from decompiled framework. Counterpart of IImsVideoCallProviderEx.

package com.android.ims.internal;

import com.android.ims.LGImsCallMediaDebugInfo;
import com.android.ims.LGImsVideoCallEffectInfo;

interface IImsVideoCallCallbackEx {
    void changeMediaDebugInfo(in LGImsCallMediaDebugInfo info);
    void changePeerDisplayOrientation(int orientation);
    void handleCallSessionEventEx(int event);
    void handleCallSessionResultEvent(int event, int result);
    void onVideoCallEffectInfoListener(in LGImsVideoCallEffectInfo info);
}
