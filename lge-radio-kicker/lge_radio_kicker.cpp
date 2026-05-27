// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
//
// lge_radio_kicker — disable qcrild's local PDN-failure throttler so SETUP_DATA_CALL
// for apn=ims is no longer short-circuited with cached SERVICE_OPTION_NOT_SUBSCRIBED.
//
// Background (full analysis: memory/lge_radio_kicker.md, "Update 2026-05-27 — DSDS"):
// stock LG framework fires the OEM RIL request RIL_REQUEST_DATA_PDN_THROTTLE_IND_REPORT
// (id=427) with enable=false on every active phone. That request maps to the HIDL
// method ILgeRadio::reportPdnThrottleInd(int32_t serial, bool enable) in
// vendor.lge.hardware.radio@2.0, served by qcrild itself under instance "lge_radio"
// (slot 0) and qcrild2 under "lge_radio2" (slot 1).
//
// On LineageOS the LG-specific RILJ extension is not present so the call is never
// made. Without it qcrild caches the very first SERVICE_OPTION_NOT_SUBSCRIBED response
// from the modem and short-circuits every subsequent SETUP_DATA_CALL apn=ims with
// PDN_IPV4_CALL_THROTTLED (0x7f2). One kick at boot is NOT enough — qcrild's throttler
// auto-rethrottles after EACH modem failure. So we must keep kicking periodically:
//
//   * On carriers that accept IMS subscription on the first attempt (MTS RU on slot 0)
//     the kicker is a no-op after the first iteration.
//   * On carriers that reject the first attempt with cause=33 (Beeline RU on slot 1)
//     each retry from AOSP DRM lands on a re-armed throttler — only by repeatedly
//     calling reportPdnThrottleInd(false) every few seconds do we keep the throttler
//     window open for DRM's next SETUP_DATA_CALL to actually reach the modem.
//
// 10s period chosen to be much shorter than DRM's smallest non-zero retry interval
// (2.5s for cap=eims, then 2.5s/3s/5s/... for ims), giving every SETUP_DATA_CALL a
// throttler-disabled window.
//
// The HIDL interface already exists in the LineageOS caymanslm tree at
// hardware/lge/interfaces/hardware/radio/2.0/ as the hidl_interface module
// vendor.lge.hardware.radio@2.0 — no need to reconstruct .hal files like we did for
// vss_ims_kicker.
//
// =====================================================================
// DO NOT add setLteProc / setImsRegistrationStatus calls to this kicker.
// =====================================================================
// Investigation 2026-05-27 (memory: setlteproc_dispatch_unregistered.md):
//
// Even with the EXACT semantics from ghidra-reverse of libril-qc-hal-qmi.so
// (setLteProc type ∈ [0..5] enum, setImsRegistrationStatus 6-int vec
// {reg_state,reg_services,detail_state,system_mode,reason,slot_id}), calling
// either method from this kicker SIGSEGVs qcrild at boot:
//
//   #00 dispatchInts(int,int,int,int,...)+528   /vendor/lib64/libril-qc-hal-qmi.so
//   #01 LgeRadio::setLteProc(int,int)+124
//   signal 11 SIGSEGV  fault addr 0x8  null pointer dereference
//
// LgeRadio::reportPdnThrottleInd uses a different path internally
// (SolicitedMessage<QcRilRequestMessageCallbackPayload>) which is initialized
// at qcrild start. LgeRadio::setLteProc and LgeRadio::setImsRegistrationStatus
// route through `dispatchInts(serial, slot_id, msg_id, count, ...)` which
// does a lookup in qcrild's global handler table by msg_id (0x196 for
// setLteProc, 0x187 for setImsRegistrationStatus). Those handler slots are
// only populated when stock LG framework (Java side, in framework.jar's
// com.lge.internal.telephony.* / AppLGDcTracker.java) makes its init-time
// RIL_REQUEST round-trip. LineageOS framework never does that.
//
// So the dispatchInts path lands on a NULL handler → SIGSEGV. The methods
// can't be called blind from a vendor daemon — they require Java framework
// init that we don't (and can't easily) replicate.

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

enum class KickResult {
    kOk,
    kNotAvailable,
    kTransactionFailed,
};

KickResult kickOnce(const char* instance) {
    sp<ILgeRadio> radio = ILgeRadio::getService(instance);
    if (radio == nullptr) {
        return KickResult::kNotAvailable;
    }

    // serial=0: arbitrary token — qcrild logs it as
    // "RILC_EX: reportPdnThrottleInd: serial 0" but does not act on it.
    // enable=false: actual switch — disable per-PDN failure throttling in
    // qcrild's data plane. qcrild auto-rearms it after every modem failure,
    // which is why we keep calling it forever.
    Return<void> ret = radio->reportPdnThrottleInd(0, false);
    if (!ret.isOk()) {
        LOG(ERROR) << "reportPdnThrottleInd transaction failed on "
                   << instance << ": " << ret.description();
        return KickResult::kTransactionFailed;
    }
    return KickResult::kOk;
}

// In DSDS each qcrild instance publishes its own ILgeRadio:
//   - "lge_radio"  is served by qcrild  (slot 0)
//   - "lge_radio2" is served by qcrild2 (slot 1)
constexpr const char* kInstances[] = { "lge_radio", "lge_radio2" };

// 10s period — shorter than DRM's typical retry intervals so every retry hits
// a recently-disabled throttler.
constexpr auto kPeriod = std::chrono::seconds(10);

}  // namespace

int main() {
    KickResult last[2] = { KickResult::kTransactionFailed, KickResult::kTransactionFailed };
    bool first_iteration = true;

    while (true) {
        for (size_t j = 0; j < 2; ++j) {
            KickResult now = kickOnce(kInstances[j]);

            if (first_iteration || now != last[j]) {
                switch (now) {
                    case KickResult::kOk:
                        LOG(INFO) << "reportPdnThrottleInd(serial=0, enable=false) "
                                  << "delivered to " << kInstances[j];
                        break;
                    case KickResult::kNotAvailable:
                        LOG(WARNING) << "ILgeRadio/" << kInstances[j]
                                     << " not available (single-SIM build, or "
                                     << "qcrild not yet up)";
                        break;
                    case KickResult::kTransactionFailed:
                        // Already logged inside kickOnce().
                        break;
                }
            }
            last[j] = now;
        }
        first_iteration = false;
        std::this_thread::sleep_for(kPeriod);
    }

    return 0;
}
