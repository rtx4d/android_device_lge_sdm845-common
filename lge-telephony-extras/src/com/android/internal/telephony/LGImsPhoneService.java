// SPDX-FileCopyrightText: 2018 LG Electronics — original implementation
// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
//
// Stub of stock LGImsPhoneService. Stock class registers a binder service
// "com.lge.ims.phone" from inside the AOSP Phone process during init —
// that init point doesn't exist in LineageOS, so the service is never
// registered and `getService()` returns null. Ims6 imports this class
// only to call the static `getService()` and `asBinder()` (which goes
// through ILGImsPhoneService.Stub.asInterface, so any non-null Binder
// result is fine for compile-time linking).

package com.android.internal.telephony;

import android.content.Context;
import android.os.IBinder;
import android.os.ServiceManager;

public final class LGImsPhoneService {
    public static final String ACTION_IMS_PHONE_STARTED = "com.lge.ims.action.IMS_PHONE_STARTED";
    private static final String SERVICE_INTERFACE = "com.lge.ims.phone";

    private LGImsPhoneService() { }

    public static ILGImsPhoneService getService() {
        return ILGImsPhoneService.Stub.asInterface(
                ServiceManager.getService(SERVICE_INTERFACE));
    }

    /**
     * Stock-API parity: stock Phone-process calls this from PhoneFactory at
     * boot. On LineageOS no caller hits this entry point, so the body is a
     * no-op — but Ims6 references it via reflection in some debug paths,
     * keep the symbol resolvable.
     */
    public static void create(Context context, Object[] phones, Object[] commands) {
        // intentionally empty — no Phone-process integration on this build
    }

    /** Returned binder is null on LineageOS — see class doc. */
    public IBinder asBinder() {
        return null;
    }
}
