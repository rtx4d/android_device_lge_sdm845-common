// SPDX-License-Identifier: Apache-2.0
//
// Compile-time stub of com.qualcomm.qcrilhook.IOemHookCallback —
// the AIDL callback used by IQcrilMsgTunnel.sendOemRilRequestRawAsync.
// We don't use the async path; the stub exists only to satisfy the
// signature reference in IQcrilMsgTunnel.
package com.qualcomm.qcrilhook;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

public interface IOemHookCallback extends IInterface {
    abstract class Stub extends Binder implements IOemHookCallback {
        public static IOemHookCallback asInterface(IBinder obj) {
            throw new RuntimeException("Stub only");
        }

        @Override
        public IBinder asBinder() { return this; }
    }
}
