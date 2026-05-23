// SPDX-FileCopyrightText: 2018 LG Electronics
// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
package com.android.internal.telephony;

import com.android.internal.telephony.ILGImsIsim;
import com.android.internal.telephony.ILGImsPhoneProxy;
import com.android.internal.telephony.ILGImsVoNR;

interface ILGImsPhoneService {
    ILGImsIsim getIsimInterface(int slot);
    ILGImsPhoneProxy getPhoneProxy(int slot);
    ILGImsVoNR getVoNRInterface(int slot);
}
