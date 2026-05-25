// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
//
// vss_ims_kicker — bring up the LG IMS QMI handshake on devices that lack
// the stock LG framework code that normally publishes the lgeims_mmpf
// binder service.
//
// Background (full root cause analysis is in
// memory/bringup_status_volte_2026_05_23.md): the stock LG IMS stack
// expects libimsmmpf.lge.so::getMMPFService() to find a binder service
// named "lgeims_mmpf" before it ever calls IVssIms::setCallback(). On
// LineageOS that binder is never published (LG-specific system_server
// plug-in is missing), so the LGE QMI service on the modem never gets the
// AP-side handshake and qcrild rejects every SETUP_DATA_CALL on apn=ims
// with EsmCause #33 (SERVICE_OPTION_NOT_SUBSCRIBED).
//
// This daemon bypasses MMPF entirely. It just retrieves the existing
// vendor.lge.hardware.vss_ims@1.0::IVssIms HIDL service (already running
// as vendor.lge-vss_ims-hal-1-0), registers a no-op IVssImsCallback, and
// then sleeps forever. That single setCallback() call drives
// libvssims-impl.so down lgeImsImpl_init -> qcci_qmi_lge_ims_init and
// completes the handshake with the modem.
//
// Video calling and other MMPF-dependent flows will still be broken —
// fixing those requires actually porting the stock LG framework's MMPF
// publisher, which is out of scope for this minimal VoLTE-only patch.

#define LOG_TAG "vss_ims_kicker"

#include <android-base/logging.h>
#include <hidl/HidlTransportSupport.h>
#include <utils/Errors.h>
#include <utils/StrongPointer.h>

#include <vendor/lge/hardware/vss_ims/1.0/IVssIms.h>
#include <vendor/lge/hardware/vss_ims/1.0/IVssImsCallback.h>

#include <chrono>
#include <thread>

using ::android::sp;
using ::android::hardware::hidl_string;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::vendor::lge::hardware::vss_ims::V1_0::IVssIms;
using ::vendor::lge::hardware::vss_ims::V1_0::IVssImsCallback;

namespace {

class NoopCallback : public IVssImsCallback {
public:
    Return<void> lgeImsVssIndCb(const hidl_string& msg) override {
        // The modem occasionally pushes IMS state indications here. We have
        // no consumer to forward to (Ims6 handles those via libimsmmpf which
        // we are deliberately bypassing), so just log and drop.
        LOG(INFO) << "lgeImsVssIndCb: " << msg.size() << " bytes";
        return Void();
    }
};

bool kickOnce() {
    sp<IVssIms> svc = IVssIms::getService("default");
    if (svc == nullptr) {
        LOG(WARNING) << "IVssIms/default not yet available";
        return false;
    }

    sp<IVssImsCallback> cb = new NoopCallback();
    Return<void> ret = svc->setCallback(cb);
    if (!ret.isOk()) {
        LOG(ERROR) << "setCallback transaction failed: " << ret.description();
        return false;
    }

    LOG(INFO) << "setCallback delivered; LGE QMI handshake should now proceed";
    return true;
}

}  // namespace

int main() {
    using namespace std::chrono_literals;

    // Retry until the HAL is up. vendor.lge-vss_ims-hal-1-0 starts in init
    // class "hal" so it is normally up before us, but on cold boot we may
    // race with hwservicemanager.
    for (int i = 0; i < 60; ++i) {
        if (kickOnce()) {
            break;
        }
        std::this_thread::sleep_for(2s);
    }

    // Stay alive so the IVssImsCallback strong reference held by the HAL
    // remains valid. If we exit, hwbinder will release the callback,
    // libvssims-impl will drop the QMI registration, and the modem will
    // tear the IMS subscription down again.
    while (true) {
        std::this_thread::sleep_for(1h);
    }
    return 0;
}
