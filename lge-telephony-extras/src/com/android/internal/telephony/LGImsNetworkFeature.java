// SPDX-FileCopyrightText: 2018 LG Electronics — original implementation
// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
//
// Stock helpers referenced LG-only constants packages; inlined here.

package com.android.internal.telephony;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

public final class LGImsNetworkFeature implements Parcelable {

    public static final int DISABLED = 0;
    public static final int ENABLED = 1;
    public static final int FEATURE_AC_BARRING_FOR_EMERGENCY = 2;
    public static final int FEATURE_ALL = 1795;
    public static final int FEATURE_EMC_BS = 256;
    public static final int FEATURE_IMS_EMERGENCY_SUPPORT = 1;
    public static final int FEATURE_NONE = 0;
    public static final int FEATURE_PLMN1 = 4;
    public static final int FEATURE_PLMN2 = 8;
    public static final int FEATURE_VOPS = 512;
    public static final int FEATURE_VOPS_ON_3G = 1024;

    private static final String NULL_VALUE = "null";

    public static final Parcelable.Creator<LGImsNetworkFeature> CREATOR =
            new Parcelable.Creator<LGImsNetworkFeature>() {
        @Override public LGImsNetworkFeature createFromParcel(Parcel p) {
            return new LGImsNetworkFeature(p);
        }
        @Override public LGImsNetworkFeature[] newArray(int size) {
            return new LGImsNetworkFeature[size];
        }
    };

    private int mAcBarringForEmergency;
    private int mEmcBs;
    private int mImsEmergencySupport;
    private String mPlmn1;
    private String mPlmn2;
    private int mVoPS;
    private int mVoPSOn3G;

    public LGImsNetworkFeature() { init(); }
    public LGImsNetworkFeature(Parcel p) { readFromParcel(p); }

    public static boolean hasAcBarringForEmergency(int v) { return (v & 2) != 0; }
    public static boolean hasEmcBs(int v) { return (v & 256) != 0; }
    public static boolean hasImsEmergencySupport(int v) { return (v & 1) != 0; }
    public static boolean hasVoPS(int v) { return (v & 512) != 0; }
    public static boolean hasVoPSOn3G(int v) { return (v & 1024) != 0; }

    static boolean isRilValueValid(String s) {
        return s != null && !NULL_VALUE.equalsIgnoreCase(s);
    }

    static int rilBoolToInt(String s) {
        return "1".equals(s) ? 1 : 0;
    }

    private void readFromParcel(Parcel p) {
        mImsEmergencySupport = p.readInt();
        mAcBarringForEmergency = p.readInt();
        mPlmn1 = p.readString();
        mPlmn2 = p.readString();
        mEmcBs = p.readInt();
        mVoPS = p.readInt();
        mVoPSOn3G = p.readInt();
    }

    public void copyFrom(LGImsNetworkFeature o) {
        if (o != null) {
            mImsEmergencySupport = o.mImsEmergencySupport;
            mAcBarringForEmergency = o.mAcBarringForEmergency;
            mPlmn1 = o.mPlmn1;
            mPlmn2 = o.mPlmn2;
            mEmcBs = o.mEmcBs;
            mVoPS = o.mVoPS;
            mVoPSOn3G = o.mVoPSOn3G;
        }
    }

    @Override public int describeContents() { return 0; }

    public int getAcBarringForEmergency() { return mAcBarringForEmergency; }
    public int getEmcBs() { return mEmcBs; }
    public int getImsEmergencySupport() { return mImsEmergencySupport; }
    public String getPlmn1() { return mPlmn1; }
    public String getPlmn2() { return mPlmn2; }
    public int getVoPS() { return mVoPS; }
    public int getVoPSOn3G() { return mVoPSOn3G; }

    public void init() {
        mImsEmergencySupport = 0;
        mAcBarringForEmergency = 0;
        mPlmn1 = "";
        mPlmn2 = "";
        mEmcBs = 0;
        mVoPS = 0;
        mVoPSOn3G = 0;
    }

    int setAcBarringForEmergency(int v) {
        if (mAcBarringForEmergency == v) return 0;
        mAcBarringForEmergency = v; return 2;
    }

    int setEmcBs(int v) {
        if (mEmcBs == v) return 0;
        mEmcBs = v; return 256;
    }

    int setImsEmergencySupport(int v) {
        if (mImsEmergencySupport == v) return 0;
        mImsEmergencySupport = v; return 1;
    }

    int setPlmn1(String v) {
        if (Objects.equals(mPlmn1, v)) return 0;
        mPlmn1 = v; return 4;
    }

    int setPlmn2(String v) {
        if (Objects.equals(mPlmn2, v)) return 0;
        mPlmn2 = v; return 8;
    }

    int setVoPS(int v) {
        if (mVoPS == v) return 0;
        mVoPS = v; return 512;
    }

    int setVoPSOn3G(int v) {
        if (mVoPSOn3G == v) return 0;
        mVoPSOn3G = v; return 1024;
    }

    @Override public String toString() {
        return "{ imsEmergencySupport=" + mImsEmergencySupport
                + ", acBarringForEmergency=" + mAcBarringForEmergency
                + ", PLMN1=" + mPlmn1
                + ", PLMN2=" + mPlmn2
                + ", emcBs=" + mEmcBs
                + ", voPS=" + mVoPS
                + ", voPSOn3G=" + mVoPSOn3G + " }";
    }

    @Override public void writeToParcel(Parcel p, int flags) {
        p.writeInt(mImsEmergencySupport);
        p.writeInt(mAcBarringForEmergency);
        p.writeString(mPlmn1 == null ? "" : mPlmn1);
        p.writeString(mPlmn2 == null ? "" : mPlmn2);
        p.writeInt(mEmcBs);
        p.writeInt(mVoPS);
        p.writeInt(mVoPSOn3G);
    }
}
