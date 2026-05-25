// SPDX-FileCopyrightText: 2018 LG Electronics — original implementation
// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
//
// Stub of stock com.lge.lgdata.LGDataRuntimeFeatureUtils.
//
// Stock pulls in LGDataInfo, LGDataFeatureSetFactory, Operator, Country —
// each of which drags more LG framework. lgdataservice.apk only calls
// `isOperator(...)` and `isVzwOperators()`. Both return false unless the
// SIM is Verizon (or similar US carriers); on every non-VZW build that's
// what stock would have returned anyway. We keep the rest of the public
// surface compileable with sensible defaults.

package com.lge.lgdata;

import android.os.SystemProperties;
import android.text.TextUtils;

public class LGDataRuntimeFeatureUtils {

    private LGDataRuntimeFeatureUtils() { }

    public static String getCountry()        { return ""; }
    public static String getCountry(int slot) { return ""; }
    public static String getOperator()        { return ""; }
    public static String getOperator(int slot) { return ""; }
    public static String getConstCountry()    { return ""; }
    public static String getConstOperator()   { return ""; }

    public static int getDefaultDataSubPhoneId() { return 0; }

    public static int getPhoneCount() {
        String cfg = SystemProperties.get("persist.radio.multisim.config", "ss");
        if (TextUtils.equals(cfg, "dsds") || TextUtils.equals(cfg, "dsda")) return 2;
        if (TextUtils.equals(cfg, "tsts")) return 3;
        return 1;
    }

    public static boolean isMultiSimEnabled() { return getPhoneCount() > 1; }

    public static boolean isCountry(String... names)               { return false; }
    public static boolean isCountry(int slot, String... names)     { return false; }
    public static boolean isOperator(String... names)              { return false; }
    public static boolean isOperator(int slot, String... names)    { return false; }
    public static boolean isConstCountry(String... names)          { return false; }
    public static boolean isConstOperator(String... names)         { return false; }
    public static boolean isJpSimOperator(String... names)         { return false; }
    public static boolean isKrSimOperator(String... names)         { return false; }

    public static boolean isAttOperators()    { return false; }
    public static boolean isVzwOperators()    { return false; }
    public static boolean isTmusOperators()   { return false; }
    public static boolean isGlobalOperators() { return true; }
    public static boolean isGlobalOperators(int slot) { return true; }
}
