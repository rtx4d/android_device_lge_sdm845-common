// SPDX-FileCopyrightText: 2018 LG Electronics — original API surface
// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
//
// Stock LG `com.lge.os.PropertyUtils` is a singleton that brokers
// vendor properties through a system binder (`lge_property` →
// IPropertyService → `setVendorProperty`/`getVendorProperty`). The
// `int` parameter is a PROP_CODE id into a vendor-side store; only a
// handful of String-keyed entries (mSyspropTable) bypass the service
// and read AOSP SystemProperties via a reflection lookup of
// com.lge.sysprop.ExportedVendorProperties.
//
// On LineageOS we don't have either the vendor IPropertyService nor
// ExportedVendorProperties. None of the callers we ship break if every
// PROP_CODE simply returns the supplied default — IWLAN handover
// state, COTA tracking, BSP fingerprints — none of which our LTE-only
// VoLTE path consumes. So this stub is intentionally a no-op store
// that returns `def` for every read and silently drops every write.
//
// We DO keep the legacy `get(String)` / `get(String, String)` /
// `put(String, String)` AOSP-passthrough methods for callers that
// expect plain SystemProperties access (Ims6 uses these for its own
// keys it owns). They predate the int PROP_CODE path on stock and a
// few classes still rely on them.

package com.lge.os;

import android.os.SystemProperties;

public final class PropertyUtils {
    private static final PropertyUtils INSTANCE = new PropertyUtils();

    private PropertyUtils() { }

    public static PropertyUtils getInstance() {
        return INSTANCE;
    }

    // ---- Stock int-keyed PROP_CODE API ---------------------------------
    //
    // Stock signatures resolve over the `lge_property` binder. We have
    // no such service on LineageOS, so always fall through to `def`
    // (read) or no-op (write). Callers we ship handle missing values
    // gracefully (the IWLAN handover code in lgdataservice's
    // NetworkTrackingService treats "" as "no source iface known").

    public String get(int propCode, String def) {
        return def;
    }

    public boolean getBoolean(int propCode, boolean def) {
        return def;
    }

    public int getInt(int propCode, int def) {
        return def;
    }

    public long getLong(int propCode, long def) {
        return def;
    }

    public void set(int propCode, String val) {
        // no-op
    }

    // ---- Legacy String-keyed AOSP passthrough --------------------------
    //
    // Pre-PROP_CODE callers still expect plain SystemProperties keys.
    // These have nothing to do with the lge_property binder.

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
