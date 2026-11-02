package com.aura.os.binderprobe

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import com.aura.os.binder.AndroidBinderPlatform
import com.aura.os.binder.AuraBinderIdentityResolver

/**
 * Test-only remote-process endpoint, deliberately not a SystemServer service.
 * The signature digest is derived from THIS installed test APK only, never from IPC.
 */
class IdentityProbeService : Service() {
    override fun onBind(intent: Intent?): IBinder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != IBinder.FIRST_CALL_TRANSACTION) {
                return super.onTransact(code, data, reply, flags)
            }
            data.enforceInterface("com.aura.os.binderprobe.IDENTITY")
            data.readString() // Attacker-controlled claimed package; intentionally ignored.
            data.enforceNoDataAvail()

            // All checks happen INSIDE the live Binder transaction, before identity clearing.
            val platform = AndroidBinderPlatform(this@IdentityProbeService)
            val pkg = packageName
            val expectedSigners = platform.currentSigningDigests(pkg)
            val resolver = AuraBinderIdentityResolver(
                platform,
                mapOf(pkg to expectedSigners),
                mapOf(pkg to setOf("probe.echo")),
                { 1L }
            )
            val caller = try {
                resolver.resolve()
            } catch (failure: IllegalArgumentException) {
                throw SecurityException("Binder probe caller rejected", failure)
            } catch (failure: IllegalStateException) {
                throw SecurityException("Binder probe caller rejected", failure)
            }
            reply?.writeNoException()
            reply?.writeInt(caller.uid)
            reply?.writeInt(Process.myPid())
            return true
        }
    }
}
