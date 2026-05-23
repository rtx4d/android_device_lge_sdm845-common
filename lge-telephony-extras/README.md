# lge-telephony-extras

Side-jar that re-exposes the LG-specific framework classes that
`Ims6.apk` (port from stock LG Velvet G910EMW30l) imports from stock
`framework.jar`. Without these, the APK will fail dex loading on a
LineageOS build because the AOSP framework jar doesn't contain the
`com.lge.{config,os,sysprop,uicc,lgdata}` packages.

## Scope (MVP)

What's in this jar right now:

- `com.lge.config.Features2` — three feature flags (`ims()`, `laop()`,
  `lapex()`) plus the property/version helpers Ims6 transitively pulls
  in through `com.lge.os.Build`.
- `com.lge.os.{Build, PropertyUtils}` — thin wrappers over
  `SystemProperties.get()`.
- `com.lge.uicc.{ILGUiccService.aidl, ISimCallback.aidl, LGUiccManager}`
  — binder client used by Ims6 to query the `lguicc` service. On a
  LineageOS build that service is not registered; the manager handles
  that case by returning the supplied default values, so `Ims6` keeps
  running with no UICC-specific data. Patching this is out of scope for
  the VoLTE bring-up.

## Deliberately NOT in this MVP

- `com.android.internal.telephony.{LGImsPhoneProxy, ILGImsPhoneProxy,
  ILGImsPhoneProxyCallback}` — the 4000-line AIDL bridge between the
  AOSP Phone process and `Ims6`. Will be added in a follow-up after the
  MVP is verified to load.
- `com.lge.sysprop.ExportedVendorProperties` — sysprop_library
  generated class. Needs its own `.sysprop` file rather than a
  hand-rolled stub. Add when a runtime crash points at it.
- `com.lge.lgdata.LGDataPhoneConstants`, `com.lge.uicc.LGUiccCard`,
  `com.lge.uicc.SimStateListener` — pulled in through indirect Ims6
  paths; same approach: add when they show up in a `ClassNotFoundError`.

## How to wire into the build

1. Move (or symlink) this directory into the LineageOS source tree at
   `vendor/lge/lge-telephony-extras/`.
2. Add to `device/lge/sdm845-common/sdm845.mk`:
   ```mk
   PRODUCT_PACKAGES += \
       lge-telephony-extras \
       lge-telephony-extras.xml

   PRODUCT_SYSTEM_EXT_BOOT_JARS += lge-telephony-extras
   ```
3. Soong namespace import — the device tree already exports
   `device/lge/sdm845-common` and `hardware/lge` through
   `PRODUCT_SOONG_NAMESPACES`. If you put the module elsewhere, add
   that path to `PRODUCT_SOONG_NAMESPACES`.

## Why a side-jar and not a framework patch

The stock LG `framework.jar` contains roughly 70 LG-specific top-level
packages (`com.lge.{andsf, bnr, bluetooth, ...}`), most unrelated to
IMS. Patching all of them into AOSP `frameworks/base` on every LOS
upgrade would be a maintenance nightmare and most of the surface is
unused on this port. A side-jar lets us ship only the classes Ims6
genuinely imports and keep the AOSP framework untouched.
