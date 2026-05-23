// SPDX-FileCopyrightText: 2018 LG Electronics — original implementation
// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
//
// Restored from decompiled stock G910EMW30l telephony-common.jar.
// Stock helpers `isRilValueValid()` / `rilModeToString()` referenced
// `com.android.internal.telephony.gsm.LgeNetworkNameConstants` and
// `com.android.internal.telephony.lgradio.LgGpriAmrwbParser` (both LG-only).
// They're inlined here so we don't drag in those packages — the only
// callers were inside this class anyway.

package com.android.internal.telephony;

import android.os.Parcel;
import android.os.Parcelable;

public final class LGImsCellInfo implements Parcelable {

    public static final String MODE_FDD = "FDD";
    public static final String MODE_TDD = "TDD";
    private static final String NULL_VALUE = "null";

    public static final Parcelable.Creator<LGImsCellInfo> CREATOR =
            new Parcelable.Creator<LGImsCellInfo>() {
        @Override public LGImsCellInfo createFromParcel(Parcel p) {
            return new LGImsCellInfo(p);
        }
        @Override public LGImsCellInfo[] newArray(int size) {
            return new LGImsCellInfo[size];
        }
    };

    private String mCellIdentity;
    private String mMcc;
    private String mMnc;
    private String mMode;
    private int mNetworkType;
    private String mTac;

    public LGImsCellInfo(int networkType) {
        mNetworkType = networkType;
        init();
    }

    public LGImsCellInfo(Parcel p) { readFromParcel(p); }

    static boolean isRilValueValid(String s) {
        return s != null && !NULL_VALUE.equalsIgnoreCase(s);
    }

    static String rilModeToString(String s) {
        if ("1".equals(s)) return MODE_FDD;
        if ("2".equals(s)) return MODE_TDD;
        return "";
    }

    private void readFromParcel(Parcel p) {
        mNetworkType = p.readInt();
        mMcc = p.readString();
        mMnc = p.readString();
        mCellIdentity = p.readString();
        mTac = p.readString();
        mMode = p.readString();
    }

    public void copyFrom(LGImsCellInfo o) {
        if (o != null) {
            mNetworkType = o.mNetworkType;
            mMcc = o.mMcc;
            mMnc = o.mMnc;
            mCellIdentity = o.mCellIdentity;
            mTac = o.mTac;
            mMode = o.mMode;
        }
    }

    @Override public int describeContents() { return 0; }

    public String getCellIdentity() { return mCellIdentity; }
    public String getMcc() { return mMcc; }
    public String getMnc() { return mMnc; }
    public String getMode() { return mMode; }
    public int getNetworkType() { return mNetworkType; }
    public String getSectorId() { return mCellIdentity; }
    public String getSubnetLength() { return mTac; }
    public String getTac() { return mTac; }

    public void init() {
        mMcc = null;
        mMnc = null;
        mCellIdentity = null;
        mTac = null;
        mMode = null;
    }

    void setCellIdentity(String v) { mCellIdentity = v; }
    void setMcc(String v) { mMcc = v; }
    void setMnc(String v) { mMnc = v; }
    void setMode(String v) { mMode = v; }
    void setNetworkType(int v) { mNetworkType = v; }
    void setSectorId(String v) { mCellIdentity = v; }
    void setSubnetLength(String v) { mTac = v; }
    void setTac(String v) { mTac = v; }

    public String[] toArray() {
        return mNetworkType == 14
                ? new String[] { getSectorId(), getSubnetLength() }
                : new String[] { getMcc(), getMnc(), getCellIdentity(), getTac(), getMode() };
    }

    @Override public String toString() {
        return "{ networkType=" + mNetworkType
                + ", mcc=" + mMcc
                + ", mnc=" + mMnc
                + ", cellIdentity=" + mCellIdentity
                + ", tac=" + mTac
                + ", mode=" + mMode + " }";
    }

    @Override public void writeToParcel(Parcel p, int flags) {
        p.writeInt(mNetworkType);
        p.writeString(mMcc);
        p.writeString(mMnc);
        p.writeString(mCellIdentity);
        p.writeString(mTac);
        p.writeString(mMode);
    }
}
