// SPDX-License-Identifier: Apache-2.0
//
// Compile-time stub of com.android.lge.lgsvcitems.LgSvcCmd, modelled on
// the decompiled stock LG lgsvcitems.jar (system_ext/framework/). At
// runtime, the <uses-library com.android.lge.lgsvcitems> declaration
// in the consuming app's manifest causes the platform's library loader
// to resolve these symbols against the real on-device jar instead of
// this stub. The stub bodies therefore never run; they exist only to
// give javac something with the right signatures to compile against.
//
// We cannot consume the real lgsvcitems jar at compile time because it
// is a dex_import (classes.dex only, no .class files), and javac needs
// .class files. The runtime <uses-library> mechanism handles the rest.
//
// Source of truth: decompiled/lgsvcitems/sources/com/android/lge/
//   lgsvcitems/LgSvcCmd.java + LgSvcItems.java + ILgSvcItems.java.
package com.android.lge.lgsvcitems;

import android.content.Context;

import java.io.IOException;

public class LgSvcCmd {
    public static synchronized LgSvcCmd getInstance(Context context) {
        throw new RuntimeException("Stub only");
    }

    public boolean getIQcrilMsgTunnelServiceStatus() {
        throw new RuntimeException("Stub only");
    }

    public String getCmdValue(int itemID) throws IOException {
        throw new RuntimeException("Stub only");
    }

    public int setCmdValue(int itemID, String value) {
        throw new RuntimeException("Stub only");
    }
}
