#!/usr/bin/env -S PYTHONPATH=../../../tools/extract-utils python3
#
# SPDX-FileCopyrightText: 2024 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

from extract_utils.fixups_blob import (
    blob_fixup,
    blob_fixups_user_type,
)
from extract_utils.fixups_lib import (
    lib_fixup_remove,
    lib_fixups,
    lib_fixups_user_type,
)
from extract_utils.main import (
    ExtractUtils,
    ExtractUtilsModule,
)

namespace_imports = [
    "hardware/lge",
    "device/lge/sdm845-common",
    "hardware/qcom-caf/common/libqti-perfd-client",
    "hardware/qcom-caf/sdm845",
    "hardware/qcom-caf/wlan",
    "vendor/qcom/opensource/dataservices",
    "vendor/qcom/opensource/display",
]


def lib_fixup_vendor_suffix(lib: str, partition: str, *args, **kwargs):
    return f'{lib}_{partition}' if partition == 'vendor' else None


lib_fixups: lib_fixups_user_type = {
    **lib_fixups,
    (
        'com.qualcomm.qti.dpm.api@1.0',
        'vendor.qti.hardware.fm@1.0',
        'vendor.qti.imsrtpservice@3.0',
        'vendor.qti.ims.rcsconfig@1.0.so',
        'vendor.qti.ims.rcsconfig@1.1.so',
        'vendor.qti.ims.rcsconfig@2.0.so',
        'vendor.qti.ims.rcsconfig@2.1.so',
        'com.qualcomm.qti.imscmservice@1.0.so',
        'com.qualcomm.qti.imscmservice@2.0.so',
        'com.qualcomm.qti.imscmservice@2.1.so',
        'com.qualcomm.qti.imscmservice@2.2.so',
        'vendor.lge.hardware.vss_ims@1.0.so;',
        'vendor.qti.hardware.radio.ims@1.0.so',
        'vendor.qti.hardware.radio.ims@1.1.so',
        'vendor.qti.hardware.radio.ims@1.2.so',
        'vendor.qti.hardware.radio.ims@1.3.so',
        'vendor.qti.hardware.radio.ims@1.4.so',
        'vendor.qti.hardware.radio.ims@1.5.so',
        'vendor.qti.hardware.radio.ims@1.6.so',
        'vendor.qti.hardware.radio.ims@1.7.so',
        'vendor.qti.ims.callcapability@1.0.so',
        'vendor.qti.ims.callinfo@1.0.so',
        'vendor.qti.ims.factory@1.0.so',
        'vendor.qti.ims.factory@1.1.so'
    ): lib_fixup_vendor_suffix
}


blob_fixups: blob_fixups_user_type = {
    'vendor/etc/init/vendor.sensors.sscrpcd.rc': blob_fixup()
        .regex_replace('class early_hal', 'class core'),
    'vendor/lib64/libwvhidl.so': blob_fixup()
        .add_needed('libcrypto_shim.so'),
    'system_ext/lib64/libimsmmpf.lge.so': blob_fixup()
        .add_needed('libui_shim.so'),
    'system_ext/lib64/libims.lge.so': blob_fixup()
        .replace_needed('libutils.so', 'libutils-v32.so'),
}  # fmt: skip

module = ExtractUtilsModule(
    'sdm845-common',
    'lge',
    blob_fixups=blob_fixups,
    lib_fixups=lib_fixups,
    namespace_imports=namespace_imports,
)

if __name__ == '__main__':
    utils = ExtractUtils.device(module)
    utils.run()
