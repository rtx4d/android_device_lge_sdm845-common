// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
//
// mmpf_kicker — publish the legacy binder service `lgeims_mmpf` that
// the stock LG IMS audio/video pipeline (libimsmmpf.lge.so) expects to
// find via android::defaultServiceManager().
//
// Background: stock LG ships a system_server-side hook that, at boot,
// calls android::MMPFService::instantiate() (defined inside
// libimsmmpf.lge.so itself). That single call publishes `lgeims_mmpf`
// in the system service manager. On LineageOS we don't have that hook
// — and `libims.lge.so` (the JNI lib loaded by Ims6.apk) is purely a
// client of MMPF, it never publishes the service.
//
// Symptom on a connected VoLTE call without this daemon:
//   - SIP signalling completes (100/183/PRACK 200 OK/UPDATE 200 OK/INVITE 200 OK)
//   - MMPF_CP_IF::createQMISession returns OK locally (cp session 0)
//   - Audio HAL switches voicemmode1-call, vsid 0x11c05000 activated
//   - …but no incoming MMPF messages from modem; no audio in/out
//
// libimsmmpf.lge.so::android::MMPF::getMMPFService() loops with
//   while (defaultServiceManager()->getService("lgeims_mmpf") == nullptr)
//       usleep(500ms);
// — so until the binder is published, every Ims6 client sits inside that
// loop, the QMI handshake never happens, and modem<->AP audio plumbing
// stays dark.
//
// Resolution: dlopen libimsmmpf.lge.so, resolve the mangled symbol
//   `_ZN7android11MMPFService11instantiateEv`
// (= android::MMPFService::instantiate()) and call it. That's the
// AOSP BinderService<T>::instantiate() pattern: one call publishes the
// service via defaultServiceManager()->addService().
//
// The library handles the BBinder lifetime and onTransact dispatch on
// its own; we only need to keep the process alive (joinThreadPool) so
// the binder thread that services the calls keeps running.

#define LOG_TAG "mmpf_kicker"

#include <android-base/logging.h>
#include <binder/IPCThreadState.h>
#include <binder/ProcessState.h>

#include <dlfcn.h>

namespace {

// android::MMPFService::instantiate()
//
// Stock symbol is `void android::MMPFService::instantiate()` — the
// classic AOSP `BinderService<T>::instantiate()` template returns void.
// We MUST NOT read x0 after the call: it holds whatever the callee
// last wrote to it (often a moved sp<IBinder>), not a status_t.
constexpr const char* kInstantiateSym = "_ZN7android11MMPFService11instantiateEv";
constexpr const char* kMmpfLibPath    = "libimsmmpf.lge.so";

using instantiate_fn_t = void (*)();

bool publishMmpfService() {
    void* handle = dlopen(kMmpfLibPath, RTLD_NOW);
    if (!handle) {
        LOG(ERROR) << "dlopen(" << kMmpfLibPath << ") failed: " << dlerror();
        return false;
    }

    auto fn = reinterpret_cast<instantiate_fn_t>(dlsym(handle, kInstantiateSym));
    if (!fn) {
        LOG(ERROR) << "dlsym(" << kInstantiateSym << ") failed: " << dlerror();
        return false;
    }

    fn();
    LOG(INFO) << "MMPFService::instantiate() called — lgeims_mmpf should now be published";
    return true;
}

}  // namespace

int main() {
    // We are a service-manager-side binder publisher, not a HIDL/AIDL HAL.
    // ProcessState gives us the binder thread pool that the BBinder
    // implementations inside libimsmmpf.lge.so need.
    ::android::ProcessState::self()->setThreadPoolMaxThreadCount(4);
    ::android::ProcessState::self()->startThreadPool();

    if (!publishMmpfService()) {
        LOG(ERROR) << "failed to dlopen/dlsym libimsmmpf — parking anyway "
                      "to avoid init crash-loop rollback";
        // Deliberately don't exit non-zero: init has a 4-restarts-in-4-min
        // updatable_crashing trigger that triggers flags_health_check
        // rollback, which kills qcrild and breaks the modem. Better to
        // park silently than take down the whole telephony stack.
    }

    // Block forever so the published BBinder stays referenced.
    ::android::IPCThreadState::self()->joinThreadPool();
    return 0;  // unreachable
}
