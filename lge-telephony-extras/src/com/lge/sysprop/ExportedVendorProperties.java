// SPDX-FileCopyrightText: 2018 LG Electronics — sysprop_library generated
// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
//
// Stock LG generates this class from a `.sysprop` definition (Soong's
// sysprop_library). Full stock surface has ~250 typed accessors; we keep
// only the ones Ims6.apk actually references. All 14 plus the generic
// get()/put() helpers were enumerated by grepping the decompiled APK.
//
// If a future Ims6 update or sibling LG app pulls in another property,
// add a method here following the same pattern. Long term it would be
// cleaner to ship a real .sysprop file under sysprop_library so Soong
// regenerates this from a spec, but the hand-rolled stub keeps the MVP
// dependency-free.

package com.lge.sysprop;

import android.os.SystemProperties;

import java.util.Optional;

public final class ExportedVendorProperties {

    private ExportedVendorProperties() { }

    // Ims6 calls .get(key, default) for ad-hoc lookups; mirror stock semantics.
    public static String get(String key, String def) {
        String v = SystemProperties.get(key, def);
        return v == null ? def : v;
    }

    // Ims6 calls .put(key, value) when a property change has to be persisted.
    public static void put(String key, String value) {
        SystemProperties.set(key, value == null ? "" : value);
    }

    // ---- typed read accessors ----

    public static Optional<String> swversion_svn() {
        return ofProp("ro.vendor.lge.swversion_svn");
    }

    public static Optional<String> laop_brand() {
        return ofProp("ro.vendor.lge.laop.brand");
    }

    public static Optional<String> ntcode() {
        return ofProp("persist.vendor.lge.ntcode");
    }

    public static Optional<String> ims_dualvolte() {
        return ofProp("persist.vendor.lge.ims.dualvolte");
    }

    public static Optional<String> data_ltedsds() {
        return ofProp("persist.vendor.lge.data.ltedsds");
    }

    public static Optional<String> ril_ecclist_withcat() {
        return ofProp("vendor.lge.ril.ecclist.withcat");
    }

    public static Optional<String> ril_ecclist_withcat1() {
        return ofProp("vendor.lge.ril.ecclist.withcat1");
    }

    public static Optional<String> data_lte_ipv_rmnet_data0() {
        return ofProp("vendor.lge.data.lte.ipv.rmnet_data0");
    }

    public static Optional<String> data_lte_ipv_rmnet_data1() {
        return ofProp("vendor.lge.data.lte.ipv.rmnet_data1");
    }

    public static Optional<String> data_lte_ipv_rmnet_data2() {
        return ofProp("vendor.lge.data.lte.ipv.rmnet_data2");
    }

    public static Optional<String> data_lte_ipv_rmnet_data3() {
        return ofProp("vendor.lge.data.lte.ipv.rmnet_data3");
    }

    public static Optional<String> data_lte_ipv_rmnet_data4() {
        return ofProp("vendor.lge.data.lte.ipv.rmnet_data4");
    }

    public static Optional<String> data_lte_ipv_rmnet_data5() {
        return ofProp("vendor.lge.data.lte.ipv.rmnet_data5");
    }

    public static Optional<String> data_lte_ipv_rmnet_data6() {
        return ofProp("vendor.lge.data.lte.ipv.rmnet_data6");
    }

    public static Optional<String> data_lte_ipv_rmnet_data7() {
        return ofProp("vendor.lge.data.lte.ipv.rmnet_data7");
    }

    public static Optional<String> capp_cupss_op_dir() {
        return ofProp("ro.vendor.lge.capp_cupss.op.dir");
    }

    private static Optional<String> ofProp(String key) {
        String v = SystemProperties.get(key);
        return v == null || v.isEmpty() ? Optional.empty() : Optional.of(v);
    }
}
