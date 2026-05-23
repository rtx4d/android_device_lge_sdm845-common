// SPDX-FileCopyrightText: 2018 LG Electronics — original implementation
// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
//
// Restored from decompiled stock G910EMW30l framework.jar.

package com.lge.lgdata;

import java.util.HashMap;

public class LGDataPhoneConstants {
    public static final String ACTION_VOLTE_EMERGENCY_CALL_FAIL_CAUSE = "lge.intent.action.DATA_EMERGENCY_FAILED";
    public static final String ACTION_VOLTE_EPS_NETWORK_SUPPORT = "lge.intent.action.LTE_NETWORK_SUPPORTED_INFO";
    public static final String ACTION_VOLTE_LTE_STATE_INFO = "lge.intent.action.LTE_STATE_INFO";
    public static final String ACTION_VOLTE_NETWORK_SIB_INFO = "lge.intent.action.LTE_NETWORK_SIB_INFO";
    public static final String ACTION_VOLTE_ROAMING_IMS_SETUP_FAIL = "lge.intent.action.DATA_VOLTE_ROAMING_IMS_SETUP_FAILED";
    public static final int BLOCK_BAD_SIGNAL = 2;
    public static final int BLOCK_CONNECTION_FAIL = 32;
    public static final int BLOCK_IMS_REGFAIL = 1;
    public static final int BLOCK_IMS_RTP_FAIL = 4;
    public static final int BLOCK_IPSEC_FAIL = 8;
    public static final int BLOCK_ROAMING = 16;
    public static final int BLOCK_T3402 = 64;
    public static final String DATA_SMCAUSE = "smCause";
    public static final String REASON_CLEANUP_METERED_APN = "cleanUpMeteredApn";
    public static final String sEMC_FailCause = "EMC_FailCause";
    public static final String sEPDN_Barring = "EPDN_Barring";
    public static final String sEPDN_Support = "EPDN_Support";
    public static final String sEmer_Attach_Support = "Emer_Attach_Support";
    public static final String sEmer_Camped_CID = "Emer_Camped_CID";
    public static final String sEmer_Camped_TAC = "Emer_Camped_TAC";
    public static final String sLteDetachCause = "LteDetachCause";
    public static final String sLteStateInfo = "LteStateInfo";
    public static final String sLteUpdateResult = "LteUpdateResult";
    public static final String sVoPS_Support = "VoPS_Support";

    public enum EmcFailCause {
        NONE(0), PDN_FAILED(1), ATTACH_FAILED(2), BARRED(3);

        private static final HashMap<Integer, EmcFailCause> MAP = new HashMap<>();
        private final int mCode;

        static {
            for (EmcFailCause v : values()) MAP.put(v.getCode(), v);
        }

        EmcFailCause(int code) { mCode = code; }
        public int getCode() { return mCode; }

        public static EmcFailCause fromInt(int i) {
            EmcFailCause v = MAP.get(i);
            return v == null ? NONE : v;
        }
    }

    public enum LteStateInfo {
        NONE(0),
        NORMAL_DETACHED(1),
        EMERGENCY_ATTACHED(2),
        NORMAL_ATTACHED(3),
        EMERGENCY_DETACHED(4),
        EPS_ONLY_ATTACHED(5),
        REATTACH_REQUIRED(11),
        REATTACH_NOT_REQUIURED(12),
        IMSI_DETACH_MT_DETACH(13),
        RESERVED_MT_DETACH_TYPE_ONE(16),
        RESERVED_MT_DETACH_TYPE_TWO(17);

        private static final HashMap<Integer, LteStateInfo> MAP = new HashMap<>();
        private final int mCode;

        static {
            for (LteStateInfo v : values()) MAP.put(v.getCode(), v);
        }

        LteStateInfo(int code) { mCode = code; }
        public int getCode() { return mCode; }

        public static LteStateInfo fromInt(int i) {
            LteStateInfo v = MAP.get(i);
            return v == null ? NONE : v;
        }
    }

    public enum SIBInfoForEPDN {
        NONE(0),
        EMER_ATTACH_NOT_SUPPORT(1),
        EMER_ATTACH_SUPPORT(2),
        EPDN_NOT_BARRED(3),
        EPDN_BARRED(4);

        private static final HashMap<Integer, SIBInfoForEPDN> MAP = new HashMap<>();
        private final int mCode;

        static {
            for (SIBInfoForEPDN v : values()) MAP.put(v.getCode(), v);
        }

        SIBInfoForEPDN(int code) { mCode = code; }
        public int getCode() { return mCode; }

        public static SIBInfoForEPDN fromInt(int i) {
            SIBInfoForEPDN v = MAP.get(i);
            return v == null ? NONE : v;
        }
    }

    public enum VolteAndEPDNSupport {
        NONE(0),
        VOLTE_NOT_SUPPORT(1),
        VOLTE_SUPPORT(2),
        EPDN_NOT_SUPPORT(3),
        EPDN_SUPPORT(4);

        private static final HashMap<Integer, VolteAndEPDNSupport> MAP = new HashMap<>();
        private final int mCode;

        static {
            for (VolteAndEPDNSupport v : values()) MAP.put(v.getCode(), v);
        }

        VolteAndEPDNSupport(int code) { mCode = code; }
        public int getCode() { return mCode; }

        public static VolteAndEPDNSupport fromInt(int i) {
            VolteAndEPDNSupport v = MAP.get(i);
            return v == null ? NONE : v;
        }
    }
}
