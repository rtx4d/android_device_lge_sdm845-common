# mmpf_kicker

Tiny system_ext daemon that publishes the legacy binder service
`lgeims_mmpf` so libimsmmpf.lge.so::getMMPFService() can resolve it.

## Why

After SIP signalling completes for an outgoing/incoming VoLTE call on
the LineageOS port, the call connects but **no audio plays in either
direction** and `MMPF_CP_IF::createQMISession` waits forever for an
incoming MMPF response from modem.

Root cause: `libimsmmpf.lge.so::android::MMPF::getMMPFService()` (used
by `libims.lge.so` AudioAdaptor / VideoAdaptor) blocks in a loop:

```c
while (defaultServiceManager()->getService("lgeims_mmpf") == nullptr) {
    usleep(500000);
}
```

On stock LG firmware, `lgeims_mmpf` is published by a process we never
imported into the LineageOS build (almost certainly an LG-patched
system_server hook). Until that binder is published, every MMPF
session creation in Ims6 hangs at the `getService` loop, the QMI
handshake to the modem-side MMPF QMI service never happens, and audio
plumbing stays dark.

## How

`libimsmmpf.lge.so` itself contains both the client (Bp) AND server
(Bn) sides of the `IMMPFSystem` / `IMMPFService` binder interfaces, plus
a helper:

```
android::MMPFService::instantiate()  // mangled: _ZN7android11MMPFService11instantiateEv
```

This is the classic AOSP `BinderService<T>::instantiate()` pattern. One
call publishes the service via `defaultServiceManager()->addService()`
under the name `lgeims_mmpf`.

So our daemon:

1. `dlopen("libimsmmpf.lge.so", RTLD_NOW)`
2. `dlsym("_ZN7android11MMPFService11instantiateEv")`
3. Call it. Service is now published and visible to all Ims6 clients.
4. `joinThreadPool()` forever to keep the BBinder alive.

## Why system_ext, not vendor

`libimsmmpf.lge.so` lives in `/system/system_ext/lib64/`. The service
context is `ims_service`, registered in
`system/system_ext/etc/selinux/system_ext_service_contexts`. All of
this is system-side, not vendor — so the daemon must run from the same
partition with the system-side libbinder.

This is the opposite of `vss_ims_kicker`, which talks HIDL on hwbinder
to a vendor HAL.

## Files

- `kicker/mmpf_kicker.cpp` — daemon source
- `kicker/Android.bp` — Soong module (system_ext_specific cc_binary)
- `kicker/mmpf_kicker.rc` — `class main`, runs as `system` user, parked forever
- `kicker/sepolicy/mmpf_kicker.te` — domain with `add_service ims_service`
- `kicker/sepolicy/file_contexts` — exec label for the binary
