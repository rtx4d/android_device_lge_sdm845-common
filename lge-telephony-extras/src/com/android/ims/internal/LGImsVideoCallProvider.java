// SPDX-FileCopyrightText: 2018 LG Electronics — original implementation
// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
//
// Abstract base extending AOSP ImsVideoCallProvider with LG-specific
// camera / display / AR-effect operations. Ims6's
// `ImsVideoCallProviderBase extends LGImsVideoCallProvider` uses this
// to expose those operations through IImsVideoCallProviderEx, which
// InCallUI binds to via Connection.VideoProvider.
//
// Without this class, Ims6 hits NoClassDefFoundError on
// `MmTelFeature.createCallSession()` and outgoing calls hang in DIALING.
//
// Port note: stock LG framework.jar exposes `dispose()`,
// `isExtendedInterface()`, and `onTransact()` as protected hooks on
// `ImsVideoCallProvider` so subclasses can plug a secondary binder.
// AOSP keeps those methods package-private, so we cannot override them
// from outside the framework. Consequence: the `IImsVideoCallProviderEx`
// binder is not reachable through `Connection.VideoProvider`'s wire
// protocol — InCallUI cannot drive LG-specific camera/AR commands
// during an active video call. Voice calls and basic ViLTE
// negotiation are unaffected because they go through the AOSP
// `IImsVideoCallProvider` interface that the framework binds directly.
// `mCallbackEx` is set only via `setCallbackEx`, which becomes
// unreachable for the same reason — left in place so subclasses can
// still wire it manually if needed (Ims6 doesn't).

package com.android.ims.internal;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.telephony.Rlog;
import android.telephony.ims.ImsVideoCallProvider;

import com.android.ims.LGImsCallMediaDebugInfo;
import com.android.ims.LGImsVideoCallEffectInfo;

public abstract class LGImsVideoCallProvider extends ImsVideoCallProvider {
    private static final String TAG = "LGImsVideoCallProvider";

    private static final int MSG_SET_CALLBACK_EX = 101;
    private static final int MSG_START_BACKGROUND = 102;
    private static final int MSG_STOP_BACKGROUND = 103;
    private static final int MSG_CAPTURE_VIDEO = 104;
    private static final int MSG_SET_CAMERA_BRIGHTNESS = 105;
    private static final int MSG_SWAP_DISPLAY = 106;
    private static final int MSG_UPDATE_DISPLAY = 107;
    private static final int MSG_SET_DISPLAY_SIZE = 108;
    private static final int MSG_SET_DISPLAY_ORIENTATION = 109;
    private static final int MSG_SET_CAMERA_ONOFF = 110;
    private static final int MSG_SET_MULTITASKING_STATE = 111;
    private static final int MSG_SET_VIDEO_CALL_EFFECT = 113;

    private IImsVideoCallCallbackEx mCallbackEx;
    private final MessageHandler mHandler = new MessageHandler(Looper.getMainLooper());
    private final ImsVideoCallProviderBinderEx mBinderEx = new ImsVideoCallProviderBinderEx();

    protected final class ImsVideoCallProviderBinderEx extends IImsVideoCallProviderEx.Stub {
        @Override public void setCallbackEx(IImsVideoCallCallbackEx cb) {
            mHandler.obtainMessage(MSG_SET_CALLBACK_EX, cb).sendToTarget();
        }
        @Override public void captureVideo(String path, int orientation) {
            mHandler.obtainMessage(MSG_CAPTURE_VIDEO, orientation, 0, path).sendToTarget();
        }
        @Override public void setMultitaskingState(int state) {
            mHandler.obtainMessage(MSG_SET_MULTITASKING_STATE, state, 0).sendToTarget();
        }
        @Override public void startBackground() {
            mHandler.obtainMessage(MSG_START_BACKGROUND).sendToTarget();
        }
        @Override public void stopBackground() {
            mHandler.obtainMessage(MSG_STOP_BACKGROUND).sendToTarget();
        }
        @Override public void setCameraBrightness(int brightness) {
            mHandler.obtainMessage(MSG_SET_CAMERA_BRIGHTNESS, brightness, 0).sendToTarget();
        }
        @Override public void setCameraOnOff(int onoff) {
            mHandler.obtainMessage(MSG_SET_CAMERA_ONOFF, onoff, 0).sendToTarget();
        }
        @Override public void swapDisplay() {
            mHandler.obtainMessage(MSG_SWAP_DISPLAY).sendToTarget();
        }
        @Override public void updateDisplay(int orientation) {
            mHandler.obtainMessage(MSG_UPDATE_DISPLAY, orientation, 0).sendToTarget();
        }
        @Override public void setDisplaySize(int width, int height) {
            mHandler.obtainMessage(MSG_SET_DISPLAY_SIZE, width, height).sendToTarget();
        }
        @Override public void setDisplayOrientation(int orientation, int displayId) {
            mHandler.obtainMessage(MSG_SET_DISPLAY_ORIENTATION, orientation, displayId).sendToTarget();
        }
        @Override public void setVideoCallEffect(LGImsVideoCallEffectInfo info) {
            mHandler.obtainMessage(MSG_SET_VIDEO_CALL_EFFECT, info).sendToTarget();
        }
    }

