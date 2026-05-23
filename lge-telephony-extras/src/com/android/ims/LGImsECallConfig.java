// SPDX-FileCopyrightText: 2018 LG Electronics — original implementation
// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
package com.android.ims;

import android.os.Parcel;
import android.os.Parcelable;

public class LGImsECallConfig implements Parcelable {

    public static final int IPCAN_ALL = 3;
    public static final int IPCAN_LTE = 1;
    public static final int IPCAN_NONE = 0;
    public static final int IPCAN_WIFI = 2;

    public static final Parcelable.Creator<LGImsECallConfig> CREATOR =
            new Parcelable.Creator<LGImsECallConfig>() {
        @Override public LGImsECallConfig createFromParcel(Parcel p) {
            return new LGImsECallConfig(
                    p.readInt(),
                    p.readInt() != 0,
                    p.readInt() != 0,
                    p.readInt() != 0);
        }
        @Override public LGImsECallConfig[] newArray(int size) {
            return new LGImsECallConfig[size];
        }
    };

    private final boolean mControlledByVoLteReg;
    private final boolean mControlledByVoLteSetting;
    private final boolean mNormalCallEndRequired;
    private final int mSupportedIPCAN;

    public LGImsECallConfig(int supportedIPCAN, boolean controlledByVoLteSetting,
                            boolean controlledByVoLteReg, boolean normalCallEndRequired) {
        mSupportedIPCAN = supportedIPCAN;
        mControlledByVoLteSetting = controlledByVoLteSetting;
        mControlledByVoLteReg = controlledByVoLteReg;
        mNormalCallEndRequired = normalCallEndRequired;
    }

    @Override public int describeContents() { return 0; }

    public int getIpcanForECall() { return mSupportedIPCAN; }
    public boolean isECallControlledByVoLteReg() { return mControlledByVoLteReg; }
    public boolean isECallControlledByVoLteSetting() { return mControlledByVoLteSetting; }
    public boolean isECallSupportedInLte() { return (mSupportedIPCAN & IPCAN_LTE) != 0; }
    public boolean isECallSupportedInWifi() { return (mSupportedIPCAN & IPCAN_WIFI) != 0; }
    public boolean isImsCallEndRequiredForECall() { return mNormalCallEndRequired; }

    @Override public String toString() {
        return "{ ImsECallConfig: supportedIPCAN=" + mSupportedIPCAN
                + ", controlledByVoLteSetting=" + mControlledByVoLteSetting
                + ", controlledByVoLteReg=" + mControlledByVoLteReg
                + ", normalCallEndRequired=" + mNormalCallEndRequired + " }";
    }

    @Override public void writeToParcel(Parcel p, int flags) {
        p.writeInt(mSupportedIPCAN);
        p.writeInt(mControlledByVoLteSetting ? 1 : 0);
        p.writeInt(mControlledByVoLteReg ? 1 : 0);
        p.writeInt(mNormalCallEndRequired ? 1 : 0);
    }
}
