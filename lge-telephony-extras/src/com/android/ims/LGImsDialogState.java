// SPDX-FileCopyrightText: 2018 LG Electronics — original implementation
// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
package com.android.ims;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

public class LGImsDialogState implements Parcelable {

    public static final Parcelable.Creator<LGImsDialogState> CREATOR =
            new Parcelable.Creator<LGImsDialogState>() {
        @Override public LGImsDialogState createFromParcel(Parcel p) {
            return new LGImsDialogState(p);
        }
        @Override public LGImsDialogState[] newArray(int size) {
            return new LGImsDialogState[size];
        }
    };

    private final List<LGImsDialog> mDialogs;

    public LGImsDialogState(Parcel p) {
        mDialogs = new ArrayList<>();
        ClassLoader cl = LGImsDialogState.class.getClassLoader();
        int n = p.readInt();
        for (int i = 0; i < n; i++) {
            mDialogs.add(p.readParcelable(cl));
        }
    }

    public LGImsDialogState(List<LGImsDialog> dialogs) {
        mDialogs = dialogs;
    }

    @Override public int describeContents() { return 0; }

    public List<LGImsDialog> getDialogs() { return mDialogs; }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[ ImsDialogState: ").append(mDialogs.size());
        if (!mDialogs.isEmpty()) {
            sb.append(", [ ").append(mDialogs.get(0));
            for (int i = 1; i < mDialogs.size(); i++) {
                sb.append(", ").append(mDialogs.get(i));
            }
            sb.append(" ]");
        }
        sb.append(" ]");
        return sb.toString();
    }

    @Override public void writeToParcel(Parcel p, int flags) {
        p.writeInt(mDialogs.size());
        for (LGImsDialog d : mDialogs) {
            p.writeParcelable(d, 0);
        }
    }
}
