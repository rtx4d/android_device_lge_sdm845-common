// SPDX-FileCopyrightText: 2018 LG Electronics
// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
package com.android.internal.telephony;

import com.android.internal.telephony.LGImsCellInfo;

interface ILGImsNetworkInfoCallback {
    oneway void onCellInfo(in LGImsCellInfo cellInfo);
    oneway void onNotificationEnabled(boolean enabled);
}
