# lge-ims-config-bridge

System_ext app that replaces the runtime side of stock LG
`ImsRadioConfigManager` and the UI side of stock `HiddenMenu`'s
"Activate Vo Service" screen.

## Why

LineageOS 22.2 on caymanslm doesn't import `com.lge.internal.telephony.ImsRadioConfigManager`
or `com.lge.internal.telephony.VoConfigParser` from stock LG `framework.jar`.
Without them:
* The per-slot LG IMS sysprops (`persist.product.lge.support{volte,vt,vowifi,viwifi,rcs}[.sim2]`)
  are unset.
* The sticky broadcast `com.lge.action.VOLTE_CHANGED_INFO` that
  `Ims6.StateInfoChangedReceiver` subscribes to is never sent.
* The user can't toggle IMS features per-SIM through the stock HiddenMenu UI
  (HiddenMenu itself is in `/product/app/`, but its toggle target — the
  IRCM/VoConfig pipeline — doesn't exist on LineageOS).

This module replaces both pieces.

## Policy

**Default-on.** For any SIM with no user override, the bridge writes
`volte=1, vilte=1, vowifi=1, viwifi=1, rcs=0` sysprops and broadcasts the
matching extras. Rationale:

* The modem does its own IMS subscription check and replies
  `cause=33 SERVICE_OPTION_NOT_SUBSCRIBED` when a carrier really blocks
  IMS — the sysprops are not the gate, they are an OEM informational hint.
* Shipping a partial operator whitelist (like stock's regional
  `vo_config.xml`) actively breaks unknown carriers. Most non-EU carriers
  are missing from the stock EU XML.
* Empirical 2026-05-27 tests on LineageOS show `cause=NONE` SETUP_DATA_CALL
  on both slots regardless of per-slot sysprop value, confirming the
  modem-side gate is independent.

**RCS off by default.** RCS requires carrier provisioning that we don't
replicate; turning it on without provisioning produces noisy logs.

## UI

Launch "IMS / VoLTE Activator" from the launcher. The activity shows
one card per active subscription:

```
Slot 0 — MTS RUS
ICCID: 8970199...
MCC/MNC: 25001
[x] VoLTE      [x] ViLTE
[x] VoWiFi     [x] ViWiFi
[ ] RCS
[ Apply ]   [ Reset to defaults ]
```

Apply persists the per-ICCID override into SharedPreferences and kicks
the service to re-apply. Reset drops the override and reverts to defaults.

## Stock HiddenMenu compatibility

The bridge also accepts input from stock LG HiddenMenu's
"Activate Vo Service" UI. When the user applies via HiddenMenu, it:
1. Writes `/data/shared/cust/config/vo_config.xml`
2. Sends sticky `com.lge.action.ACTION_VO_CONFIG_UPDATE`

`VoConfigUpdateReceiver` listens for that action and forwards it to the
service, which re-parses the XML, runs the same 8-tier specificity match
stock `VoConfigParser` uses (mcc + mnc + gid + spn + imsi → catch-all),
and persists each matched SIM's choice as a per-ICCID override. The
result is identical to using our own UI activity.

This means a user can keep using the stock HiddenMenu workflow if they
prefer (`mkdir /sdcard/enable_ue` to unlock the menu first), and our
bridge will pick up the changes without any further wiring.

## Pipeline

```
boot / SIM hot-swap
       │
       ▼
LgeImsConfigBridgeService.syncAllSlots()
       │
       ├─ for each active sub: resolveConfig(iccid)
       │     └─ SharedPreferences override OR VoConfig.defaults()
       │
       ├─ writeSyspropsForSlot(slot, cfg)
       │     └─ persist.product.lge.support{volte,vt,vowifi,viwifi,rcs}[.sim2]
       │
       └─ broadcastVolteChanged(slot, cfg)
             └─ sendStickyBroadcast com.lge.action.VOLTE_CHANGED_INFO
                  extras: volte/volte2, vilte/vilte2, vowifi/vowifi2, rcs/rcs2
                  (Ims6.StateInfoChangedReceiver consumes this)
```

Globals `persist.product.lge.ims.volte_open=1` and
`persist.vendor.lge.ims.dualvolte=1` are set unconditionally on service
start. These are required by the LG IMS stack regardless of per-slot config.

## Build

```mk
PRODUCT_PACKAGES += LgeImsConfigBridge
```

```sh
m LgeImsConfigBridge && m systemextimage
```

Installed to:
* `/system_ext/priv-app/LgeImsConfigBridge/LgeImsConfigBridge.apk`
* `/system_ext/etc/permissions/permissions_com.lineageos.lgeimsconfigbridge.xml`

## Verification

```sh
# Service lifecycle
adb logcat | grep LgeImsCfg

# Sysprops written by us
adb shell getprop | grep -E 'persist\.product\.lge\.support|persist\.product\.lge\.ims|persist\.vendor\.lge\.ims'

# IMS bring-up should follow on both slots
adb logcat -b radio | grep -E 'SETUP_DATA_CALL.*ims|cause=NONE|cause=33'
adb shell dumpsys telephony.registry | grep -E 'mImsRegistered|MmTel'
```

## Persistence

* SharedPreferences live in `/data/user_de/0/com.lineageos.lgeimsconfigbridge/shared_prefs/ims_overrides.xml`
  (Direct Boot aware, available before user unlock).
* The service is `android:directBootAware="true"` so it runs from the
  earliest boot phase.
* `android:persistent="true"` on the application keeps the SubscriptionManager
  listener alive past force-stop.

## Files

| File | Purpose |
|------|---------|
| `Android.bp` | Soong module — compiles APK, installs privapp whitelist. |
| `AndroidManifest.xml` | Activity (LAUNCHER), Service, BootReceiver. |
| `permissions_com.lineageos.lgeimsconfigbridge.xml` | Privapp whitelist. |
| `src/.../LgeImsConfigBridgeService.java` | Sysprop writer + broadcast sender. |
| `src/.../MainActivity.java` | UI for per-SIM override. |
| `src/.../BootReceiver.java` | Starts the service on boot. |
| `res/layout/activity_main.xml` | Top-level scrollable layout. |
| `res/layout/slot_card.xml` | Per-SIM card with checkboxes. |
| `res/values/strings.xml` | UI strings (translatable). |
| `res/values/styles.xml` | App theme. |
