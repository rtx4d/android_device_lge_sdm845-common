# lge-ims-config-bridge

System_ext app that controls per-SIM IMS feature toggles (VoLTE, ViLTE,
VoWiFi, ViWiFi, RCS) on LineageOS for caymanslm.

Replaces the runtime gating that stock LG firmware does in
`com.lge.internal.telephony.ImsRadioConfigManager` (framework.jar, not
imported by LineageOS), and provides a UI for per-SIM toggles similar
to stock HiddenMenu's "Activate Vo Service" screen.

## What it does

For each active SIM, the service applies a `VoConfig` (5 booleans:
volte / vilte / vowifi / viwifi / rcs) by writing to two AOSP layers:

1. **`ProvisioningManager.setProvisioningStatusForCapability(...)`** —
   carrier-provisioning state. This is what `*#*#4636#*#*` →
   RadioInfo's "VoLTE Provisioned / VT Provisioned / WFC Provisioned"
   toggles read.
2. **`ImsMmTelManager.setVtSettingEnabled(...)` /
   `setVoWiFiSettingEnabled(...)` / `setVoWiFiRoamingSettingEnabled(...)`** —
   user-setting state. Triggers the LG IMS stack's MmTel feature
   capability cascade.

Both must be ON for IMS to register; both OFF causes
`ImsRegCallbackHelper` to drop the registration within ~1 second
(verified live via root logcat).

