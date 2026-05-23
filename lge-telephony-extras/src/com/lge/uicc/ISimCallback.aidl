// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
package com.lge.uicc;

interface ISimCallback {
    oneway void sendEvent(int what, int arg1, int arg2, in byte[] payload);
}
