// SPDX-FileCopyrightText: 2018 LG Electronics — original implementation
// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
//
// LG-specific AR / video-call-effect info ferried between InCallUI and
// Ims6 through LGImsVideoCallProvider. Stock implementation also carries
// a `StickerInformationDataClass` Parcelable; we omit it because nothing
// in the VoLTE registration / voice-call path reads sticker data — it's
// strictly an UX feature for ViLTE AR overlay. Wire format keeps the
// same int triplet (key/action/value) so a parcel from stock
// firmware would still readInt() correctly. The parcelable slot for the
// sticker is read/written as a plain Parcelable<?>, which serializes a
// null marker if the field isn't set. If sticker AR ever needs to work,
// add the StickerInformationDataClass class and import it here.

package com.android.ims;

import android.os.Parcel;
import android.os.Parcelable;

public class LGImsVideoCallEffectInfo implements Parcelable {
    public static final int VIDEO_CALL_EFFECT_KEY_START_AR = 0;
    public static final int VIDEO_CALL_EFFECT_KEY_STOP_AR = 1;
    public static final int VIDEO_CALL_EFFECT_KEY_SELECT_ITEM = 2;
    public static final int VIDEO_CALL_EFFECT_KEY_BEAUTY = 3;
    public static final int VIDEO_CALL_EFFECT_KEY_INFO = 4;

    public static final int MOTION_OPEN_MOUTH = 0;
    public static final int MOTION_EYE_BLINK = 1;
    public static final int MOTION_EYE_BROW_RAISE = 2;
    public static final int MOTION_HEAD_NOD = 3;
    public static final int MOTION_HEAD_SHAKE = 4;

    public static final int INFO_FACE_COUNT_CHANGED = 100;
    public static final int INFO_FACE_ACTION_DONE = 101;
    public static final int INFO_STICKER_CHANGE = 102;
    public static final int INFO_SET_FACE_COUNT = 103;
    public static final int INFO_STICKER_CHANGE_COMPLETED = 104;
    public static final int INFO_HUMAN_AVATAR_INIT_FAILED = 105;
    public static final int INFO_MEDIA_RECORDER_MAX_FILE_SIZE_REACHED = 106;

    private int mKey;
    private int mAction;
    private int mValue;
    // mStickerInfo dropped — see class doc.

    public LGImsVideoCallEffectInfo() { }

    public LGImsVideoCallEffectInfo(int key) {
        mKey = key;
    }

    public LGImsVideoCallEffectInfo(Parcel parcel) {
        readFromParcel(parcel);
    }

    private void readFromParcel(Parcel parcel) {
        mKey = parcel.readInt();
        mAction = parcel.readInt();
        mValue = parcel.readInt();
        // stock writes a sticker Parcelable here; if this parcel came from
        // stock firmware we still need to consume that slot or the rest of
        // the stream will misalign. readParcelable returns null when the
        // class isn't on the loader, and consumes the marker either way.
        parcel.readParcelable(getClass().getClassLoader());
    }

    public int getKey() { return mKey; }
    public int getAction() { return mAction; }
    public int getValue() { return mValue; }
    public void setAction(int v) { mAction = v; }
    public void setValue(int v) { mValue = v; }

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mKey);
        parcel.writeInt(mAction);
        parcel.writeInt(mValue);
        // null Parcelable marker — stock readParcelable accepts this
        parcel.writeParcelable(null, flags);
    }

    public static final Parcelable.Creator<LGImsVideoCallEffectInfo> CREATOR =
            new Parcelable.Creator<LGImsVideoCallEffectInfo>() {
        @Override
        public LGImsVideoCallEffectInfo createFromParcel(Parcel parcel) {
            return new LGImsVideoCallEffectInfo(parcel);
        }

        @Override
        public LGImsVideoCallEffectInfo[] newArray(int size) {
            return new LGImsVideoCallEffectInfo[size];
        }
    };
}
