// SPDX-FileCopyrightText: 2018 LG Electronics — original implementation
// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
//
// LG video-call media-quality debug info. Used by LGImsVideoCallProvider
// to surface bitrate/framerate/quality stats from the IMS media stack to
// InCallUI. Stub-grade port: fields and Parcelable wire format match the
// stock LG framework class so binder transactions on either side accept
// it. Field names retained verbatim.

package com.android.ims;

import android.os.Parcel;
import android.os.Parcelable;

public class LGImsCallMediaDebugInfo implements Parcelable {
    public int mTxBitrate;
    public int mRxBitrate;
    public int mTxFramerate;
    public int mRxFramerate;
    public int mVideoQualityInd;

    public LGImsCallMediaDebugInfo() { }

    public LGImsCallMediaDebugInfo(Parcel parcel) {
        readFromParcel(parcel);
    }

    private void readFromParcel(Parcel parcel) {
        mTxBitrate = parcel.readInt();
        mRxBitrate = parcel.readInt();
        mTxFramerate = parcel.readInt();
        mRxFramerate = parcel.readInt();
        mVideoQualityInd = parcel.readInt();
    }

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mTxBitrate);
        parcel.writeInt(mRxBitrate);
        parcel.writeInt(mTxFramerate);
        parcel.writeInt(mRxFramerate);
        parcel.writeInt(mVideoQualityInd);
    }

    @Override
    public String toString() {
        return super.toString()
                + ", Bitrate(Tx)=" + mTxBitrate
                + ", Bitrate(Rx)=" + mRxBitrate
                + ", Framerate(Tx)=" + mTxFramerate
                + ", Framerate(Rx)=" + mRxFramerate
                + ", VideoQualityIND=" + mVideoQualityInd;
    }

    public static final Parcelable.Creator<LGImsCallMediaDebugInfo> CREATOR =
            new Parcelable.Creator<LGImsCallMediaDebugInfo>() {
        @Override
        public LGImsCallMediaDebugInfo createFromParcel(Parcel parcel) {
            return new LGImsCallMediaDebugInfo(parcel);
        }

        @Override
        public LGImsCallMediaDebugInfo[] newArray(int size) {
            return new LGImsCallMediaDebugInfo[size];
        }
    };
}
