// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
//
// Minimal stub — Ims6.apk only calls Features2.ims(), .laop(), .lapex().
// Stock LG class also exposes ~250 other feature flags read from
// `ro.vendor.lge.feature.*` properties; callers from outside this jar
// won't link against them, but Ims6 needs at least these three.
//
// Returning `Optional.empty()` whenever the property is unset matches stock
// behaviour. `tryParseBoolean` matches stock semantics ("0"/"1"/"true"/"false";
// anything else → null).

package com.lge.config;

import android.os.SystemProperties;
import java.util.Optional;

public final class Features2 {
    private Features2() { }

    public static Optional<Boolean> ims() {
        return Optional.ofNullable(tryParseBoolean(SystemProperties.get("ro.vendor.lge.feature.ims")));
    }

    public static Optional<Boolean> laop() {
        return Optional.ofNullable(tryParseBoolean(SystemProperties.get("ro.vendor.lge.feature.laop")));
    }

    public static Optional<Boolean> lapex() {
        return Optional.ofNullable(tryParseBoolean(SystemProperties.get("ro.vendor.lge.feature.lapex")));
    }

    public static Optional<Boolean> is_os_upgraded() {
        return Optional.ofNullable(tryParseBoolean(SystemProperties.get("ro.vendor.lge.feature.is_os_upgraded")));
    }

    public static Optional<String> platform_version_original() {
        String v = SystemProperties.get("ro.vendor.lge.feature.platform_version_original");
        return v.isEmpty() ? Optional.empty() : Optional.of(v);
    }

    private static Boolean tryParseBoolean(String s) {
        if (s == null) return null;
        switch (s.toLowerCase(java.util.Locale.US)) {
            case "1":
            case "true":
                return Boolean.TRUE;
            case "0":
            case "false":
                return Boolean.FALSE;
            default:
                return null;
        }
    }
}
