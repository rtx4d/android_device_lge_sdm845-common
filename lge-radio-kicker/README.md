# lge_radio_kicker integration steps for caymanslm
#
# This is a long-running vendor HAL daemon that periodically (every 10s)
# issues HIDL calls to disable qcrild's local PDN-failure throttler:
#   ILgeRadio::reportPdnThrottleInd(serial=0, enable=false)
# on EVERY ILgeRadio instance:
#   vendor.lge.hardware.radio@2.0::ILgeRadio/lge_radio   served by qcrild
#   vendor.lge.hardware.radio@2.0::ILgeRadio/lge_radio2  served by qcrild2
#
# Stock LG framework calls reportPdnThrottleInd on every active phone
# (verified in stock log_velvet.txt — serials 145/146 for PHONE0/1).
# Without it, qcrild caches the very first SERVICE_OPTION_NOT_SUBSCRIBED
# reply from the modem on that slot and refuses every subsequent
# SETUP_DATA_CALL apn=ims with cause=PDN_IPV4_CALL_THROTTLED (0x7f2).
# Result: IMS+VoLTE works on slot 0 but never on slot 1 (or vice versa
# depending on which slot got the first failure).
#
# Why periodic, not oneshot:
# qcrild auto-rearms the throttler after EVERY modem failure. A single
# boot-time kick was empirically insufficient on Beeline RU slot 1
# (cause=33 SERVICE_OPTION_NOT_SUBSCRIBED on first attempt) — every
# subsequent SETUP_DATA_CALL retry returned PDN_IPV4_CALL_THROTTLED
# instead of reaching the modem. By kicking every 10s the throttler
# never stays armed long enough to short-circuit DRM's retries
# (DRM's smallest retry interval is 2.5s and grows from there). On
# carriers that accept IMS subscription on the first attempt (MTS RU
# slot 0), the periodic kicks are pure no-ops after IMS is up.
#
# Unlike vss_ims_kicker, this kicker DOES NOT need to reconstruct any .hal
# files — the hidl_interface module vendor.lge.hardware.radio@2.0 already
# exists in your tree at:
#   hardware/lge/interfaces/hardware/radio/2.0/{ILgeRadio.hal,...,Android.bp}
# and is already linked by android.hardware.radio@1.4-service.lge. We just
# pull the same module in via shared_libs.
#
# 1. Drop the kicker into your sdm845-common device tree:
#
#      cp -r lge-radio-kicker  device/lge/sdm845-common/lge_radio_kicker
#
#    Then move sepolicy pieces into your existing sepolicy directory
#    (typically device/lge/sdm845-common/sepolicy/vendor):
#
#      mv device/lge/sdm845-common/lge_radio_kicker/sepolicy/lge_radio_kicker.te \
#         device/lge/sdm845-common/sepolicy/vendor/lge_radio_kicker.te
#      mv device/lge/sdm845-common/lge_radio_kicker/sepolicy/file_contexts \
#         device/lge/sdm845-common/sepolicy/vendor/file_contexts.lge_radio_kicker
#      rmdir device/lge/sdm845-common/lge_radio_kicker/sepolicy
#
# 2. Add to PRODUCT_PACKAGES in device/lge/sdm845-common/sdm845.mk:
#
#      PRODUCT_PACKAGES += \
#          lge_radio_kicker
#
# 3. Build:
#
#      m lge_radio_kicker
#      m vendorimage
#      # bootimage if sepolicy moved
#
# 4. Flash and verify:
#
#      adb shell logcat -d | grep lge_radio_kicker
#      # On a DSDS device first iteration prints both instances:
#      #   I lge_radio_kicker: reportPdnThrottleInd(serial=0, enable=false) delivered to lge_radio
#      #   I lge_radio_kicker: reportPdnThrottleInd(serial=0, enable=false) delivered to lge_radio2
#      # then SILENCE — subsequent iterations only log when state changes
#      # (an instance disappears, a transaction starts failing, etc).
#
#      adb shell logcat -d -b radio | grep -E "reportPdnThrottle|SETUP_DATA_CALL"
#      # qcrild logs every kick (one entry per slot every 10s):
#      #   <pid_qcrild>  RILC_EX : reportPdnThrottleInd: serial 0
#      #   <pid_qcrild2> RILC_EX : reportPdnThrottleInd: serial 0
#      # SETUP_DATA_CALL with apn=ims should now return cause=NONE plus non-empty
#      # pcscf=[/...] on EACH PHONE instead of cause=PDN_IPV4_CALL_THROTTLED.
#
#      adb shell ps -A | grep lge_radio_kicker
#      # Should show a running process — if it shows nothing, init considered
#      # the .rc service "stopped" (oneshot leftover); see "Daemon mode" below.
#
#      adb shell lshal | grep ILgeRadio
#      # confirms there are 2 instances (lge_radio + lge_radio2) on a DSDS
#      # build; on a single-SIM build there will be only lge_radio.
#
# DSDS notes
# ----------
# * The kicker tracks per-instance state (kOk / kNotAvailable / kTransactionFailed)
#   and only emits a log line when state CHANGES, so steady-state operation is
#   silent in logcat. The first iteration always prints both instances — that is
#   the boot-time confirmation that everything wired up correctly. On single-SIM
#   builds you will see one INFO line for lge_radio and one WARNING for
#   lge_radio2 ("not available") — that is expected and harmless.
#
# * The throttler in qcrild auto-rethrottles after each modem failure. With
#   the previous oneshot kicker, a single failed attempt (e.g. Beeline RU
#   returning cause=33 SERVICE_OPTION_NOT_SUBSCRIBED on first slot 1 attach)
#   would re-arm the throttler and short-circuit every subsequent retry with
#   PDN_IPV4_CALL_THROTTLED until reboot. With the periodic 10s kicker, the
#   re-armed window is reopened before DRM's next retry can hit it.
#
# * If you build a dedicated single-SIM image, drop "lge_radio2" from the
#   kInstances[] array in the .cpp to silence the per-iteration WARNING about
#   the missing second instance.
#
# Daemon mode
# -----------
# * The .rc has NO `oneshot` keyword — this service must run forever. If you
#   change it to oneshot, the daemon will exit after the first sleep period
#   (or be killed by init) and the rethrottle problem comes back.
#
# * `init.svc.vendor.lge_radio_kicker` should report `running` after boot and
#   `ps -A | grep lge_radio_kicker` should show a live PID. If it shows
#   `stopped`, the binary either crashed (check logcat) or the .rc on device
#   is the old oneshot version (re-flash vendorimage).
#
# Notes
# -----
# * Same Soong namespace caveats as for vss_ims_kicker: device/lge/sdm845-common
#   has its own soong_namespace, hardware/lge has a separate one. By placing the
#   kicker under device/lge/sdm845-common we stay in that namespace, but we DO
#   need to pull a module (vendor.lge.hardware.radio@2.0) out of the hardware/lge
#   namespace. Add to device/lge/sdm845-common/Android.bp the line:
#       imports: ["hardware/lge"]
#   inside the soong_namespace {} block. (Or, since both trees are part of the
#   same overall build, the simpler approach is to drop the explicit
#   soong_namespace blocks entirely and let everything live in the default
#   namespace — but only do that if no other module relies on the isolation.)
#
# * No prebuilt-vs-generated conflict here because hardware/lge/interfaces is
#   already in the tree and your existing build already produces
#   vendor.lge.hardware.radio@2.0.so. Adding the kicker as a consumer simply
#   pulls it into the install set. If your stock blob extract created a
#   prebuilt copy of vendor.lge.hardware.radio@2.0.so under
#   vendor/lge/sdm845-common, you may hit a packaging conflict — comment out
#   that line in proprietary-files.txt the same way we did for vss_ims, and
#   regenerate the auto-generated Android.bp.
#
# * Reverse: drop the PRODUCT_PACKAGES line. The daemon is harmless if it
#   fails to find one of the services (it just logs a warning and keeps
#   looping; the kick is a no-op on the missing instance). To stop it
#   manually at runtime: `adb shell stop vendor.lge_radio_kicker`.
