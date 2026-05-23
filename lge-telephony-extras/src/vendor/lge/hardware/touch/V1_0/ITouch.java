// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
//
// Minimal stub of vendor.lge.hardware.touch@1.0::ITouch.
//
// Stock HIDL Java binding is several hundred lines of generated boilerplate
// (Proxy/Stub plus a dozen data types: AppInfo, CallStatus, LockScreenStatus,
// WatchFontEffectConfig, etc). Ims6 uses the touch HAL only for visual
// feedback during calls (`setCallState(int)`). On a LineageOS build this
// HAL service is not exposed to apps — `ITouch.getService()` would always
// return null even with the full binding.
//
// This stub provides just enough Class metadata for ART to resolve
// references in Ims6 dex (load class, look up method symbols). The
// callers in `LGExtApi$TouchService` are wrapped in try/catch and gracefully
// degrade when `mTouchProxy == null`, so returning null from `getService()`
// here lets Ims6 boot and just skips the touch-feedback path.
//
// If we ever want real touch-during-call feedback we can replace this with
// the full HIDL Java binding (or migrate to AIDL once LineageOS adds the
// HAL declaration to the device manifest).

package vendor.lge.hardware.touch.V1_0;

import android.os.IHwInterface;
import android.os.RemoteException;

public interface ITouch extends IHwInterface {
    String kInterfaceName = "vendor.lge.hardware.touch@1.0::ITouch";

    /** Stock signature returns int, but Ims6 ignores the return value. */
    int setCallState(int callState) throws RemoteException;

    /**
     * Stock returns an ITouch proxy bound to the registered HIDL service.
     * On LineageOS the service is not registered so this always returns null.
     * Ims6's TouchService wrapper checks for null and falls back to a no-op.
     */
    static ITouch getService() throws RemoteException {
        return null;
    }

    static ITouch getService(boolean retry) throws RemoteException {
        return null;
    }

    static ITouch getService(String serviceName) throws RemoteException {
        return null;
    }

    static ITouch getService(String serviceName, boolean retry) throws RemoteException {
        return null;
    }
}
