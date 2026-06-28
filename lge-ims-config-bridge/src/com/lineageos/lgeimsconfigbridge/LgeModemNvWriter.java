// SPDX-FileCopyrightText: 2026 LineageOS — caymanslm port
// SPDX-License-Identifier: Apache-2.0
//
// LgeModemNvWriter — pushes IMS-related NV items into the modem through
// the stock LG QcRilHook OEM_HOOK channel.
//
// Why this exists:
//   On stock LG firmware, HiddenMenu/ImsRadioConfigManager calls into
//   the same Qualcomm OEM hook that we're hitting here to enable VoLTE
//   in modem NV (the modemst1/modemst2 partitions). That write SURVIVES
//   factory-reset and OS reflash because it lives in modem-side
//   persistent storage. Without it the modem never advertises VoPS=on
//   or accepts the apn=ims dedicated bearer the way it should.
//
// Why we bypass LgSvcCmd:
//   The stock lgsvcitems.jar wraps QcRilHook through
//   TelephonyManager.invokeOemRilRequestRaw, which AOSP removed in
//   Android 15 (the call now throws NoSuchMethodError). LineageOS no
//   longer exposes ITelephony.invokeOemRilRequestRaw either. We bypass
//   both by binding to com.qualcomm.qcrilmsgtunnel.QcrilMsgTunnelService
//   directly and calling its IQcrilMsgTunnel.sendOemRilRequestRaw AIDL
//   method, which is exactly what the stock framework path eventually
//   reached anyway. The wire format on the request byte[] is the same
//   QOEMHOOK frame stock LgSvcItems built; we replicate it verbatim:
//
//     [8B "QOEMHOOK"]
//     [4B LE: requestId = QCRILHOOK_NEW_CMD_SET = 593943]
//     [4B LE: payloadSize = 4]
//     [4B LE: itemId]
//     [4B LE: valueLen]
//     [N B: UTF-8 value]
//
// qcrild's VssCommonModule routes this to qcci_qmi_lge_nv_send_sync
// (libvss_nv_iface.so), which talks to the modem's lge_nv QMI service.
// The modem persists the NV item.
//
// Dependencies:
//   <uses-library com.qualcomm.qcrilhook> — provides IQcrilMsgTunnel
//   AIDL stubs.

