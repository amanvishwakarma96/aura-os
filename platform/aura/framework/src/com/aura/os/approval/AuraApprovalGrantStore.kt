package com.aura.os.approval

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** In-memory, process-local gate. Never expose issueGrant to Binder/model clients. */
class AuraApprovalGrantStore(
    private val monotonicMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    private val random: SecureRandom = SecureRandom(),
    private val maxPending: Int = 1024,
) {
    data class Binding(
        val callerUid: Int,
        val userId: Int,
        val actionId: String,
        val canonicalArguments: ByteArray,
        val signingIdentityDigest: String = "",
        val policyVersion: Long = 0,
    )

    private data class Entry(val uid: Int, val userId: Int, val actionId: String, val digest: ByteArray, val signingIdentityDigest: String, val policyVersion: Long, val expiry: Long)
    private val pending = mutableMapOf<String, Entry>()

    init { require(maxPending > 0) }

    /** Only a trusted, verified UI/authentication coordinator may call this inside the service. */
    @Synchronized
    internal fun issueGrant(binding: Binding, lifetimeMillis: Long = 30_000): String {
        require(lifetimeMillis in 1..60_000) { "Grant lifetime outside allowed interval" }
        require(binding.callerUid >= 0 && binding.userId >= 0 && binding.actionId.isNotBlank())
        require(binding.canonicalArguments.size <= 16_384)
        require(binding.signingIdentityDigest.isNotBlank() && binding.policyVersion > 0)
        val now = monotonicMillis()
        pending.entries.removeAll { it.value.expiry <= now }
        check(pending.size < maxPending) { "Too many outstanding approval grants" }
        val expiry = Math.addExact(now, lifetimeMillis)
        val digest = MessageDigest.getInstance("SHA-256").digest(binding.canonicalArguments)
        val token = ByteArray(32).also(random::nextBytes)
        val id = Base64.getUrlEncoder().withoutPadding().encodeToString(token)
        check(!pending.containsKey(id)) { "Approval token collision" }
        pending[id] = Entry(binding.callerUid, binding.userId, binding.actionId, digest, binding.signingIdentityDigest, binding.policyVersion, expiry)
        return id
    }

    /** Atomically consumes even a mismatched grant to prevent repeated guessing. */
    @Synchronized
    fun consume(token: String, binding: Binding): Boolean {
        val entry = pending.remove(token) ?: return false
        if (monotonicMillis() >= entry.expiry) return false
        if (entry.uid != binding.callerUid || entry.userId != binding.userId || entry.actionId != binding.actionId) return false
        if (entry.signingIdentityDigest != binding.signingIdentityDigest || entry.policyVersion != binding.policyVersion) return false
        val candidate = MessageDigest.getInstance("SHA-256").digest(binding.canonicalArguments)
        return MessageDigest.isEqual(entry.digest, candidate)
    }

    @Synchronized
    fun invalidateAll() { pending.clear() }
}
