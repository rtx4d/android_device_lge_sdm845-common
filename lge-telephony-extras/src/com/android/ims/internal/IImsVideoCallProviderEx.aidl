// SPDX-FileCopyrightText: 2018 LG Electronics
// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
//
// LG-specific extension for video call control. Reconstructed from
// decompiled framework source. Used by LGImsVideoCallProvider to expose
// camera-control / display-rotation / AR-effect operations from
// InCallUI side into Ims6's ImsVideoCallSession.

package com.android.ims.internal;

import com.android.ims.internal.IImsVideoCallCallbackEx;
import com.android.ims.LGImsVideoCallEffectInfo;

interface IImsVideoCallProviderEx {
    void setCallbackEx(IImsVideoCallCallbackEx cb);
    void captureVideo(String path, int orientation);
    void setMultitaskingState(int state);
    void startBackground();
    void stopBackground();
    void setCameraBrightness(int brightness);
    void setCameraOnOff(int onoff);
    void swapDisplay();
    void updateDisplay(int orientation);
    void setDisplaySize(int width, int height);
    void setDisplayOrientation(int orientation, int displayId);
    void setVideoCallEffect(in LGImsVideoCallEffectInfo info);
}