package com.lineageos.lgeimsconfigbridge;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import com.qualcomm.qcrilmsgtunnel.IQcrilMsgTunnel;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public final class LgeModemNvWriter {
    private static final String TAG = "LgeModemNv";

    private static final String TUNNEL_PKG = "com.qualcomm.qcrilmsgtunnel";
    private static final String TUNNEL_CLS = "com.qualcomm.qcrilmsgtunnel.QcrilMsgTunnelService";

    // Stock LgSvcItems request IDs (lgsvcitems.LgSvcReqId):
    //   QCRILHOOK_CMD_SET     = 593925 — legacy: write a CMD_* item
    //   QCRILHOOK_CMD_GET     = 593924 — legacy: read a CMD_* item
    //   QCRILHOOK_NEW_CMD_SET = 593943 — newer variant
    //   QCRILHOOK_NEW_CMD_GET = 593942 — newer variant
    //
    // Our extracted qcrild only registers handlers for the legacy IDs
    // (QCRILHOOK_CMD_GET/SET/QUERY/SVC_READ/SVC_WRITE/OPRT_MODE) — the
    // NEW_* variants log nothing and time out at the binder layer. The
    // newer IDs were presumably introduced in a later qcrild that
    // shipped with a different device. We therefore stick to the
    // legacy request IDs, which is what stock LG firmware on this
    // hardware would have negotiated to as well.
    private static final int QCRILHOOK_CMD_SET = 593925;
    private static final int QCRILHOOK_CMD_GET = 593924;

    // Stock LG modem-NV item IDs (lgsvcitems.LgSvcCmdIds /
    // Ims6.com.lge.ims.volte.hidden.ImsSvcCmd.Ids).
    public static final int CMD_IMS_VLT    = 7578;
    public static final int CMD_IMS_VOWIFI = 7580;

    private static final byte[] OEM_IDENTIFIER = "QOEMHOOK".getBytes();
    private static final int    HEADER_SIZE   = OEM_IDENTIFIER.length + 8;
    private static final int    RESPONSE_SIZE = 2048;
    private static final int    DEFAULT_PHONE = 0;

    private static final long BIND_TIMEOUT_MS = 30000;
    /**
     * Hard ceiling for an OEM_HOOK round-trip. Real ones complete in
     * tens of milliseconds; if qcrild has no handler for our reqId or
     * is wedged on the modem side, the binder call would block forever.
     */
    private static final long TRANSACT_TIMEOUT_MS = 5000;

    private final Context mContext;
    private final AtomicReference<IQcrilMsgTunnel> mTunnel = new AtomicReference<>();
    private CountDownLatch mReadyLatch = new CountDownLatch(1);
    private boolean mBindRequested;
    /**
     * Worker that runs the binder transact on a thread we control, so
     * an unresponsive qcrild can't pin our caller forever — we fall
     * back via Future.get(timeout) and report the time-out.
     */
    private final ExecutorService mTransactExecutor = Executors.newSingleThreadExecutor();

    private final ServiceConnection mConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, "QcrilMsgTunnel connected");
            mTunnel.set(IQcrilMsgTunnel.Stub.asInterface(service));
            mReadyLatch.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.w(TAG, "QcrilMsgTunnel disconnected");
            mTunnel.set(null);
            // Replace the latch so the next setItem() can wait again
            // when the service comes back up.
            mReadyLatch = new CountDownLatch(1);
        }
    };

    public LgeModemNvWriter(Context context) {
        mContext = context.getApplicationContext();
    }

    /**
     * Apply a VoLTE on/off decision to modem NV. Idempotent — writing
     * the same value back is a no-op for the modem so callers don't
     * need to track previous state externally.
     *
     * @return true if the write was issued (modem accepted it),
     *         false if the QcRil tunnel never came up.
     */
    public boolean setVoLteEnabled(boolean enabled) {
        return setItem(CMD_IMS_VLT, enabled ? "1" : "0");
    }

    public boolean setVoWiFiEnabled(boolean enabled) {
        return setItem(CMD_IMS_VOWIFI, enabled ? "1" : "0");
    }

    /** Write a CMD_* item to modem NV. */
    public boolean setItem(int cmdId, String value) {
        IQcrilMsgTunnel svc = waitForTunnel();
        if (svc == null) {
            Log.w(TAG, "QcrilMsgTunnel not ready; skipping cmd=" + cmdId);
            return false;
        }
        byte[] valueBytes = value.getBytes();
        byte[] request = new byte[HEADER_SIZE + 8 + valueBytes.length];
        ByteBuffer buf = ByteBuffer.wrap(request).order(ByteOrder.nativeOrder());
        buf.put(OEM_IDENTIFIER);
        buf.putInt(QCRILHOOK_CMD_SET);
        buf.putInt(4);                  // matches stock LgSvcItems.setCommands
        buf.putInt(cmdId);
        buf.putInt(valueBytes.length);
        buf.put(valueBytes);

        byte[] response = new byte[RESPONSE_SIZE];
        Integer rc = transactWithTimeout(svc, request, response, DEFAULT_PHONE,
                "setCmdValue " + cmdId);
        if (rc == null) return false;
        Log.i(TAG, "setCmdValue(" + cmdId + ", \"" + value + "\") rc=" + rc);
        return rc >= 0;
    }

    /** Read a CMD_* item back. Useful for verifying what the modem holds. */
    public String getItem(int cmdId) {
        return getItem(cmdId, DEFAULT_PHONE);
    }

    /** Same, but on a caller-chosen phone slot. */
    public String getItem(int cmdId, int phoneId) {
        IQcrilMsgTunnel svc = waitForTunnel();
        if (svc == null) return null;
        byte[] request = new byte[HEADER_SIZE + 4];
        ByteBuffer buf = ByteBuffer.wrap(request).order(ByteOrder.nativeOrder());
        buf.put(OEM_IDENTIFIER);
        buf.putInt(QCRILHOOK_CMD_GET);
        buf.putInt(4);
        buf.putInt(cmdId);

        byte[] response = new byte[RESPONSE_SIZE];
        Integer rc = transactWithTimeout(svc, request, response, phoneId,
                "getCmdValue cmd=" + cmdId + " phone=" + phoneId);
        if (rc == null || rc < 0) return null;
        ByteBuffer rb = ByteBuffer.wrap(response).order(ByteOrder.nativeOrder());
        int len = rb.getInt();
        if (len <= 0 || len > rb.remaining()) return null;
        byte[] out = new byte[len];
        rb.get(out);
        return new String(out);
    }

    /**
     * Run sendOemRilRequestRaw on a worker so we can bound it with a
     * timeout. qcrild has no per-OEM-hook deadline of its own — if it
     * doesn't recognise our reqId or the modem is wedged, the binder
     * call would otherwise block the caller indefinitely.
     */
    private Integer transactWithTimeout(IQcrilMsgTunnel svc, byte[] request,
                                        byte[] response, int phoneId, String label) {
        Future<Integer> fut = mTransactExecutor.submit(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                return svc.sendOemRilRequestRaw(request, response, phoneId);
            }
        });
        try {
            return fut.get(TRANSACT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            Log.w(TAG, label + " transact timed out after " + TRANSACT_TIMEOUT_MS + "ms");
            fut.cancel(true);
            return null;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException ee) {
            Log.w(TAG, label + " transact threw: " + ee.getCause());
            return null;
        }
    }

    /**
     * Bind to QcrilMsgTunnelService on first call, then wait for the
     * onServiceConnected callback. Subsequent calls return the cached
     * binder immediately. Caller must NOT be the main thread because
     * the ServiceConnection callback is dispatched on the main thread —
     * blocking the main thread to await this latch deadlocks the bind.
     */
    private IQcrilMsgTunnel waitForTunnel() {
        IQcrilMsgTunnel cached = mTunnel.get();
        if (cached != null) return cached;
        synchronized (this) {
            if (!mBindRequested) {
                mBindRequested = true;
                Intent intent = new Intent();
                intent.setClassName(TUNNEL_PKG, TUNNEL_CLS);
                mContext.startService(intent);
                boolean bound = mContext.bindService(intent, mConn, Context.BIND_AUTO_CREATE);
                Log.i(TAG, "bindService(QcrilMsgTunnel) returned " + bound);
                if (!bound) {
                    mBindRequested = false;
                    return null;
                }
            }
        }
        try {
            if (!mReadyLatch.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "QcrilMsgTunnel bind timed out after " + BIND_TIMEOUT_MS + "ms");
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        return mTunnel.get();
    }
}
