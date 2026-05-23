// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
//
// Thin wrapper over android.os.SystemProperties. Stock LG class exports a
// singleton with `get()` / `put()` plus a handful of typed helpers. Ims6 only
// calls `getInstance()` and then `get(key)`/`put(key,value)`, so the rest is
// trimmed.

package com.lge.os;

import android.os.SystemProperties;

public final class PropertyUtils {
    private static final PropertyUtils INSTANCE = new PropertyUtils();

    private PropertyUtils() { }

    public static PropertyUtils getInstance() {
        return INSTANCE;
    }

    public String get(String key) {
        return SystemProperties.get(key, "");
    }

    public String get(String key, String def) {
        return SystemProperties.get(key, def);
    }

    public void put(String key, String value) {
        SystemProperties.set(key, value);
    }
}
