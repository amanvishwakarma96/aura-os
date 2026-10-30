package com.aura.os.approval

/**
 * A trusted input adapter boundary, not an Android Binder implementation.
 * Platform integration must derive every field from verified Android APIs.
 */
data class AuraTrustedCaller(
    val uid: Int,
    val userId: Int,
    val signingIdentityDigest: String,
    val policyVersion: Long,
    val capabilities: Set<String>,
) {
    init {
        require(uid >= 0 && userId >= 0)
        require(signingIdentityDigest.isNotBlank())
        require(policyVersion > 0)
        require(capabilities.none { it.isBlank() })
    }

    fun toContext() = AuraAuthorizationCoordinator.CallerContext(
        uid, userId, signingIdentityDigest, policyVersion, capabilities.toSet()
    )
}
