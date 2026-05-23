// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
package com.lge.uicc;

import com.lge.uicc.ISimCallback;

interface ILGUiccService {
    String getProperty(String key, int slotId);
    boolean setProperty(String key, int slotId, String value);
    byte[] request(String tag, in byte[] payload, IBinder cb);
}
