// SPDX-FileCopyrightText: 2018 LG Electronics — original implementation
// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
package com.android.ims;

import android.os.Parcel;
import android.os.Parcelable;

public class LGImsDevice implements Parcelable {

    public static final int TYPE_APPLIANCE = 7;
    public static final int TYPE_AUTOMOTIVE = 8;
    public static final int TYPE_DESKTOP = 6;
    public static final int TYPE_LAPTOP = 5;
    public static final int TYPE_MOBILE_PHONE = 1;
    public static final int TYPE_SET_OF_BOX = 4;
    public static final int TYPE_TABLET = 2;
    public static final int TYPE_UNSPECIFIED = 0;
    public static final int TYPE_WEARABLE = 3;

    public static final Parcelable.Creator<LGImsDevice> CREATOR =
            new Parcelable.Creator<LGImsDevice>() {
        @Override public LGImsDevice createFromParcel(Parcel p) {
            return new LGImsDevice(p.readString(), p.readString(), p.readInt());
        }
        @Override public LGImsDevice[] newArray(int size) {
            return new LGImsDevice[size];
        }
    };

    private final String mId;
    private final String mName;
    private final int mType;

    public LGImsDevice(String id, String name) {
        this(id, name, TYPE_UNSPECIFIED);
    }

    public LGImsDevice(String id, String name, int type) {
        mId = id;
        mName = name;
        mType = type;
    }

    @Override public int describeContents() { return 0; }

    public String getId() { return mId; }
    public String getName() { return mName; }
    public int getType() { return mType; }

    @Override public String toString() {
        return "{ Device: id=" + mId + ", name=" + mName + ", type=" + mType + " }";
    }

    @Override public void writeToParcel(Parcel p, int flags) {
        p.writeString(mId);
        p.writeString(mName);
        p.writeInt(mType);
    }
}
