// SPDX-FileCopyrightText: 2018 LG Electronics — original implementation
// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
//
// Restored from decompiled stock G910EMW30l telephony-common.jar.

package com.android.internal.telephony;

import android.os.Parcel;
import android.os.Parcelable;

public final class LGImsHVoLtePreference implements Parcelable {
    public static final Parcelable.Creator<LGImsHVoLtePreference> CREATOR =
            new Parcelable.Creator<LGImsHVoLtePreference>() {
        @Override public LGImsHVoLtePreference createFromParcel(Parcel p) {
            return new LGImsHVoLtePreference(p);
        }
        @Override public LGImsHVoLtePreference[] newArray(int size) {
            return new LGImsHVoLtePreference[size];
        }
    };

    private int mPreferredServices;
    private int mSysMode;

    public LGImsHVoLtePreference() { init(); }
    public LGImsHVoLtePreference(Parcel p) { readFromParcel(p); }

    private void readFromParcel(Parcel p) {
        mSysMode = p.readInt();
        mPreferredServices = p.readInt();
    }

    public void copyFrom(LGImsHVoLtePreference o) {
        if (o != null) {
            mSysMode = o.mSysMode;
            mPreferredServices = o.mPreferredServices;
        }
    }

    @Override public int describeContents() { return 0; }

    public int getPreferredServices() { return mPreferredServices; }
    public int getSysMode() { return mSysMode; }

    public void init() {
        mSysMode = 0;
        mPreferredServices = 0;
    }

    void setPreferredServices(int v) { mPreferredServices = v; }
    void setSysMode(int v) { mSysMode = v; }

    @Override public String toString() {
        return "{ sysMode=" + mSysMode
                + ", preferredServices=0x" + Integer.toHexString(mPreferredServices) + " }";
    }

    @Override public void writeToParcel(Parcel p, int flags) {
        p.writeInt(mSysMode);
        p.writeInt(mPreferredServices);
    }
}
