# lge_radio_kicker integration steps for caymanslm
#
# This daemon issues exactly one HIDL call at boot:
#   ILgeRadio::reportPdnThrottleInd(serial=0, enable=false)
# against vendor.lge.hardware.radio@2.0::ILgeRadio/lge_radio served by
# qcrild. That call disables qcrild's local PDN-failure throttle, which is
# the OEM-specific knob stock LG framework flips immediately after the LGE
# QMI handshake completes. Without it, qcrild caches the very first
# SERVICE_OPTION_NOT_SUBSCRIBED reply from the modem and refuses every
# subsequent SETUP_DATA_CALL apn=ims instantly, so VoLTE bring-up never
# starts.
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
#      # expect: "reportPdnThrottleInd(serial=0, enable=false) delivered to lge_radio"
#
#      adb shell logcat -b radio -d | grep -E "reportPdnThrottle|SETUP_DATA_CALL"
#      # expect: "RILC_EX: reportPdnThrottleInd: serial 0" from qcrild,
#      #         then SETUP_DATA_CALL with apn=ims returning cause=NONE plus
#      #         non-empty pcscf=[/...] in the response.
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
# * Reverse: drop the PRODUCT_PACKAGES line. The kicker is harmless if it
#   fails to find the service (it just exits with rc=1 after 2 minutes of
#   retries) — leaving the rest of the system untouched.
