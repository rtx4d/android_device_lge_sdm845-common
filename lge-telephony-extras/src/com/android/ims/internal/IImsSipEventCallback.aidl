// SPDX-FileCopyrightText: 2018 LG Electronics
// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
package com.android.ims.internal;

import com.android.ims.LGImsDeviceInfo;
import com.android.ims.LGImsDialogState;

oneway interface IImsSipEventCallback {
    void imsSipEventDeviceInfoChanged(in LGImsDeviceInfo deviceInfo);
    void imsSipEventDialogStateChanged(in LGImsDialogState dialogState);
}
