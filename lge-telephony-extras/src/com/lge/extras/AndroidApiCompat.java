// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
//
// Compat shims for Android 11 → 15 API drift in stock LG `lgdataservice.apk`.
//
// Stock LG framework added two methods that don't exist in AOSP Android 15:
//   • SignalStrength.getGsmEcio_DRA()         — CDMA-EVDO Ec/Io (DRA scenario)
//   • TelephonyManager.getIccOperatorNumericForData(int slot) — operator
//     numeric for the data SIM slot (was an LG @hide overload of
//     getSimOperatorNumeric)
//
// We can't add methods to the platform classes, so smali call-sites in
// lgdataservice are rewritten from `invoke-virtual <Class>->method(...)`
// to `invoke-static Lcom/lge/extras/AndroidApiCompat;->method(<Class>,...)`.
// The original receiver becomes the first argument.
//
// Behaviour is "safe-default": getGsmEcio_DRA() returns -1 (the sentinel
// stock LG used to indicate "no signal info"), getIccOperatorNumericForData
// delegates to the standard AOSP getSimOperatorNumeric(int).

package com.lge.extras;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

public final class AndroidApiCompat {
    private AndroidApiCompat() { }

    public static int getGsmEcio_DRA(SignalStrength ss) {
        return -1;
    }

    public static String getIccOperatorNumericForData(TelephonyManager tm, int slotId) {
        if (tm == null) return null;
        int subId = SubscriptionManager.getSubscriptionId(slotId);
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return null;
        }
        return tm.createForSubscriptionId(subId).getSimOperator();
    }

    /**
     * PendingIntent.getBroadcast wrapper that forces FLAG_IMMUTABLE when the
     * caller didn't specify FLAG_IMMUTABLE or FLAG_MUTABLE. Android 12+
     * (S+, target 31+) throws IllegalArgumentException without one of those.
     * Stock LG `lgdataservice` was compiled against API 31 but passes raw
     * FLAG_UPDATE_CURRENT (0x8000000) without the mutability bit, so every
     * call would crash on Android 15. Forcing IMMUTABLE matches the
     * recommended default for static-payload broadcasts.
     */
    public static PendingIntent getBroadcast(Context context, int requestCode,
                                             Intent intent, int flags) {
        return PendingIntent.getBroadcast(context, requestCode, intent,
                ensureMutability(flags));
    }

    public static PendingIntent getActivity(Context context, int requestCode,
                                            Intent intent, int flags) {
        return PendingIntent.getActivity(context, requestCode, intent,
                ensureMutability(flags));
    }

    public static PendingIntent getService(Context context, int requestCode,
                                           Intent intent, int flags) {
        return PendingIntent.getService(context, requestCode, intent,
                ensureMutability(flags));
    }

    private static int ensureMutability(int flags) {
        final int mutabilityMask = PendingIntent.FLAG_IMMUTABLE
                | PendingIntent.FLAG_MUTABLE;
        if ((flags & mutabilityMask) == 0) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }
}
