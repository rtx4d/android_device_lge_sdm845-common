// SPDX-License-Identifier: Apache-2.0
//
// Compile-time stub of com.qualcomm.qcrilmsgtunnel.IQcrilMsgTunnel.
//
// Source of truth: decompiled/qcrilhook/sources/com/qualcomm/
//   qcrilmsgtunnel/IQcrilMsgTunnel.java (the full AIDL-generated stub
//   with Stub/Proxy plumbing). At runtime the consuming app's
//   <uses-library com.qualcomm.qcrilhook> declaration causes the
//   platform loader to use the real /system_ext/framework/qcrilhook.jar
//   classes instead of these stubs. We only need enough of the type
//   here for javac to compile the binder-client call site:
//
//     IQcrilMsgTunnel svc = IQcrilMsgTunnel.Stub.asInterface(binder);
//     svc.sendOemRilRequestRaw(req, resp, sub);
package com.qualcomm.qcrilmsgtunnel;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

public interface IQcrilMsgTunnel extends IInterface {
    int sendOemRilRequestRaw(byte[] request, byte[] response, int sub) throws RemoteException;

    void sendOemRilRequestRawAsync(byte[] request,
            com.qualcomm.qcrilhook.IOemHookCallback oemHookCb, int sub) throws RemoteException;

    abstract class Stub extends Binder implements IQcrilMsgTunnel {
        public static IQcrilMsgTunnel asInterface(IBinder obj) {
            throw new RuntimeException("Stub only");
        }

        @Override
        public IBinder asBinder() { return this; }
    }
}
