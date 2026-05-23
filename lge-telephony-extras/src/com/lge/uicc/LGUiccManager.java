// SPDX-FileCopyrightText: 2018 LG Electronics — original implementation
// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
//
// Restored from decompiled stock G910EMW30l framework.jar.
// Used by Ims6.apk (com.lge.ims) — calls only getProperty() and readIccRecordToString().
// The "lguicc" binder service is registered by stock vendor.lge.hardware.radio HAL service.
// On a LineageOS build that service is not present; getUiccService() returns null,
// and every public method here returns null/-1/false. Ims6 handles those negative results.

package com.lge.uicc;

import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.Rlog;

public class LGUiccManager {
    private static final String TAG = "LGUICC";

    public static int activateUiccCard(int slotId) {
        Parcel p = Parcel.obtain();
        p.writeInt(2);
        p.writeInt(slotId);
        try {
            ILGUiccService svc = getUiccService();
            if (svc != null) {
                byte[] resp = svc.request("extphone", p.marshall(), null);
                if (resp != null) {
                    p.unmarshall(resp, 0, resp.length);
                    p.setDataPosition(0);
                    return p.readInt();
                }
            }
        } catch (RemoteException | NullPointerException e) {
            loge("activateUiccCard: " + e);
        }
        return -1;
    }

    public static int deactivateUiccCard(int slotId) {
        Parcel p = Parcel.obtain();
        p.writeInt(3);
        p.writeInt(slotId);
        try {
            ILGUiccService svc = getUiccService();
            if (svc != null) {
                byte[] resp = svc.request("extphone", p.marshall(), null);
                if (resp != null) {
                    p.unmarshall(resp, 0, resp.length);
                    p.setDataPosition(0);
                    return p.readInt();
                }
            }
        } catch (RemoteException | NullPointerException e) {
            loge("deactivateUiccCard: " + e);
        }
        return -1;
    }

    public static byte[] genericIO(String tag, byte[] payload) throws RemoteException {
        try {
            ILGUiccService svc = getUiccService();
            if (svc == null) {
                return null;
            }
            return svc.request(tag, payload, null);
        } catch (RemoteException | NullPointerException e) {
            loge("genericIO: " + e);
            return null;
        }
    }

    public static int getCurrentUiccCardProvisioningStatus(int slotId) {
        Parcel p = Parcel.obtain();
        p.writeInt(1);
        p.writeInt(slotId);
        try {
            ILGUiccService svc = getUiccService();
            if (svc != null) {
                byte[] resp = svc.request("extphone", p.marshall(), null);
                if (resp != null) {
                    p.unmarshall(resp, 0, resp.length);
                    p.setDataPosition(0);
                    return p.readInt();
                }
            }
        } catch (RemoteException | NullPointerException e) {
            loge("getCurrentUiccCardProvisioningStatus: " + e);
        }
        return -1;
    }

    public static String getProperty(String key, int slotId, String defaultValue) {
        try {
            ILGUiccService svc = getUiccService();
            if (svc == null) {
                return defaultValue;
            }
            String v = svc.getProperty(key, slotId);
            if (v != null && !v.isEmpty()) {
                return v;
            }
            logd("getProperty: not ready: key=" + key + ", slot=" + slotId);
        } catch (RemoteException | NullPointerException e) {
            loge("getProperty: " + e);
        }
        return defaultValue;
    }

    public static String getProperty(String key, String defaultValue) {
        return getProperty(key, 0, defaultValue);
    }

    static ILGUiccService getUiccService() {
        ILGUiccService svc = ILGUiccService.Stub.asInterface(ServiceManager.getService("lguicc"));
        if (svc == null) {
            loge("service is not ready");
        }
        return svc;
    }

    public static byte[] readIccRecord(int slotId) {
        return readIccRecord(0, slotId);
    }

    public static byte[] readIccRecord(int recordType, int slotId) {
        Parcel p = Parcel.obtain();
        p.writeInt(recordType);
        p.writeInt(slotId);
        try {
            ILGUiccService svc = getUiccService();
            if (svc != null) {
                byte[] resp = svc.request("IccFileIO.read", p.marshall(), null);
                if (resp != null) {
                    p.unmarshall(resp, 0, resp.length);
                    p.setDataPosition(0);
                    return p.createByteArray();
                }
            }
        } catch (RemoteException | NullPointerException e) {
            loge("readIccRecord: " + e);
        }
        return null;
    }

