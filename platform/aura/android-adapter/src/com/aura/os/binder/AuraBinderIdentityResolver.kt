package com.aura.os.binder

import com.aura.os.approval.AuraTrustedCaller

/**
 * Intended exclusively for invocation directly within an incoming Binder transaction.
 * Never use request-supplied UID, package, signer or capabilities.
 * This platform adapter is not connected to SystemServer.
 */
class AuraBinderIdentityResolver(
    private val platform: Platform,
    private val allowedSigners: Map<String, Set<String>>,
    private val allowedCapabilities: Map<String, Set<String>>,
    private val policyVersion: () -> Long,
) {
    interface Platform {
        fun callerUid(): Int
        fun callerPid(): Int
        fun servicePid(): Int
        fun userIdForUid(uid: Int): Int
        fun hasCallingPermission(permission: String): Boolean
        fun packagesForUid(uid: Int): List<String>
        /** SHA-256 signing certificate digests for APK content signers, lowercase hex. */
        fun currentSigningDigests(packageName: String): Set<String>
    }

    fun resolve(): AuraTrustedCaller {
        val uid = platform.callerUid()
        val pid = platform.callerPid()
        require(uid >= 0 && pid > 0 && pid != platform.servicePid()) { "Not a remote Binder call" }
        check(platform.hasCallingPermission("com.aura.os.permission.ACCESS_AGENT_SERVICE")) {
            "Missing signature permission"
        }
        val packages = platform.packagesForUid(uid).distinct()
        // Deny ambiguous shared UIDs and any package not explicitly trusted.
        check(packages.size == 1) { "Ambiguous caller package identity" }
        val pkg = packages.single()
        val expected = allowedSigners[pkg] ?: error("Caller not allowlisted")
        check(expected.isNotEmpty() && expected.all { it.matches(Regex("[0-9a-f]{64}")) })
        val observed = platform.currentSigningDigests(pkg)
        check(observed.isNotEmpty() && observed == expected) { "Signing identity mismatch" }
        val version = policyVersion()
        check(version > 0)
        val userId = platform.userIdForUid(uid)
        check(userId == 0) { "Cross-user/profile access unavailable in first revision" }
        return AuraTrustedCaller(uid, userId, observed.sorted().joinToString(":"), version,
            allowedCapabilities[pkg]?.toSet() ?: emptySet())
    }
}
