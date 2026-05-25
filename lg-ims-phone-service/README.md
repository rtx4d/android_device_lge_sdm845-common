# LGImsPhoneService — caymanslm port

System-uid app that publishes the `com.lge.ims.phone` binder
(`ILGImsPhoneService`) consumed by Ims6's `ImsPhoneProxyManager`. The only
substantively-implemented method is `ILGImsPhoneProxy.getPcscfAddress(apnType)`,
which is what unblocks `AoSPCSCF::IsConfigured` in the native LG IMS stack.

## Why

Final remaining gap as of 2026-05-25 ~05:15 in the VoLTE bring-up trail:

1. `Apn::requestNetwork [0] type = mobile_ims` — Ims6 issues the request
2. AOSP `DataNetworkController` brings up `apn=ims` PDN, modem returns
   `cause=NONE` with `pcscf=[10.32.6.190, 10.32.246.190]`
3. Java `Apn$ImsNetworkCallback::onLinkPropertiesChanged` receives the
   addresses. Ims6 logs `linkProperties: ... PcscfAddresses: [...]`.
4. `AoSConnection [0:mobile_ims] STATE_ACTIVE` → `Connection_Connected`
5. **Native** `AoSPCSCF [0:aos_app_0] GetFromPCO :: IPv(4)` → `no pcscf
   count` → `pcscf list :: ()` → `IsConfigured :: (false)`. **AoSReg
   never starts.**

The native `AoSPCSCF` does **not** read P-CSCF directly from
`LinkProperties` — instead it goes through Java
`DCApn.getPcscfAddress(apnType)`:

```java
if (MSimUtils.isMultiSimEnabled()) {
    return LGExtApi.Data.getPcscfAddress(slotId, apnType);
}
return telephonyManager.getPcscfAddress(apnType);
```

`isMultiSimEnabled()` returns `true` on caymanslm
(`persist.radio.multisim.config = dsds`), so the path goes through
`LGExtApi.Data.getPcscfAddress`. That method already exists inside
Ims6.apk and chains to:

```
ImsPhoneProxyManager.getInstance().getPhoneProxy(slotId).getPcscfAddress(apnType)
```

`ImsPhoneProxyManager.bindService()` calls
`LGImsPhoneService.getService()` which is
`ServiceManager.getService("com.lge.ims.phone")`. On stock LG firmware
that binder is registered from inside the Phone process by
`LGImsPhoneService.create()` in `PhoneFactory`. LineageOS does not
import that LG framework class, so the binder is unregistered and
`mService` ends up `null`. Every per-slot `ImsPhoneProxy` then wraps a
null binder and `getPcscfAddress()` returns `null`.

This app simply **publishes that binder**. Its `getPhoneProxy(slot)`
returns a Stub whose `getPcscfAddress(apnType)`:

1. Resolves slot → subId via `SubscriptionManager.getSubscriptionIds`
2. Walks `ConnectivityManager.getAllNetworks()` for one with
   `TRANSPORT_CELLULAR + NET_CAPABILITY_IMS` (or `EIMS`/`XCAP`/
   `INTERNET` depending on `apnType`) and matching subId in its
   `TelephonyNetworkSpecifier`
3. Returns `LinkProperties.getPcscfServers()` formatted as `String[]`
   — bare numeric form, e.g. `["10.32.6.190", "10.32.246.190"]`

Everything else in the `ILGImsPhoneProxy` AIDL surface (cell-info
caches, modem-info reads, FDN list, network-feature broadcasts,
emergency-call state, VoNR session control) is a no-op stub returning
sensible defaults. None of those are on the VoLTE registration path.

## Integration

1. Drop the project into `device/lge/sdm845-common/`:

   ```
   cp -r lg-ims-phone-service device/lge/sdm845-common/LGImsPhoneService
   ```

2. Add to `PRODUCT_PACKAGES` in `device/lge/sdm845-common/sdm845.mk`:

   ```
   PRODUCT_PACKAGES += \
       LGImsPhoneService
   ```

   The `privapp_whitelist_com.lineageos.lgimsphone` permission file is
   pulled in automatically via `required:` in `Android.bp`.

3. Build:

   ```
   m LGImsPhoneService
   m systemextimage
   ```

   Since the app links against `lge-telephony-extras` (for AIDL
   interfaces and stub classes), make sure that library is also in the
   build. Lands in `/system_ext/priv-app/LGImsPhoneService/`.

## Verify

After flash, on every boot the chain should be:

```
adb shell logcat | grep LGImsPhoneSvc
  -> "Service onCreate"
  -> "Service onStartCommand"
  -> "addService(\"com.lge.ims.phone\") OK; slots=2"
  -> "sendBroadcast(com.lge.ims.action.IMS_PHONE_STARTED)"
```

Ims6 should now bind the service:

```
adb shell logcat | grep LGIMS_IPPM
  -> "bindService :: android.os.BinderProxy@..."
  -> "tryBindService :: retry=1" then OK (or no retry if we beat Ims6 to start)
  -> "notifyServiceConnected"
```

When `AoSConnection [0:mobile_ims] Connection_Connected` fires, the
native AoSPCSCF read should now succeed:

```
adb shell logcat | grep -E "LGImsPhoneSvc|AoSPCSCF|AoSReg|SendREGISTER"
  -> "getPcscfAddress[0] apnType=ims -> [10.32.6.190, 10.32.246.190]"
  -> "AoSPCSCF [0:aos_app_0] IsConfigured :: (true)"
  -> "AoSConnector :: Connection_Activated"
  -> "AoSRegistration :: Start"
  -> "AoSRegistration :: PrepareRegistration"
  -> "AoSRegistration :: SendREGISTER"
```

Final SIP confirmation:

```
adb shell logcat | grep -E "SIP/2.0 200|REGISTER ims:"
adb shell dumpsys phone | grep -E "Voice: true|MMTEL.*READY"
```

## Notes

* **Race with Ims6 boot order.** `ImsPhoneProxyManager.create()` is
  called from inside Ims6 process startup. If Ims6 starts before this
  service publishes, `bindService()` returns null, gets retried 3× at
  1s intervals, then idles. Our `IMS_PHONE_STARTED` broadcast handler
  in `ImsPhoneProxyManager.AnonymousClass1.onReceive` re-triggers
  binding regardless, so the broadcast is the safety net.

* **uid=system requirement.** `ServiceManager.addService` on a
  system-defined name requires uid=system or uid=root. We get uid=system
  via `sharedUserId="android.uid.system"` + `certificate: "platform"`.
  Without that the binder publishes silently fails.

* **Selinux.** `system_app` domain (which this app inherits via
  `sharedUserId=system`) is allowed `add { service_manager_type }` for
  most service names. If `addService` is denied, look for an avc deny
  in dmesg pointing at `tcontext=u:object_r:default_android_service:s0`
  — fix is to declare a typed service entry in
  `system_ext/sepolicy/private/service_contexts`:
  `com.lge.ims.phone u:object_r:lge_ims_phone_service:s0` plus the
  matching `type lge_ims_phone_service, service_manager_type;` and
  allow rule for `system_app`.

* **NetworkCapabilities subscriptionId fallback.** Some Android 15
  builds drop the `TelephonyNetworkSpecifier` from satisfied networks
  on `ConnectivityManager.getAllNetworks()` callbacks. We fall back to
  `nc.getSubscriptionIds()` when the specifier isn't a
  `TelephonyNetworkSpecifier`.

* **Reverse:** drop the `PRODUCT_PACKAGES` line. Note that this
  re-introduces the gap: `getPcscfAddress()` returns null and IMS
  registration cannot start.
