# ImsNetworkRequester integration for caymanslm
#
# Tiny system app that issues ConnectivityManager.requestNetwork() with
# NET_CAPABILITY_IMS on every active subscription at boot. Replaces the
# missing call from com.lge.lgdataphone.dataconnection.ApnManager (LG
# framework jar that LineageOS does not import). Without this active
# request, AOSP DataNetworkController never sets up the apn=ims PDN, no
# P-CSCF arrives via PCO, and IMS registration cannot start.
#
# Why not just use vss_ims_kicker / lge_radio_kicker?
# - Both kickers handle the *modem-facing* side (LGE QMI handshake + qcrild
#   throttle disable). Verified via logcat that those steps complete OK.
# - The remaining gap is on the *Java/framework* side: nobody asks
#   ConnectivityService to bring up an IMS network. Stock LG ApnManager
#   does. AOSP framework does not.
#
# Integration steps in your build tree
# ------------------------------------
# 1. Drop the project into your sdm845-common device tree:
#
#      cp -r ims-network-requester  device/lge/sdm845-common/ImsNetworkRequester
#
# 2. Add to PRODUCT_PACKAGES in device/lge/sdm845-common/sdm845.mk:
#
#      PRODUCT_PACKAGES += \
#          ImsNetworkRequester
#
#    (privapp_whitelist_com.lineageos.imsnetworkrequester is pulled in
#    automatically via `required`.)
#
# 3. Build:
#      m ImsNetworkRequester
#      m systemextimage   # signature is platform; lands in /system_ext/priv-app/
#
# 4. Flash. Verify:
#
#      adb shell logcat | grep ImsNetReq
#      # expect on boot:
#      #   I ImsNetReq: BootReceiver received: android.intent.action.BOOT_COMPLETED
#      #   I ImsNetReq: Service onCreate
#      #   I ImsNetReq: Service onStartCommand
#      #   I ImsNetReq: [sub=1] requestNetwork(IMS) issued
#      # then once IMS PDN comes up:
#      #   I ImsNetReq: [sub=1] IMS network onAvailable: 121
#      #   I ImsNetReq: [sub=1] linkProperties: ... PcscfAddresses: [/10.32.6.190,/10.32.246.190]
#
#      adb shell logcat -b radio | grep "SETUP_DATA_CALL"
#      # expect first apn=ims call:
#      #   < SETUP_DATA_CALL DataCallResponse: cause=0 ... pcscf=[/10.32.246.190, /10.32.6.190]
#
#      adb shell logcat | grep -iE "AoSReg|SendREGISTER"
#      # expect:
#      #   AoSRegistration::Start
#      #   AoSRegistration::SendREGISTER
#
#      adb shell dumpsys phone | grep -E "Voice: true|MMTEL.*READY"
#      # expect MMTel state=READY with Voice: true
#
# Notes
# -----
# * The app is signed with the platform certificate (uid=system) so it gets
#   CONNECTIVITY_USE_RESTRICTED_NETWORKS without per-app prompting. IMS
#   networks are flagged restricted (no NOT_RESTRICTED capability), so this
#   permission is mandatory.
#
# * Service is android:persistent="true" + START_STICKY so init keeps it
#   alive. If killed, the request is reissued on next start. Callbacks live
#   for the lifetime of the process — never explicitly unregistered (only
#   on Service.onDestroy).
#
# * If you ever want VoLTE only on a specific slot, change
#   `requestImsForAllActiveSubs()` to filter by SubscriptionInfo.getSlotIndex()
#   before issuing the request. Not needed for caymanslm (slot 0 is the only
#   one with a SIM in normal use).
#
# * Reverse: drop the PRODUCT_PACKAGES line. The app simply isn't installed,
#   nothing else changes.
