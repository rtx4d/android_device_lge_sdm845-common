// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
//
// lge_radio_kicker — disable qcrild's local PDN-failure throttler so SETUP_DATA_CALL
// for apn=ims is no longer short-circuited with cached SERVICE_OPTION_NOT_SUBSCRIBED.
//
// Background (full analysis: memory/vss_ims_kicker.md, "Update 2026-05-24 ~04:20"):
// stock LG framework fires the OEM RIL request RIL_REQUEST_DATA_PDN_THROTTLE_IND_REPORT
// (id=427) with enable=false ~6ms after the LGE QMI handshake completes. That request
// maps to the HIDL method ILgeRadio::reportPdnThrottleInd(int32_t serial, bool enable)
// in vendor.lge.hardware.radio@2.0, served by qcrild itself under instance "lge_radio".
//
// On LineageOS the LG-specific RILJ extension is not present, so the call is never
// made. Without it qcrild caches the very first SERVICE_OPTION_NOT_SUBSCRIBED response
// from the modem and refuses every subsequent SETUP_DATA_CALL apn=ims instantly.
// We just need to invoke that one HIDL method once at boot.
//
// The HIDL interface already exists in the LineageOS caymanslm tree at
// hardware/lge/interfaces/hardware/radio/2.0/ as the hidl_interface module
// vendor.lge.hardware.radio@2.0 — no need to reconstruct .hal files like we did for
// vss_ims_kicker.

#define LOG_TAG "lge_radio_kicker"

#include <android-base/logging.h>
#include <hidl/HidlTransportSupport.h>
#include <utils/Errors.h>
#include <utils/StrongPointer.h>

#include <vendor/lge/hardware/radio/2.0/ILgeRadio.h>

#include <chrono>
#include <thread>

using ::android::sp;
using ::android::hardware::Return;
using ::vendor::lge::hardware::radio::V2_0::ILgeRadio;

namespace {

bool kickOnce() {
    sp<ILgeRadio> radio = ILgeRadio::getService("lge_radio");
    if (radio == nullptr) {
        LOG(WARNING) << "ILgeRadio/lge_radio not yet available";
        return false;
    }

    // serial=0: arbitrary token — the response handler in qcrild logs it
    // (RILC_EX: reportPdnThrottleInd: serial 0) but does not act on it.
    // enable=false: this is the actual switch — disable per-PDN failure
    // throttling in qcrild's data plane.
    Return<void> ret = radio->reportPdnThrottleInd(0, false);
    if (!ret.isOk()) {
        LOG(ERROR) << "reportPdnThrottleInd transaction failed: " << ret.description();
        return false;
    }

    LOG(INFO) << "reportPdnThrottleInd(serial=0, enable=false) delivered to lge_radio";
    return true;
}

}  // namespace

int main() {
    using namespace std::chrono_literals;

    // qcrild registers ILgeRadio/lge_radio early in its init (see
    // RILC_EX: LgeRadio[2.0]::registerAsService(lge_radio) in radio logs).
    // The service is normally up well before us, but on cold boot we still
    // race with hwservicemanager populating the entry. Retry up to 60 times
    // (~2 minutes) with 2s spacing.
    for (int i = 0; i < 60; ++i) {
        if (kickOnce()) {
            return 0;
        }
        std::this_thread::sleep_for(2s);
    }

    LOG(ERROR) << "Gave up waiting for ILgeRadio/lge_radio";
    return 1;
}
