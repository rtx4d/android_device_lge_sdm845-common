// SPDX-FileCopyrightText: 2018 LG Electronics — original implementation
// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
//
// Restored from decompiled stock G910EMW30l framework.jar.

package com.lge.os;

import android.os.SystemProperties;
import com.lge.config.Features2;

public class Build {
    public static class CA_TARGET {
        public static final String OPERATOR = SystemProperties.get("ro.vendor.lge.build.target_operator", "unknown");
        public static final String COUNTRY = SystemProperties.get("ro.vendor.lge.build.target_country", "unknown");
        public static final String REGION = SystemProperties.get("ro.vendor.lge.build.target_region", "unknown");
    }

    public static class LGAPI {
        public static final String VERSION = SystemProperties.get("ro.lge.apiversion", "unknown");
    }

    public static class LGUI_VERSION {
        // Mapped from `ro.vendor.lge.lguiversion` string (e.g. "9.6") to the
        // integer code stock LG framework uses (e.g. 96). Mirrors the switch
        // table in stock Build.java; ordering matters because Ims6 calls
        // sites like `RELEASE >= 80` to gate features by LG UI generation.
        // Default 71 ("V7.1") matches stock fallback when the property is
        // absent — same value the property defaults to in the original code.
        public static final int RELEASE;

        static {
            String v = SystemProperties.get("ro.vendor.lge.lguiversion", "4.1");
            int code;
            switch (v) {
                case "4.0": code = 1;  break;
                case "4.1": code = 2;  break;
                case "4.2": code = 3;  break;
                case "5.0": code = 4;  break;
                case "5.1": code = 5;  break;
                case "6.0": code = 6;  break;
                case "6.1": code = 7;  break;
                case "6.2": code = 8;  break;
                case "7.0": code = 9;  break;
                case "7.1": code = 71; break;
                case "7.2": code = 72; break;
                case "8.0": code = 80; break;
                case "9.0": code = 90; break;
                case "9.1": code = 91; break;
                case "9.2": code = 92; break;
                case "9.3": code = 93; break;
                case "9.4": code = 94; break;
                case "9.5": code = 95; break;
                case "9.6": code = 96; break;
                case "10.0": code = 100; break;
                default: code = 2; break; // matches stock fallback for "4.1"
            }
            RELEASE = code;
        }
    }

    public static class LGUI_VERSION_CODES {
        public static final int BASE = 1;
        public static final int EMERALD = 2;
        public static final int PEARL = 3;
        public static final int RUBY = 4;
        public static final int GARNET = 5;
        public static final int TOPAZ = 6;
        public static final int OPAL = 7;
        public static final int ONYX = 8;
        public static final int PERIDOT = 9;
        public static final int V7_1 = 71;
        public static final int V7_2 = 72;
        public static final int V8_0 = 80;
        public static final int V9_0 = 90;
        public static final int V9_1 = 91;
        public static final int V9_2 = 92;
        public static final int V9_3 = 93;
        public static final int V9_4 = 94;
        public static final int V9_5 = 95;
        public static final int V9_6 = 96;
        public static final int V10_0 = 100;
    }

    public static class VERSION {
        public static final String ORIGINAL_RELEASE = Features2.platform_version_original().orElse("");
        public static final boolean IS_OS_UPGRADED = Features2.is_os_upgraded().orElse(false).booleanValue();
    }
}

