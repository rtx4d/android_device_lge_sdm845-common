// SPDX-FileCopyrightText: 2018 LG Electronics
// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
package com.android.internal.telephony;

import com.android.internal.telephony.LGImsEnvelope;
import com.android.internal.telephony.LGImsHVoLtePreference;
import com.android.internal.telephony.LGImsNetworkFeature;

interface ILGImsPhoneProxyCallback {
    oneway void onCommand(int command, int value);
    oneway void onLteStateChanged(in int[] state);
    oneway void onNetworkFeatureChanged(in LGImsNetworkFeature feature, int type);
    oneway void onModemInfoReadCompleted(int type, int value, String extra);
    oneway void onEfRecordReadCompleted(int efId, String data);
    oneway void onEnvelopeMessageResponseReceived(in LGImsEnvelope envelope, int result, String extra);
    oneway void onDataLimitChanged(boolean limited);
    oneway void onHVoLtePreferenceChanged(in LGImsHVoLtePreference preference);
    oneway void onModemRestarted();
}
