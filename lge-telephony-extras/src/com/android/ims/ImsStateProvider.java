// SPDX-FileCopyrightText: 2018 LG Electronics — original implementation
// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
//
// Restored from decompiled stock G910EMW30l framework.jar.
//
// Stock referenced `com.lge.lgdata.Operator.ORG` ("Orange") inside
// VoWiFiActivationState.getOperator() to map a Polish carrier string;
// inlined here to avoid pulling in the whole `com.lge.lgdata` package
// just for one constant.
//
// Decompiled `getInt()` had unreachable code after a return statement
// (a known JADX issue with try-finally combined with cursor.close()).
// Reconstructed cleanly.

package com.android.ims;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.sysprop.TelephonyProperties;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import android.util.Log;

import java.util.Locale;

public final class ImsStateProvider {

    public static final String AUTHORITY = "com.lge.ims.provider.ims_state";

    public static final String COLUMN_CALL_STATE             = "call_state";
    public static final String COLUMN_CONNECTED_CALL_ON_WIFI = "connected_call_on_wifi";
    public static final String COLUMN_NETWORK_TYPE           = "network_type";
    public static final String COLUMN_OPERATOR               = "operator";
    public static final String COLUMN_SERVICE_STATUS         = "service_status";
    public static final String COLUMN_SLOT_ID                = "slot_id";
    public static final String COLUMN_STATE                  = "state";
    public static final String COLUMN_SUB_ID                 = "sub_id";
    public static final String COLUMN_VOLTE_PROVISIONED      = "volte_provisioned";
    public static final String COLUMN_VOLTE_ROAMING          = "volte_roaming";
    public static final String COLUMN_VT_PROVISIONED         = "vt_provisioned";
    public static final String COLUMN_VT_ROAMING             = "vt_roaming";
    public static final String COLUMN_WFC_PROVISIONED        = "wfc_provisioned";

    public static final int NETWORK_TYPE_WIFI = 31;
    public static final int STATE_ACTIVE      = 1;
    public static final int STATE_INACTIVE    = 0;

    private static final int DEFAULT_PHONE_ID = 0;
    private static final int DEFAULT_SUB_ID   = Integer.MAX_VALUE;
    private static final int INVALID_VALUE    = -1;
    private static final int PRIMARY_KEY_BASE = 1;

    private static final String TAG = "LGIMS";
    private static final boolean DBG;
    private static final boolean MSIM;
    private static final String MSIM_CONFIG;

    static {
        MSIM_CONFIG = TelephonyProperties.multi_sim_config().orElse("");
        MSIM = MSIM_CONFIG.equals("dsds")
                || MSIM_CONFIG.equals("dsda")
                || MSIM_CONFIG.equals("tsts");
        DBG = !"user".equals(Build.TYPE);
    }

    private ImsStateProvider() { }

    public static final class CallState {
        public static final Uri CONTENT_URI =
                Uri.parse("content://com.lge.ims.provider.ims_state/call_state");
        public static final String TABLE_NAME = "call_state";

        public static int getConnectedCallOnWifi(ContentResolver cr) {
            return getConnectedCallOnWifi(cr, getDefaultSubId(cr, CONTENT_URI));
        }

        public static int getConnectedCallOnWifi(ContentResolver cr, int subId) {
            return getCallStateInt(cr, selectForSubId(subId), COLUMN_CONNECTED_CALL_ON_WIFI, 0);
        }

        public static int getState(ContentResolver cr) {
            return getState(cr, getDefaultSubId(cr, CONTENT_URI));
        }

        public static int getState(ContentResolver cr, int subId) {
            return getCallStateInt(cr, selectForSubId(subId), COLUMN_STATE, 0);
        }

        public static boolean registerObserver(ContentResolver cr, ContentObserver obs) {
            return registerContentObserver(cr, obs, CONTENT_URI);
        }

        public static void unregisterObserver(ContentResolver cr, ContentObserver obs) {
            unregisterContentObserver(cr, obs);
        }
    }

    public static final class RegState {
        public static final Uri CONTENT_URI =
                Uri.parse("content://com.lge.ims.provider.ims_state/reg_state");
        public static final String TABLE_NAME = "reg_state";