It also sends sticky `com.lge.action.VOLTE_CHANGED_INFO` matching stock
LG wire format (Ims6's `StateInfoChangedReceiver` listens for this).

Two global sysprops are set unconditionally on every boot — without
them the LG IMS stack never starts:

* `persist.product.lge.ims.volte_open=1`
* `persist.vendor.lge.ims.dualvolte=1`

## Default policy

For any SIM with no user override saved: VoLTE=on, ViLTE=on, VoWiFi=on,
ViWiFi=on, RCS=off. Reasoning:

* The modem does its own subscription check during `SETUP_DATA_CALL
  apn=ims` and replies `cause=33 SERVICE_OPTION_NOT_SUBSCRIBED` when a
  carrier really blocks IMS. The Android-side flags don't gate the modem.
* No operator whitelist is shipped. Stock's regional `vo_config.xml`
  covers ~110 carriers and excludes most of the world; defaulting to off
  for unknown carriers actively breaks legitimate users.
* RCS is off because it requires separate provisioning we don't replicate.

## UI

Launch **"IMS / VoLTE Activator"** from the launcher.

```
Slot 0 — MTS RUS
ICCID: 8970199...
MCC/MNC: 25001
[x] VoLTE      [x] ViLTE
[x] VoWiFi     [x] ViWiFi
[ ] RCS
[ Apply ]   [ Reset to defaults ]
```

* **Apply** — persists the per-ICCID override into SharedPreferences and
  kicks the service to re-apply immediately. Override is keyed by ICCID,
  so it survives moving the SIM between slots.
* **Reset to defaults** — drops the override; the SIM falls back to
  defaults on the next sync.

## Stock vo_config.xml interop

`VoConfigUpdateReceiver` listens for sticky
`com.lge.action.ACTION_VO_CONFIG_UPDATE` (the broadcast stock
HiddenMenu's "Activate Vo Service" sends after writing
`/data/shared/cust/config/vo_config.xml`). When received the service
re-parses the XML, runs the same 8-tier specificity match stock
`VoConfigParser` uses (mcc → mnc → gid → spn → imsi), and persists
each matched SIM's choice as a per-ICCID override.

Passive interop only — we don't trigger or depend on the stock
producer; anything that follows the protocol gets picked up.

## Pipeline

```
boot / SIM hot-swap / Apply
       │
       ▼
LgeImsConfigBridgeService.syncAllSlots()
       │
       ├─ for each active sub: resolveConfig(iccid)
       │     └─ SharedPreferences override OR VoConfig.defaults()
       │
       ├─ applyMmTelSettings(subId, cfg)
       │     ├─ ProvisioningManager.setProvisioningStatusForCapability
       │     │     VOICE/LTE, VIDEO/LTE, VOICE/IWLAN, VIDEO/IWLAN
       │     │     ↳ visible in *#*#4636#*#* RadioInfo
       │     │
       │     └─ ImsMmTelManager.setVt/VoWiFi/VoWiFiRoamingSettingEnabled
       │           ↳ triggers MmTel capability change cascade
       │           ↳ ImsRegCallbackHelper drops registration if all caps off
       │
       └─ sendStickyBroadcast com.lge.action.VOLTE_CHANGED_INFO
             extras: volte/volte2, vilte/vilte2, vowifi/vowifi2, rcs/rcs2
             ↳ Ims6.StateInfoChangedReceiver consumes for policy update
```

## Build

```mk
PRODUCT_PACKAGES += LgeImsConfigBridge
```

```sh
m LgeImsConfigBridge && m systemextimage
```

Installs:

* `/system_ext/priv-app/LgeImsConfigBridge/LgeImsConfigBridge.apk`
* `/system_ext/etc/permissions/permissions_com.lineageos.lgeimsconfigbridge.xml`

## Verification

```sh
# Service lifecycle
adb logcat | grep LgeImsCfg

# Live IMS state — toggling Apply should flip these within ~1 second:
adb logcat | grep -E 'ImsRegCallbackHelper|MmTel Capabilities'

# RadioInfo's view (4636 → IMS Service Configuration):
adb shell am start -n com.android.phone/.settings.RadioInfo

# Globals required by the stack:
adb shell getprop | grep -E 'persist\.product\.lge\.ims\.volte_open|persist\.vendor\.lge\.ims\.dualvolte'

# First SETUP_DATA_CALL apn=ims should be cause=NONE:
adb logcat -b radio | grep -E 'SETUP_DATA_CALL.*ims|cause=NONE|cause=33'
```

When all checkboxes are unchecked + Apply: expect logcat to show
`ImsRegCallbackHelper: REGISTRATION_STATE_REGISTERED → REGISTRATION_STATE_NOT_REGISTERED`
within ~1 second. Re-checking + Apply reverses it.

## Persistence

* User overrides live in
  `/data/user_de/0/com.lineageos.lgeimsconfigbridge/shared_prefs/ims_overrides.xml`
  (Direct Boot aware, available before user unlock).
* `android:directBootAware="true"` lets the service run from the earliest
  boot phase (LOCKED_BOOT_COMPLETED).
* `android:persistent="true"` keeps the SubscriptionManager listener
  alive past force-stop.

## Files

| File | Purpose |
|------|---------|
| `Android.bp` | Soong module — compiles APK, installs privapp whitelist. |
| `AndroidManifest.xml` | Activity (LAUNCHER), Service, BootReceiver, VoConfigUpdateReceiver. |
| `permissions_com.lineageos.lgeimsconfigbridge.xml` | Privapp whitelist (READ_PRIVILEGED_PHONE_STATE, MODIFY_PHONE_STATE, WRITE_SECURE_SETTINGS, PERFORM_IMS_SINGLE_REGISTRATION). |
| `src/.../LgeImsConfigBridgeService.java` | Provisioning + MmTel writer + broadcast sender + vo_config.xml import. |
| `src/.../MainActivity.java` | UI for per-SIM override. |
| `src/.../BootReceiver.java` | Starts the service on BOOT_COMPLETED + LOCKED_BOOT_COMPLETED. |
| `src/.../VoConfigUpdateReceiver.java` | Stock vo_config.xml interop receiver. |
| `res/layout/activity_main.xml` | Top-level scrollable layout. |
| `res/layout/slot_card.xml` | Per-SIM card with checkboxes. |
| `res/values/strings.xml` | UI strings (translatable). |
| `res/values/styles.xml` | App theme. |

## What does NOT work / was removed

These were tried in earlier versions and confirmed empirically to be
no-ops on this LineageOS porting surface:

* Per-slot `persist.product.lge.support{volte,vt,vowifi,viwifi,rcs}[.sim2]`
  sysprops — not consulted at runtime by the LG IMS stack on this port.
* `Settings.Global.volte_vt_enabled<subId>` / `enhanced_4g_lte_mode_enabled<subId>` /
  `wfc_ims_enabled<subId>` — Android 15 pipeline doesn't gate on these.
* `siminfo.voims_opt_in_status` (via reflection on
  `SubscriptionManager.setSubscriptionProperty`) — written but ignored
  by the registration pipeline.
* Operator whitelist (`vo_config.xml` shipped with the module) —
  partial coverage, broke unknown carriers.

What replaced them is the two-layer ProvisioningManager + ImsMmTelManager
approach above, verified to flip live registration state.
