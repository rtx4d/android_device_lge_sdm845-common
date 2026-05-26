// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
//
// Helper used from a smali patch on Ims6.apk
// (ImsCallSessionImpl$UCSessionListenerProxy.onSessionStarted) to fix
// the caller-ID-disappears-on-answer bug.
//
// Background: Ims6 builds the answered-call ImsCallProfile via
// `ImsCallUtils.createCallProfileFromCallInfo(ctx, sessInfo, mediaInfo)`,
// which fills only media/session extras. It has no access to the
// caller-id extras (oi/cna/oir/cnap) which were populated earlier in
// `createCallProfileFromIncomingCallInfo` and stored on the session's
// `mCallProfile`. When this fresh-but-incomplete profile is delivered
// to AOSP via `callSessionInitiated`, AOSP's
// `ImsPhoneConnection.updateAddressDisplay` reads `EXTRA_OI=null`,
// runs `if (!equalsBaseDialString(mAddress, address)) { mAddress = address; }`
// — which OVERWRITES the previously-set mAddress with null. From that
// moment Telecom's `Connection.handle` is null, the dialer shows
// "Unknown", and the call log records no number.
//
// The smali patch inserts a single `invoke-static` to this method
// between `createCallProfileFromCallInfo` and `invokeStarted`, copying
// the four caller-id extras from the session's stable mCallProfile
// onto the new started-profile. That keeps AOSP from clobbering
// mAddress.

package com.lge.extras;

import android.telephony.ims.ImsCallProfile;

public final class LGImsCallProfileUtils {
    // Stock LG `Session.EXTRA_*` constants. We don't depend on the
    // original Session class here so this jar stays self-contained.
    private static final String EXTRA_OI = "oi";
    private static final String EXTRA_CNA = "cna";
    private static final String EXTRA_OIR = "oir";
    private static final String EXTRA_CNAP = "cnap";

    private LGImsCallProfileUtils() { }

    /**
     * Copy the caller-id extras (oi, cna, oir, cnap) from {@code src}
     * onto {@code dst}, skipping keys that aren't present on src.
     *
     * Both args may be null (no-op).
     */
    public static void preserveCallerIdExtras(ImsCallProfile src, ImsCallProfile dst) {
        if (src == null || dst == null) {
            return;
        }
        String oi = src.getCallExtra(EXTRA_OI);
        if (oi != null) {
            dst.setCallExtra(EXTRA_OI, oi);
        }
        String cna = src.getCallExtra(EXTRA_CNA);
        if (cna != null) {
            dst.setCallExtra(EXTRA_CNA, cna);
        }
        // For int extras we use a sentinel default (Integer.MIN_VALUE)
        // to detect "not set". Stock LG uses 0..4 for these enums.
        int oir = src.getCallExtraInt(EXTRA_OIR, Integer.MIN_VALUE);
        if (oir != Integer.MIN_VALUE) {
            dst.setCallExtraInt(EXTRA_OIR, oir);
        }
        int cnap = src.getCallExtraInt(EXTRA_CNAP, Integer.MIN_VALUE);
        if (cnap != Integer.MIN_VALUE) {
            dst.setCallExtraInt(EXTRA_CNAP, cnap);
        }
    }
}