        public static int getNetworkType(ContentResolver cr) {
            return getNetworkType(cr, getDefaultSubId(cr, CONTENT_URI));
        }

        public static int getNetworkType(ContentResolver cr, int subId) {
            return getRegStateInt(cr, selectForSubId(subId), COLUMN_NETWORK_TYPE, 0);
        }

        public static int getState(ContentResolver cr) {
            return getState(cr, getDefaultSubId(cr, CONTENT_URI));
        }

        public static int getState(ContentResolver cr, int subId) {
            return getRegStateInt(cr, selectForSubId(subId), COLUMN_STATE, 0);
        }

        public static boolean registerObserver(ContentResolver cr, ContentObserver obs) {
            return registerContentObserver(cr, obs, CONTENT_URI);
        }

        public static void unregisterObserver(ContentResolver cr, ContentObserver obs) {
            unregisterContentObserver(cr, obs);
        }
    }

    public static final class RoamingState {
        public static final Uri CONTENT_URI =
                Uri.parse("content://com.lge.ims.provider.ims_state/roaming_state");
        public static final String TABLE_NAME = "roaming_state";

        public static int getVoLteRoaming(ContentResolver cr) {
            return getVoLteRoaming(cr, getDefaultSubId(cr, CONTENT_URI));
        }

        public static int getVoLteRoaming(ContentResolver cr, int subId) {
            int v = getRoamingStateInt(cr, selectForSubId(subId), COLUMN_VOLTE_ROAMING, -1);
            if (v != -1) return v;
            int phoneId = getPhoneId(subId, -1);
            if (phoneId < 0) return 0;
            log("FailOver :: phoneId=" + phoneId);
            return getVoLteRoamingForPhoneId(cr, phoneId);
        }

        public static int getVoLteRoamingForPhoneId(ContentResolver cr, int phoneId) {
            return getRoamingStateInt(cr, selectForPhoneId(phoneId), COLUMN_VOLTE_ROAMING, 0);
        }

        public static int getVtRoaming(ContentResolver cr) {
            return getVtRoaming(cr, getDefaultSubId(cr, CONTENT_URI));
        }

        public static int getVtRoaming(ContentResolver cr, int subId) {
            int v = getRoamingStateInt(cr, selectForSubId(subId), COLUMN_VT_ROAMING, -1);
            if (v != -1) return v;
            int phoneId = getPhoneId(subId, -1);
            if (phoneId < 0) return 0;
            log("FailOver :: phoneId=" + phoneId);
            return getVtRoamingForPhoneId(cr, phoneId);
        }

        public static int getVtRoamingForPhoneId(ContentResolver cr, int phoneId) {
            return getRoamingStateInt(cr, selectForPhoneId(phoneId), COLUMN_VT_ROAMING, 0);
        }

        public static boolean registerObserver(ContentResolver cr, ContentObserver obs) {
            return registerContentObserver(cr, obs, CONTENT_URI);
        }

        public static void unregisterObserver(ContentResolver cr, ContentObserver obs) {
            unregisterContentObserver(cr, obs);
        }
    }

    public static final class VoLteState {
        public static final Uri CONTENT_URI =
                Uri.parse("content://com.lge.ims.provider.ims_state/volte_state");
        public static final String TABLE_NAME = "volte_state";

        public static final int SERVICE_NONE = 0;
        public static final int SERVICE_VOIP = 1;
        public static final int SERVICE_VT   = 2;
        public static final int SERVICE_UC   = 3;

        public static int getCallState(ContentResolver cr) {
            return getCallState(cr, getDefaultSubId(cr, CONTENT_URI));
        }

        public static int getCallState(ContentResolver cr, int subId) {
            return getVoLteStateInt(cr, selectForSubId(subId), COLUMN_CALL_STATE, 0);
        }

        public static int getServiceStatus(ContentResolver cr) {
            return getServiceStatus(cr, getDefaultSubId(cr, CONTENT_URI));
        }

