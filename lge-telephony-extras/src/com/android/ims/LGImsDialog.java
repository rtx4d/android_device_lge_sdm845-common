// SPDX-FileCopyrightText: 2018 LG Electronics — original implementation
// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
package com.android.ims;

import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.telephony.ims.ImsStreamMediaProfile;

import java.util.Locale;

public class LGImsDialog implements Parcelable {

    public static final int DIRECTION_INITIATOR = 1;
    public static final int DIRECTION_RECIPIENT = 2;

    public static final Parcelable.Creator<LGImsDialog> CREATOR =
            new Parcelable.Creator<LGImsDialog>() {
        @Override public LGImsDialog createFromParcel(Parcel p) {
            return new LGImsDialog(
                    p.readString(),
                    p.readInt(),
                    State.createState(p),
                    Participant.createParticipant(p),
                    Participant.createParticipant(p),
                    p.readInt() == 1,
                    (ImsStreamMediaProfile) p.readParcelable(null));
        }
        @Override public LGImsDialog[] newArray(int size) {
            return new LGImsDialog[size];
        }
    };

    public static class Participant {
        private final boolean mConference;
        private final String mDisplayName;
        private final Bundle mExtraParams;
        private final Uri mUri;

        public Participant(String displayName, Uri uri, boolean conference, Bundle extraParams) {
            mDisplayName = displayName;
            mUri = uri;
            mConference = conference;
            mExtraParams = extraParams;
        }

        public static Participant createParticipant(Parcel p) {
            ClassLoader cl = LGImsDialog.class.getClassLoader();
            return new Participant(
                    p.readString(),
                    (Uri) p.readParcelable(cl),
                    p.readInt() == 1,
                    (Bundle) p.readParcelable(cl));
        }

        public String getDisplayName() { return mDisplayName; }
        public Bundle getExtraParams() { return mExtraParams; }
        public Uri getUri() { return mUri; }
        public boolean isConference() { return mConference; }

        @Override public String toString() {
            return "[Participant: display=" + mDisplayName
                    + ", uri=" + mUri
                    + ", conference=" + (mConference ? "1" : "2") + "]";
        }

        public void writeToParcel(Parcel p, int flags) {
            p.writeString(mDisplayName);
            p.writeParcelable(mUri, 0);
            p.writeInt(mConference ? 1 : 0);
            p.writeParcelable(mExtraParams, 0);
        }
    }

    public static class State {
        public static final int EVENT_NONE = 0;
        public static final int EVENT_CANCELLED = 1;
        public static final int EVENT_REJECTED = 2;
        public static final int EVENT_REPLACED = 3;
        public static final int EVENT_LOCAL_BYE = 4;
        public static final int EVENT_REMOTE_BYE = 5;
        public static final int EVENT_ERROR = 6;
        public static final int EVENT_TIMEOUT = 7;
        public static final int STATE_IDLE = 0;
        public static final int STATE_TRYING = 1;
        public static final int STATE_PROCEEDING = 2;
        public static final int STATE_EARLY = 3;
        public static final int STATE_CONFIRMED = 4;
        public static final int STATE_TERMINATED = 5;
        public static final int STATE_ON_HOLD = 6;

        private final int mCode;
        private final int mEvent;
        private final int mState;

        public State(int state, int event, int code) {
            mState = state;
            mEvent = event;
            mCode = code;
        }

        public static State createState(Parcel p) {
            return new State(p.readInt(), p.readInt(), p.readInt());
        }

        public int getCode() { return mCode; }
        public int getEvent() { return mEvent; }
        public int getState() { return mState; }

        @Override public String toString() {
            return String.format(Locale.US,
                    "[State: state=%d, event=%d, code=%d]", mState, mEvent, mCode);
        }

        public void writeToParcel(Parcel p, int flags) {
            p.writeInt(mState);
            p.writeInt(mEvent);
            p.writeInt(mCode);
        }
    }

    private final int mDirection;
    private final String mId;
    private final Participant mLocal;
    private final ImsStreamMediaProfile mMediaProfile;
    private final boolean mPullEnabled;
    private final Participant mRemote;
    private final State mState;

    public LGImsDialog(String id, int direction, State state, Participant local,
                       Participant remote, boolean pullEnabled,
                       ImsStreamMediaProfile mediaProfile) {
        mId = id;
        mDirection = direction;
        mState = state;
        mLocal = local;
        mRemote = remote;
        mPullEnabled = pullEnabled;
        mMediaProfile = mediaProfile;
    }

    @Override public int describeContents() { return 0; }

    public int getDirection() { return mDirection; }
    public String getId() { return mId; }
    public Participant getLocal() { return mLocal; }
    public ImsStreamMediaProfile getMediaProfile() { return mMediaProfile; }
    public Participant getRemote() { return mRemote; }
    public State getState() { return mState; }
    public boolean isPullEnabled() { return mPullEnabled; }

    public boolean isConference() {
        return (mLocal != null && mLocal.isConference())
                || (mRemote != null && mRemote.isConference());
    }

    @Override public String toString() {
        return "{ Dialog: id=" + mId
                + ", direction=" + mDirection
                + ", " + mState
                + ", Local " + mLocal
                + ", Remote " + mRemote
                + ", pullEnabled=" + (mPullEnabled ? "1" : "2")
                + ", mediaProfile=" + mMediaProfile + " }";
    }

    @Override public void writeToParcel(Parcel p, int flags) {
        p.writeString(mId);
        p.writeInt(mDirection);
        mState.writeToParcel(p, flags);
        mLocal.writeToParcel(p, flags);
        mRemote.writeToParcel(p, flags);
        p.writeInt(mPullEnabled ? 1 : 0);
        p.writeParcelable(mMediaProfile, 0);
    }
}
