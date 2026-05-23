// SPDX-FileCopyrightText: 2018 LG Electronics — original implementation
// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
package com.android.ims;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

public class LGImsDeviceInfo implements Parcelable {

    public static final Parcelable.Creator<LGImsDeviceInfo> CREATOR =
            new Parcelable.Creator<LGImsDeviceInfo>() {
        @Override public LGImsDeviceInfo createFromParcel(Parcel p) {
            return new LGImsDeviceInfo(p);
        }
        @Override public LGImsDeviceInfo[] newArray(int size) {
            return new LGImsDeviceInfo[size];
        }
    };

    private final List<LGImsDevice> mDevices;

    public LGImsDeviceInfo(Parcel p) {
        mDevices = new ArrayList<>();
        ClassLoader cl = LGImsDeviceInfo.class.getClassLoader();
        int n = p.readInt();
        for (int i = 0; i < n; i++) {
            mDevices.add(p.readParcelable(cl));
        }
    }

    public LGImsDeviceInfo(List<LGImsDevice> devices) {
        mDevices = devices;
    }

    @Override public int describeContents() { return 0; }

    public List<LGImsDevice> getDevices() { return mDevices; }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[ ImsDeviceInfo: ").append(mDevices.size());
        if (!mDevices.isEmpty()) {
            sb.append(", [ ").append(mDevices.get(0));
            for (int i = 1; i < mDevices.size(); i++) {
                sb.append(", ").append(mDevices.get(i));
            }
            sb.append(" ]");
        }
        sb.append(" ]");
        return sb.toString();
    }

    @Override public void writeToParcel(Parcel p, int flags) {
        p.writeInt(mDevices.size());
        for (LGImsDevice d : mDevices) {
            p.writeParcelable(d, 0);
        }
    }
}
