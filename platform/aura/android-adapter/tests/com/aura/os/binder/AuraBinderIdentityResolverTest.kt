package com.aura.os.binder

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumentation tests for the resolver using an injected, deterministic platform boundary.
 * Real multi-process Binder denial tests are still required before SystemServer integration.
 */
@RunWith(AndroidJUnit4::class)
class AuraBinderIdentityResolverTest {
    private val digest = "a".repeat(64)
    private class FakePlatform(
        var uid: Int = 10001, var pid: Int = 20001, var ownPid: Int = 10,
        var userId: Int = 0, var permission: Boolean = true,
        var packages: List<String> = listOf("com.aura.trusted"),
        var signatures: Set<String> = setOf("a".repeat(64))
    ) : AuraBinderIdentityResolver.Platform {
        override fun callerUid() = uid
        override fun callerPid() = pid
        override fun servicePid() = ownPid
        override fun userIdForUid(uid: Int) = userId
        override fun hasCallingPermission(permission: String) = this.permission
        override fun packagesForUid(uid: Int) = packages
        override fun currentSigningDigests(packageName: String) = signatures
    }
    private fun resolver(fake: FakePlatform, version: Long = 1) =
        AuraBinderIdentityResolver(fake, mapOf("com.aura.trusted" to setOf(digest)),
            mapOf("com.aura.trusted" to setOf("read.settings")), { version })

    private fun rejects(fake: FakePlatform, version: Long = 1) {
        assertThrows(IllegalStateException::class.java) { resolver(fake, version).resolve() }
    }

    @Test fun trustedRemoteCallerAccepted() {
        val identity = resolver(FakePlatform()).resolve()
        assertEquals(10001, identity.uid)
        assertEquals(setOf("read.settings"), identity.capabilities)
    }
    @Test fun permissionMissingDenied() = rejects(FakePlatform(permission = false))
    @Test fun packageNotAllowlistedDenied() = rejects(FakePlatform(packages = listOf("com.untrusted")))
    @Test fun sharedUidDenied() = rejects(FakePlatform(packages = listOf("com.aura.trusted", "com.other")))
    @Test fun signerMismatchDenied() = rejects(FakePlatform(signatures = setOf("b".repeat(64))))
    @Test fun crossUserDenied() = rejects(FakePlatform(userId = 10))
    @Test fun missingPackageDenied() = rejects(FakePlatform(packages = emptyList()))
    @Test fun selfProcessDenied() {
        val fake = FakePlatform(pid = 10)
        assertThrows(IllegalArgumentException::class.java) { resolver(fake).resolve() }
    }
    @Test fun invalidPolicyVersionDenied() = rejects(FakePlatform(), version = 0)
}