        public static int getServiceStatus(ContentResolver cr, int subId) {
            return getVoLteStateInt(cr, selectForSubId(subId), COLUMN_SERVICE_STATUS, 0);
        }

        public static int getVoLteProvisioned(ContentResolver cr) {
            return getVoLteProvisioned(cr, getDefaultSubId(cr, CONTENT_URI));
        }

        public static int getVoLteProvisioned(ContentResolver cr, int subId) {
            return failOverGet(cr, subId, COLUMN_VOLTE_PROVISIONED);
        }

        public static int getVoLteProvisionedForPhoneId(ContentResolver cr, int phoneId) {
            return getVoLteStateInt(cr, selectForPhoneId(phoneId), COLUMN_VOLTE_PROVISIONED, 0);
        }

        public static int getVtProvisioned(ContentResolver cr) {
            return getVtProvisioned(cr, getDefaultSubId(cr, CONTENT_URI));
        }

        public static int getVtProvisioned(ContentResolver cr, int subId) {
            return failOverGet(cr, subId, COLUMN_VT_PROVISIONED);
        }

        public static int getVtProvisionedForPhoneId(ContentResolver cr, int phoneId) {
            return getVoLteStateInt(cr, selectForPhoneId(phoneId), COLUMN_VT_PROVISIONED, 0);
        }

        public static int getWfcProvisioned(ContentResolver cr) {
            return getWfcProvisioned(cr, getDefaultSubId(cr, CONTENT_URI));
        }

        public static int getWfcProvisioned(ContentResolver cr, int subId) {
            return failOverGet(cr, subId, COLUMN_WFC_PROVISIONED);
        }

        public static int getWfcProvisionedForPhoneId(ContentResolver cr, int phoneId) {
            return getVoLteStateInt(cr, selectForPhoneId(phoneId), COLUMN_WFC_PROVISIONED, 0);
        }

        private static int failOverGet(ContentResolver cr, int subId, String column) {
            int v = getVoLteStateInt(cr, selectForSubId(subId), column, -1);
            if (v != -1) return v;
            int phoneId = getPhoneId(subId, -1);
            if (phoneId < 0) return 0;
            log("FailOver :: phoneId=" + phoneId);
            return getVoLteStateInt(cr, selectForPhoneId(phoneId), column, 0);
        }

        public static boolean registerObserver(ContentResolver cr, ContentObserver obs) {
            return registerContentObserver(cr, obs, CONTENT_URI);
        }

        public static void unregisterObserver(ContentResolver cr, ContentObserver obs) {
            unregisterContentObserver(cr, obs);
        }
    }

    public static final class VoWiFiActivationState {
        public static final Uri CONTENT_URI =
                Uri.parse("content://com.lge.ims.provider.ims_state/vowifi_activation_state");
        public static final String TABLE_NAME = "vowifi_activation_state";

        // Carrier-specific quirk: stock Operator class maps "NJU" + country "PL"
        // (Nju Mobile Poland) onto Orange. Inlined from com.lge.lgdata.Operator.
        private static final String OPERATOR_ORG = "ORG";

        private static String getOperator(String operator, String country) {
            if (TextUtils.isEmpty(country)) return operator;
            String op = operator;
            if ("NJU".equals(op) && "PL".equals(country)) op = OPERATOR_ORG;
            return String.format(Locale.US, "%s-%s", op, country);
        }

        public static int getState(ContentResolver cr, int slotId,
                                   String operator, String country) {
            return getInt(cr, CONTENT_URI, getWhereClause(slotId, operator, country),
                    COLUMN_STATE, 0);
        }

        private static String getWhereClause(int slotId, String operator, String country) {
            return String.format(Locale.US, "%s='%d' AND %s='%s'",
                    COLUMN_SLOT_ID, slotId,
                    COLUMN_OPERATOR, getOperator(operator, country));
        }

        public static boolean registerObserver(ContentResolver cr, ContentObserver obs) {
            return registerContentObserver(cr, obs, CONTENT_URI);
        }

        public static void unregisterObserver(ContentResolver cr, ContentObserver obs) {
            unregisterContentObserver(cr, obs);
        }
    }

    // ---------- statics shared across the inner state classes ----------

