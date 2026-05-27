# ImsNetworkRequester integration for caymanslm
#
# A small system app that does two things at boot and on every active
# subscription change:
#
#   1. Issues ConnectivityManager.requestNetwork() with NET_CAPABILITY_IMS
#      bound to a TelephonyNetworkSpecifier per active subscription, so
#      AOSP DataNetworkController brings up the apn=ims PDN. Without this
#      active request, no IMS PDN is ever set up, no P-CSCF arrives via
#      PCO, and the rest of the LG IMS stack cannot start.
#
#   2. Seeds an apn=ims/type=ims row into telephony.db for every active
#      subscription whose carrier has no such row. This works around an
#      AOSP DataProfileManager race in DSDS where the synthetic "DEFAULT
#      IMS" profile is gated on mSimState==SIM_STATE_LOADED, but mSimState
#      is updated via this::post asynchronously and loses the race against
#      the synchronous onCarrierConfigUpdated → updateDataProfiles() call
#      on the second slot. The carrier whose loose race wins ends up with
#      no IMS profile at all, fails with NO_SUITABLE_DATA_PROFILE forever.
#      Inserted rows get edited=4 (CARRIER_EDITED) and survive reboots
#      and apns-conf.xml reloads.
#
#      The seeder also UPGRADES existing IMS rows whose protocol is not
#      IPV4V6 — observed that pre-existing apns-conf.xml entries with
#      protocol=IP (IPv4-only) cause DPM to mark the IMS profile permanently
#      failed because the carrier (e.g. Beeline RU) refuses IPv4-only PDN
#      and serves IMS over IPv6 only.
#
# Replaces what stock LG firmware does from
# com.lge.lgdataphone.dataconnection.ApnManager — a class in the LG-specific
# framework jar that LineageOS does not import.
#
# Why not just use vss_ims_kicker / lge_radio_kicker?
# - Both kickers handle the *modem-facing* side (LGE QMI handshake + qcrild
#   throttle disable). Verified via logcat that those steps complete OK.
# - The remaining gap is on the *Java/framework* side: nobody asks
#   ConnectivityService to bring up an IMS network. Stock LG ApnManager
#   does. AOSP framework does not.
# - The seeder piece additionally fills the data-profile gap that AOSP
#   only papers over with a race-prone synthetic profile.
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
#      # boot:
#      #   I ImsNetReq: BootReceiver received: android.intent.action.BOOT_COMPLETED
#      #   I ImsNetReq: Service onCreate
#      #   I ImsNetReq: Service onStartCommand
#      #
#      # On a fresh sub whose carrier has no ims-typed APN row:
#      #   I ImsNetReq: [sub=N] inserted IMS APN for <plmn> -> content://...
#      #
#      # On a sub whose carrier already has an ims row with the right protocol:
#      #   I ImsNetReq: [sub=N] IMS APN already present and IPV4V6 for <plmn>
#      #
#      # On a sub whose carrier has an ims row with protocol=IP (IPv4-only):
#      #   I ImsNetReq: [sub=N] upgraded IMS APN protocol IP/IP->IPV4V6 for <plmn> (id=..., updated=1)
#      #
#      # Per-subscription request and bring-up:
#      #   I ImsNetReq: [sub=N] requestNetwork(IMS) issued
#      #   I ImsNetReq: [sub=N] IMS network onAvailable: ...
#      #   I ImsNetReq: [sub=N] linkProperties: ... PcscfAddresses: [/...]
#
#      adb shell logcat -b radio | grep "SETUP_DATA_CALL"
#      # expect first apn=ims call per active phone:
#      #   < SETUP_DATA_CALL DataCallResponse: cause=0 ... pcscf=[...]
#
#      adb shell logcat | grep -iE "AoSReg|SendREGISTER"
#      # expect:
#      #   AoSRegistration::Start
#      #   AoSRegistration::SendREGISTER
#
#      adb shell dumpsys phone | grep -E "Voice: true|MMTEL.*READY"
#      # expect MMTel state=READY with Voice: true
#
# DSDS / hot-swap behaviour
# -------------------------
# * The service registers SubscriptionManager.OnSubscriptionsChangedListener
#   and re-runs syncRequestsToActiveSubs() on every change. This handles:
#     - Cold boot with N SIMs already inserted
#     - SIM hot-swap (subId disappears for the removed SIM, fresh subId
#       appears for the new ICCID — old NetworkRequest is unregistered,
#       new one is issued, IMS APN is seeded for the new carrier)
#     - DSDS where the second slot loses the AOSP DataProfileManager race
#
# * The persistent app + START_STICKY service is required for the listener
#   to keep working past boot. android:persistent="true" on the application
#   in AndroidManifest.xml prevents force-stop from tearing it down.
#
# Permissions
# -----------
# WRITE_APN_SETTINGS is required for the seeder. The privapp permission
# whitelist (permissions_com.lineageos.imsnetworkrequester.xml) grants it
# alongside the existing CONNECTIVITY_INTERNAL,
# CONNECTIVITY_USE_RESTRICTED_NETWORKS and NETWORK_SETTINGS that the
# requestNetwork side needs.
#
# The app is signed with the platform certificate (uid=system) so it gets
# CONNECTIVITY_USE_RESTRICTED_NETWORKS without per-app prompting. IMS
# networks are flagged restricted (no NOT_RESTRICTED capability), so this
# permission is mandatory.
#
# Notes
# -----
# * If you ever want VoLTE only on a specific slot, change
#   `syncRequestsToActiveSubs()` to filter by SubscriptionInfo.getSlotIndex()
#   before issuing the request.
#
# * Inserted IMS rows survive apns-conf.xml updates because their
#   `edited` field is set to CARRIER_EDITED (=4). If you ever need to wipe
#   them, use:
#     adb shell content delete --uri content://telephony/carriers \
#         --where 'name="IMS (auto)"'
#
# * Reverse: drop the PRODUCT_PACKAGES line. The app simply isn't installed,
#   and any IMS rows it previously inserted stay in telephony.db (harmless
#   — they just match what AOSP would synthesise on its own).
#
# Known limitations
# -----------------
# * The seeder cannot fix slot 1 IMS bring-up when qcrild2's PDN throttler
#   re-throttles after each modem failure (e.g. Beeline RU returning
#   SERVICE_OPTION_NOT_SUBSCRIBED first). That requires either a periodic
#   reportPdnThrottleInd from lge_radio_kicker or an OEM-specific RIL
#   knob we have not identified yet — see
#   memory/todo_beeline_slot1_ims_subscribed.md.