    public static String readIccRecordToString(int slotId) {
        Parcel p = Parcel.obtain();
        p.writeInt(0);
        p.writeInt(slotId);
        try {
            ILGUiccService svc = getUiccService();
            if (svc != null) {
                byte[] resp = svc.request("IccFileIO.read", p.marshall(), null);
                if (resp != null) {
                    p.unmarshall(resp, 0, resp.length);
                    p.setDataPosition(0);
                    p.createByteArray();
                    return p.readString();
                }
            }
        } catch (RemoteException | NullPointerException e) {
            loge("readIccRecord: " + e);
        }
        return null;
    }

    public static String[] readIccRecordAllToString(int slotId) {
        Parcel p = Parcel.obtain();
        p.writeInt(0);
        p.writeInt(slotId);
        try {
            ILGUiccService svc = getUiccService();
            if (svc != null) {
                byte[] resp = svc.request("IccFileIO.read", p.marshall(), null);
                if (resp != null) {
                    p.unmarshall(resp, 0, resp.length);
                    p.setDataPosition(0);
                    p.createByteArray();
                    p.readString();
                    return p.createStringArray();
                }
            }
        } catch (RemoteException | NullPointerException e) {
            loge("readIccRecordAllToString: " + e);
        }
        return null;
    }

    public static String requestEnvelope(String tag, String envelope) {
        Parcel p = Parcel.obtain();
        p.writeString(envelope);
        try {
            ILGUiccService svc = getUiccService();
            if (svc == null) {
                return null;
            }
            byte[] resp = svc.request(tag, p.marshall(), null);
            if (resp == null) {
                return "FAIL";
            }
            p.unmarshall(resp, 0, resp.length);
            p.setDataPosition(0);
            return p.readString();
        } catch (RemoteException | NullPointerException e) {
            loge("requestEnvelope: " + e);
            return "FAIL";
        }
    }

    public static boolean setProperty(String key, int slotId, String value) {
        try {
            ILGUiccService svc = getUiccService();
            if (svc == null) {
                return false;
            }
            return svc.setProperty(key, slotId, value);
        } catch (RemoteException | NullPointerException e) {
            loge("setProperty: " + e);
            return false;
        }
    }

    public static boolean setProperty(String key, String value) {
        return setProperty(key, 0, value);
    }

    public static boolean updateIccRecord(int slotId, byte[] data) {
        return updateIccRecord(0, slotId, data);
    }

    public static boolean updateIccRecord(int recordType, int slotId, byte[] data) {
        Parcel p = Parcel.obtain();
        p.writeInt(recordType);
        p.writeInt(slotId);
        p.writeByteArray(data);
        p.writeString(null);
        try {
            ILGUiccService svc = getUiccService();
            if (svc != null) {
                byte[] resp = svc.request("IccFileIO.update", p.marshall(), null);
                if (resp != null) {
                    p.unmarshall(resp, 0, resp.length);
                    p.setDataPosition(0);
                    return p.readInt() == 1;
                }
            }
        } catch (RemoteException | NullPointerException e) {
            loge("updateIccRecord: " + e);
        }
        return false;
    }

    public static boolean updateIccRecordFromString(int slotId, String value) {
        Parcel p = Parcel.obtain();
        p.writeInt(0);
        p.writeInt(slotId);
        p.writeByteArray(null);
        p.writeString(value);
        try {
            ILGUiccService svc = getUiccService();
            if (svc != null) {
                byte[] resp = svc.request("IccFileIO.update", p.marshall(), null);
                if (resp != null) {
                    p.unmarshall(resp, 0, resp.length);
                    p.setDataPosition(0);
                    return p.readInt() == 1;
                }
            }
        } catch (RemoteException | NullPointerException e) {
            loge("updateIccRecordFromString: " + e);
        }
        return false;
    }

    static void logd(String s) {
        Rlog.d(TAG, "[LGUiccManager] " + s);
    }

    static void loge(String s) {
        Rlog.e(TAG, "[LGUiccManager] " + s);
    }
}
