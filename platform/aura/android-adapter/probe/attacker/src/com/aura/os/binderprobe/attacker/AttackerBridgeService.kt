package com.aura.os.binderprobe.attacker

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** An independent APK makes a real Binder IPC request with an untrusted UID. */
class AttackerBridgeService : Service() {
    override fun onBind(intent: Intent?): IBinder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != IBinder.FIRST_CALL_TRANSACTION) return super.onTransact(code, data, reply, flags)
            data.enforceInterface("com.aura.os.binderprobe.ATTACKER")
            data.enforceNoDataAvail()

            val latch = CountDownLatch(1)
            var target: IBinder? = null
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    target = service
                    latch.countDown()
                }
                override fun onServiceDisconnected(name: ComponentName) = Unit
            }
            val intent = Intent().setComponent(
                ComponentName("com.aura.os.binderprobe",
                    "com.aura.os.binderprobe.IdentityProbeService")
            )
            var verdict = 2 // Inconclusive: connection or non-security failure.
            val bound = bindService(intent, connection, Context.BIND_AUTO_CREATE)
            try {
                if (bound && latch.await(10, TimeUnit.SECONDS)) {
                    val request = Parcel.obtain()
                    val response = Parcel.obtain()
                    try {
                        request.writeInterfaceToken("com.aura.os.binderprobe.IDENTITY")
                        request.writeString("com.aura.os.binderprobe") // Forged trusted package.
                        if (target!!.transact(IBinder.FIRST_CALL_TRANSACTION, request, response, 0)) {
                            try {
                                response.readException()
                                verdict = 0 // Security bypass: must fail the instrumentation test.
                            } catch (_: SecurityException) {
                                verdict = 1 // Correct denial from remote service.
                            }
                        }
                    } finally {
                        request.recycle()
                        response.recycle()
                    }
                }
            } finally {
                if (bound) unbindService(connection)
            }
            reply?.writeNoException()
            reply?.writeInt(verdict)
            reply?.writeInt(Process.myUid())
            return true
        }
    }
}