    public static int getCallStateInt(ContentResolver cr, String where, String col, int def) {
        return getInt(cr, CallState.CONTENT_URI, where, col, def);
    }

    public static int getRegStateInt(ContentResolver cr, String where, String col, int def) {
        return getInt(cr, RegState.CONTENT_URI, where, col, def);
    }

    public static int getRoamingStateInt(ContentResolver cr, String where, String col, int def) {
        return getInt(cr, RoamingState.CONTENT_URI, where, col, def);
    }

    public static int getVoLteStateInt(ContentResolver cr, String where, String col, int def) {
        return getInt(cr, VoLteState.CONTENT_URI, where, col, def);
    }

    static int getDefaultSubId(ContentResolver cr, Uri uri) {
        if (isMultiSimEnabled()) {
            return SubscriptionManager.getDefaultDataSubscriptionId();
        }
        int subId = getSubId(0);
        if (isValidSubId(subId)) return subId;
        log("Invalid default subscription; SUB" + subId);
        return getSubId(cr, uri, 0);
    }

    static int getInt(ContentResolver cr, Uri uri, String selection, String column, int def) {
        if (cr == null || uri == null) {
            loge("getInt :: cr=" + cr + ", uri=" + uri);
            return def;
        }
        Cursor c = null;
        try {
            c = cr.query(uri, null, selection, null, null);
            if (c == null || !c.moveToFirst()) return def;
            int idx = c.getColumnIndex(column);
            int value = idx < 0 ? def : c.getInt(idx);
            if (DBG) {
                log("getInt :: column=" + column + ", value=" + value + " at " + idx);
            }
            return value;
        } catch (Throwable t) {
            loge(t.toString());
            return def;
        } finally {
            if (c != null) c.close();
        }
    }

    private static int getPhoneCount() {
        if (!isMultiSimEnabled()) return 1;
        return MSIM_CONFIG.equals("tsts") ? 3 : 2;
    }

    static int getPhoneId(int subId, int fallback) {
        int phoneId = SubscriptionManager.getPhoneId(subId);
        if (phoneId >= 0 && phoneId < getPhoneCount()) return phoneId;
        if (fallback >= 0 || isMultiSimEnabled()) return fallback;
        return 0;
    }

    private static int getPrimaryKey(int phoneId) {
        return (phoneId < 0 ? 0 : phoneId) + PRIMARY_KEY_BASE;
    }

    private static int getSubId(int phoneId) {
        int[] subIds = SubscriptionManager.getSubId(phoneId);
        return (subIds == null || subIds.length == 0) ? -1 : subIds[0];
    }

    private static int getSubId(ContentResolver cr, Uri uri, int phoneId) {
        return getInt(cr, uri, selectForPrimaryKey(getPrimaryKey(phoneId)), COLUMN_SUB_ID, -1);
    }

    private static boolean isMultiSimEnabled() { return MSIM; }

    private static boolean isValidSubId(int subId) {
        return subId >= 0 && subId <= Integer.MAX_VALUE - 1;
    }

    static void log(String s) { Log.d(TAG, "[ImsStateProvider] " + s); }
    static void loge(String s) { Log.e(TAG, "[ImsStateProvider] " + s); }

    static boolean registerContentObserver(ContentResolver cr, ContentObserver obs, Uri uri) {
        try {
            cr.registerContentObserver(uri, true, obs);
            return true;
        } catch (Throwable t) {
            if (DBG) log(t.toString());
            return false;
        }
    }

    static String selectForPhoneId(int phoneId) {
        return selectForPrimaryKey(getPrimaryKey(phoneId));
    }

    private static String selectForPrimaryKey(int key) {
        return String.format(Locale.US, "%s='%d'", "_id", key);
    }

    static String selectForSubId(int subId) {
        return String.format(Locale.US, "%s='%d'", COLUMN_SUB_ID, subId);
    }

    static void unregisterContentObserver(ContentResolver cr, ContentObserver obs) {
        try {
            cr.unregisterContentObserver(obs);
        } catch (Throwable t) {
            if (DBG) log(t.toString());
        }
    }
}
