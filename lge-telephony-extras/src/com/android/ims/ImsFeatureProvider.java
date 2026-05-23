// SPDX-FileCopyrightText: 2018 LG Electronics — original implementation
// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
//
// Restored from decompiled stock G910EMW30l framework.jar.
// Thin client over the ContentProvider Ims6 itself registers
// (com.lge.ims.provider.ims_feature). Every hasXxx() method routes
// to ContentResolver.call(); no framework-internal symbols touched.

package com.android.ims;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.lge.config.Features2;

public final class ImsFeatureProvider {

    private static final String TAG = "LGIMS";

    private ImsFeatureProvider() { }

    private static String getProperty(Context context, String key) {
        Bundle b;
        try {
            b = context.getContentResolver().call(LGImsFeature.CONTENT_URI,
                    "getProperty", key, null);
        } catch (IllegalArgumentException | NullPointerException e) {
            log(e.toString());
            b = null;
        }
        return b != null ? b.getString("result", "") : "";
    }

    public static boolean hasCallComposer(Context context) {
        return hasFeature(context, LGImsFeature.FEATURE_CALLCOMPOSER);
    }

    public static boolean hasCdmaLess(Context context) {
        return hasFeature(context, LGImsFeature.FEATURE_CDMALESS);
    }

    public static boolean hasCdmaLessForPhone(Context context) {
        if (isLaop()) {
            return LGImsFeature.getFile(LGImsFeature.FILE_XML_CDMALESS) != null;
        }
        PackageManager pm = context != null ? context.getPackageManager() : null;
        return pm != null && pm.hasSystemFeature(LGImsFeature.FEATURE_CDMALESS);
    }

    public static boolean hasDualVoLte(Context context) {
        return "1".equals(getProperty(context, "persist.vendor.lge.ims.dualvolte"));
    }

    public static boolean hasEab(Context context) {
        return hasFeature(context, LGImsFeature.FEATURE_EAB);
    }

    public static boolean hasFeature(Context context, String feature) {
        if (context == null || TextUtils.isEmpty(feature)) {
            return false;
        }
        return hasFeatureInternal(context, feature, null);
    }

    public static boolean hasFeature(Context context, String feature, int version) {
        if (version <= 0) {
            return hasFeature(context, feature);
        }
        if (context == null || TextUtils.isEmpty(feature)) {
            return false;
        }
        Bundle b = new Bundle();
        b.putInt("version", version);
        return hasFeatureInternal(context, feature, b);
    }

    private static boolean hasFeatureInternal(Context context, String feature, Bundle args) {
        Bundle b;
        try {
            b = context.getContentResolver().call(LGImsFeature.CONTENT_URI,
                    LGImsFeature.METHOD_HAS_FEATURE, feature, args);
        } catch (IllegalArgumentException | NullPointerException e) {
            log(e.toString());
            b = null;
        }
        return b != null && b.getBoolean("result", false);
    }

    public static boolean hasHVoLte(Context context) {
        return hasFeature(context, LGImsFeature.FEATURE_HVOLTE);
    }

    public static boolean hasHVoLteForPhone(Context context) {
        if (isLaop()) {
            return LGImsFeature.getFile(LGImsFeature.FILE_XML_HVOLTE) != null;
        }
        PackageManager pm = context != null ? context.getPackageManager() : null;
        return pm != null && pm.hasSystemFeature(LGImsFeature.FEATURE_HVOLTE);
    }

    public static boolean hasJansky(Context context) {
        return hasFeature(context, LGImsFeature.FEATURE_JANSKY);
    }

    public static boolean hasLgeFeatureIms() {
        return Features2.ims().orElse(false).booleanValue();
    }

    public static boolean hasMediaCamera(Context context, int version) {
        return hasFeature(context, LGImsFeature.FEATURE_MEDIA_CAMERA, version);
    }

    public static boolean hasMediaEvs(Context context) {
        return hasFeature(context, LGImsFeature.FEATURE_MEDIA_EVS);
    }

    public static boolean hasMediaEvsWb(Context context) {
        return hasFeature(context, LGImsFeature.FEATURE_MEDIA_EVS_WB);
    }

    public static boolean hasRtt(Context context) {
        return hasFeature(context, LGImsFeature.FEATURE_RTT);
    }

    public static boolean hasServerIms(Context context) {
        return hasLgeFeatureIms();
    }

    public static boolean hasServerMmpf(Context context) {
        return hasFeature(context, LGImsFeature.FEATURE_SERVER_MMPF);
    }

    public static boolean hasServerSms(Context context) {
        return hasFeature(context, LGImsFeature.FEATURE_SERVER_SMS);
    }

    public static boolean hasServerSmsForPhone(Context context) {
        if (isLaop()) {
            return LGImsFeature.getFile(LGImsFeature.FILE_XML_SMS) != null;
        }
        PackageManager pm = context != null ? context.getPackageManager() : null;
        return pm != null && pm.hasSystemFeature(LGImsFeature.FEATURE_SERVER_SMS);
    }

    public static boolean hasServerSmsSCBM(Context context) {
        return hasFeature(context, LGImsFeature.FEATURE_SERVER_SMS_SCBM);
    }

    public static boolean hasServerSmsSCBMForPhone(Context context) {
        if (isLaop()) {
            return LGImsFeature.getFile(LGImsFeature.FILE_XML_SMS_SCBM) != null;
        }
        PackageManager pm = context != null ? context.getPackageManager() : null;
        return pm != null && pm.hasSystemFeature(LGImsFeature.FEATURE_SERVER_SMS_SCBM);
    }

    public static boolean hasVoLte(Context context) {
        return hasFeature(context, LGImsFeature.FEATURE_VOLTE);
    }

    public static boolean hasVoNr(Context context) {
        return LGImsFeature.FEATURE_VONR;
    }

    public static boolean hasVoWiFi(Context context) {
        return hasFeature(context, LGImsFeature.FEATURE_VOWIFI);
    }

    public static boolean hasVt(Context context) {
        return hasFeature(context, LGImsFeature.FEATURE_VT);
    }

    private static boolean isLaop() {
        return Features2.laop().orElse(false).booleanValue()
                || Features2.lapex().orElse(false).booleanValue();
    }

    private static void log(String s) {
        Log.d(TAG, "[ImsFeatureProvider] " + s);
    }

    public static boolean registerObserver(Context context, ContentObserver observer) {
        if (context == null) {
            return false;
        }
        try {
            context.getContentResolver().registerContentObserver(
                    LGImsFeature.CONTENT_URI, true, observer);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void unregisterObserver(Context context, ContentObserver observer) {
        if (context == null) {
            return;
        }
        try {
            context.getContentResolver().unregisterContentObserver(observer);
        } catch (Throwable t) {
            // ignore
        }
    }
}