    private final class MessageHandler extends Handler {
        MessageHandler(Looper looper) { super(looper); }

        @Override
        public void handleMessage(Message msg) {
            log("[tc >> ims] handleMessage :: msg=" + msg.what);
            switch (msg.what) {
                case MSG_SET_CALLBACK_EX:
                    mCallbackEx = (IImsVideoCallCallbackEx) msg.obj;
                    break;
                case MSG_START_BACKGROUND: onStartBackground(); break;
                case MSG_STOP_BACKGROUND: onStopBackground(); break;
                case MSG_CAPTURE_VIDEO: onCaptureVideo((String) msg.obj, msg.arg1); break;
                case MSG_SET_CAMERA_BRIGHTNESS: onSetCameraBrightness(msg.arg1); break;
                case MSG_SWAP_DISPLAY: onSwapDisplay(); break;
                case MSG_UPDATE_DISPLAY: onUpdateDisplay(msg.arg1); break;
                case MSG_SET_DISPLAY_SIZE: onSetDisplaySize(msg.arg1, msg.arg2); break;
                case MSG_SET_DISPLAY_ORIENTATION: onSetDisplayOrientation(msg.arg1, msg.arg2); break;
                case MSG_SET_CAMERA_ONOFF: onSetCameraOnOff(msg.arg1); break;
                case MSG_SET_MULTITASKING_STATE: onSetMultitaskingState(msg.arg1); break;
                case MSG_SET_VIDEO_CALL_EFFECT:
                    onSetVideoCallEffect((LGImsVideoCallEffectInfo) msg.obj);
                    break;
            }
        }
    }

    public void onStartBackground() { logImplRequired("onStartBackground"); }
    public void onStopBackground() { logImplRequired("onStopBackground"); }
    public void onCaptureVideo(String path, int orientation) { logImplRequired("onCaptureVideo"); }
    public void onSetCameraBrightness(int brightness) { logImplRequired("onSetCameraBrightness"); }
    public void onSetCameraOnOff(int onoff) { logImplRequired("onSetCameraOnOff"); }
    public void onSwapDisplay() { logImplRequired("onSwapDisplay"); }
    public void onUpdateDisplay(int orientation) { logImplRequired("onUpdateDisplay"); }
    public void onSetDisplaySize(int width, int height) { logImplRequired("onSetDisplaySize"); }
    public void onSetDisplayOrientation(int orientation, int displayId) { logImplRequired("onSetDisplayOrientation"); }
    public void onSetMultitaskingState(int state) { logImplRequired("onSetMultitaskingState"); }
    public void onSetVideoCallEffect(LGImsVideoCallEffectInfo info) { logImplRequired("onSetVideoCallEffect"); }

    public void changeMediaDebugInfo(LGImsCallMediaDebugInfo info) {
        IImsVideoCallCallbackEx cb = mCallbackEx;
        if (cb != null) {
            try { cb.changeMediaDebugInfo(info); }
            catch (RemoteException e) { e.printStackTrace(); }
        }
    }

    public void changePeerDisplayOrientation(int orientation) {
        IImsVideoCallCallbackEx cb = mCallbackEx;
        if (cb != null) {
            try { cb.changePeerDisplayOrientation(orientation); }
            catch (RemoteException e) { e.printStackTrace(); }
        }
    }

    public void handleCallSessionEventEx(int event) {
        IImsVideoCallCallbackEx cb = mCallbackEx;
        if (cb != null) {
            try { cb.handleCallSessionEventEx(event); }
            catch (RemoteException e) { e.printStackTrace(); }
            // Stock fires `dispose()` here on event==999, but AOSP keeps
            // dispose() package-private so we can't call it from this
            // package. Subclasses can override this method and call their
            // own teardown if needed.
        }
    }

    public void handleCallSessionResultEvent(int event, int result) {
        IImsVideoCallCallbackEx cb = mCallbackEx;
        if (cb != null) {
            try { cb.handleCallSessionResultEvent(event, result); }
            catch (RemoteException e) { e.printStackTrace(); }
        }
    }

    public void onVideoCallEffectInfoListener(LGImsVideoCallEffectInfo info) {
        IImsVideoCallCallbackEx cb = mCallbackEx;
        if (cb != null) {
            try { cb.onVideoCallEffectInfoListener(info); }
            catch (RemoteException e) { e.printStackTrace(); }
        }
    }

    private static void log(String msg) {
        Rlog.d(TAG, msg);
    }

    private static void logImplRequired(String name) {
        log("Subclass MUST implement this method; " + name);
    }
}
