package com.aura.os.binder

import android.content.Context
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Process
import java.security.MessageDigest

/**
 * Platform-only implementation, requiring an AOSP platform build with suitable APIs.
 * Not included in framework Android.bp and not registered as a Binder service.
 */
class AndroidBinderPlatform(private val context: Context) : AuraBinderIdentityResolver.Platform {
    override fun callerUid(): Int = Binder.getCallingUid()
    override fun callerPid(): Int = Binder.getCallingPid()
    override fun servicePid(): Int = Process.myPid()
    // AOSP's per-user UID partition is 100000. Unlike UserHandle.getUserId,
    // the arithmetic works with the public SDK used for isolated device tests.
    // This is an AOSP-specific contract; revisit if the platform UID model changes.
    override fun userIdForUid(uid: Int): Int {
        require(uid >= 0) { "Invalid Binder UID" }
        return uid / 100_000
    }
    override fun hasCallingPermission(permission: String): Boolean =
        context.checkCallingPermission(permission) == PackageManager.PERMISSION_GRANTED
    override fun packagesForUid(uid: Int): List<String> =
        context.packageManager.getPackagesForUid(uid)?.toList() ?: emptyList()

    override fun currentSigningDigests(packageName: String): Set<String> {
        val info = context.packageManager.getPackageInfo(
            packageName, PackageManager.GET_SIGNING_CERTIFICATES
        )
        val signatures = info.signingInfo?.apkContentsSigners ?: return emptySet()
        return signatures.map { sig ->
            MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }.toSet()
    }
}
