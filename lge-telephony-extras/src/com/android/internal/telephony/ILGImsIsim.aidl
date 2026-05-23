// SPDX-FileCopyrightText: 2018 LG Electronics
// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
package com.android.internal.telephony;

import com.android.internal.telephony.ILGImsIsimCallback;
import android.os.Bundle;

interface ILGImsIsim {
    String getDomain();
    String getImpi();
    String[] getImpu();
    String getChallengeResponse(String challenge);
    String getIst();
    String[] getPcscfAddress();
    String getState();
    void setCallback(ILGImsIsimCallback callback);
    byte[] getRand();
    String getBTid();
    String getKeyLifetime();
    boolean isGbaSupported();
    void setGbaBootstrappingParameters(in byte[] rand, String autn, String naf);
    Bundle getGbaBootstrappingResponse(in byte[] rand, in byte[] autn);
    byte[] getNafExternalKey(in byte[] naf);
}
