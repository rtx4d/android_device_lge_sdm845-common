// SPDX-FileCopyrightText: 2018 LG Electronics — original implementation
// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
//
// Restored from decompiled stock G910EMW30l framework.jar.
// Stock LG ships this in the AOSP package `com.android.ims` via a
// framework patch — Ims6 imports it directly. We re-expose the same
// API surface from the side-jar; ART tolerates the split package because
// no class names collide with AOSP's `ims-common.jar`.
//
// Only the four symbols Ims6 actually references at runtime are kept:
//   - CONTENT_URI                  (com.lge.ims.provider.ims_feature)
//   - FEATURE_VOLTE_OPEN           (persist.product.lge.ims.volte_open != 0)
//   - getCupssConfigDir()          (per-product config root)
//   - getFile(name)                (resolves a config file by lookup order)
// Plus the FEATURE_* string constants, which stock code uses to key
// ContentProvider calls — kept verbatim so any Ims6 string interning
// against them still matches.

package com.android.ims;

import android.net.Uri;
import android.os.SystemProperties;

import java.io.File;

public final class LGImsFeature {

    public static final String AUTHORITY = "com.lge.ims.provider.ims_feature";
    public static final Uri CONTENT_URI = Uri.parse("content://com.lge.ims.provider.ims_feature");

    public static final String FEATURE_CALLCOMPOSER = "com.lge.ims.callcomposer";
    public static final String FEATURE_CDMALESS     = "com.lge.ims.cdmaless";
    public static final String FEATURE_EAB          = "com.lge.ims.service.eab";
    public static final String FEATURE_HVOLTE       = "com.lge.ims.hvolte";
    public static final String FEATURE_JANSKY       = "com.lge.ims.jansky";
    public static final String FEATURE_MEDIA_CAMERA = "com.lge.ims.media.camera";
    public static final String FEATURE_MEDIA_EVS    = "com.lge.ims.media.evs";
    public static final String FEATURE_MEDIA_EVS_WB = "com.lge.ims.media.evs.wb";
    public static final String FEATURE_RTT          = "com.lge.ims.rtt";
    public static final String FEATURE_SERVER_MMPF  = "com.lge.ims.mmpfservice";
    public static final String FEATURE_SERVER_SMS   = "com.lge.server.ims.sms";
    public static final String FEATURE_SERVER_SMS_SCBM = "com.lge.server.ims.sms.scbm";
    public static final String FEATURE_VOLTE        = "com.lge.ims.volte";
    public static final String FEATURE_VOWIFI       = "com.lge.ims.vowifi";
    public static final String FEATURE_VT           = "com.lge.ims.vt";

    public static final String FILE_XML             = "com.lge.ims.xml";
    public static final String FILE_XML_CDMALESS    = "com.lge.ims.cdmaless.xml";
    public static final String FILE_XML_HVOLTE      = "com.lge.ims.hvolte.xml";
    public static final String FILE_XML_SMS         = "com.lge.server.ims.sms.xml";
    public static final String FILE_XML_SMS_SCBM    = "com.lge.server.ims.sms.scbm.xml";

    public static final String KEY_ARG_VERSION      = "version";
    public static final String KEY_RESULT           = "result";
    public static final String METHOD_HAS_FEATURE   = "hasFeature";
    public static final String METHOD_UPDATE_FEATURE = "updateFeature";
    public static final String PATH_COTA            = "/data/shared/cust/config";

    public static final boolean FEATURE_VOLTE_OPEN =
            SystemProperties.getInt("persist.product.lge.ims.volte_open", 0) > 0;
    public static final boolean FEATURE_VONR =
            SystemProperties.getInt("persist.product.lge.ims.vonr", 0) > 0;

    // Stock derives these from Features2.product_overlayfs() and the
    // capp_cupss_op_dir vendor property; until ExportedVendorProperties is
    // wired in we hard-code the modern overlayfs path. If a future build
    // turns out to want the legacy /product/OP path, switch this default.
    public static final String PATH_CUPSS_DEFAULT  = "/product";
    public static final String PATH_CUPSS_ROOTDIR  =
            SystemProperties.get("persist.vendor.lge.cupss.rootdir", PATH_CUPSS_DEFAULT);
    public static final String PATH_CONFIG_LEGACY  = PATH_CUPSS_ROOTDIR + "/config";
    public static final String PATH_CONFIG_ETC     = PATH_CUPSS_ROOTDIR + "/etc";

    private LGImsFeature() { }

    public static String getCupssConfigDir() {
        // Stock: Features2.product_overlayfs() ? PATH_CONFIG_ETC : PATH_CONFIG_LEGACY
        // We always have product_overlayfs in modern builds; pick ETC.
        return PATH_CONFIG_ETC;
    }

    public static String getDefaultCupssPath(String fallback) {
        return fallback != null ? fallback : PATH_CUPSS_DEFAULT;
    }

    public static File getFile(String name) {
        try {
            File f = new File(PATH_COTA, name);
            if (f.exists()) return f;
            f = new File(getCupssConfigDir(), name);
            return f.exists() ? f : null;
        } catch (Exception e) {
            return null;
        }
    }
}
