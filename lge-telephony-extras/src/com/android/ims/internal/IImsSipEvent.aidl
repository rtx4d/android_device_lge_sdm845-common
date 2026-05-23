// SPDX-FileCopyrightText: 2018 LG Electronics
// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
package com.android.ims.internal;

import com.android.ims.internal.IImsSipEventCallback;

interface IImsSipEvent {
    void addCallback(IImsSipEventCallback callback);
    void removeCallback(IImsSipEventCallback callback);
    void requestDeviceInfo();
    void requestDialogState();
}
