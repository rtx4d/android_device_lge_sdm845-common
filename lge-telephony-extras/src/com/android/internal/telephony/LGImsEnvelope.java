// SPDX-FileCopyrightText: 2018 LG Electronics — original implementation
// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0

package com.android.internal.telephony;

import android.os.Parcel;
import android.os.Parcelable;

public final class LGImsEnvelope implements Parcelable {

    public static final int NOT_USED_TID = -1;

    public static final Parcelable.Creator<LGImsEnvelope> CREATOR =
            new Parcelable.Creator<LGImsEnvelope>() {
        @Override public LGImsEnvelope createFromParcel(Parcel p) {
            return new LGImsEnvelope(p);
        }
        @Override public LGImsEnvelope[] newArray(int size) {
            return new LGImsEnvelope[size];
        }
    };

    private String mData;
    private String mTag;
    private int mTid;

    public LGImsEnvelope(Parcel p) { readFromParcel(p); }

    public LGImsEnvelope(String tag, int tid, String data) {
        mTag = tag;
        mTid = tid;
        mData = data;
    }

    private void readFromParcel(Parcel p) {
        mTag = p.readString();
        mTid = p.readInt();
        mData = p.readString();
    }

    @Override public int describeContents() { return 0; }

    public String getData() { return mData; }
    public String getTag() { return mTag; }
    public int getTid() { return mTid; }

    @Override public String toString() {
        return "{ tag=" + mTag + ", tid=" + mTid + ", data=" + mData + " }";
    }

    @Override public void writeToParcel(Parcel p, int flags) {
        p.writeString(mTag);
        p.writeInt(mTid);
        p.writeString(mData);
    }
}
