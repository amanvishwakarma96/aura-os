package com.aura.os.provider

/**
 * Account/profile-scoped token state. Callers must use a protected encrypted
 * SessionStore, not Android shared preferences in plaintext.
 */
class AuraChatGptSessionManager(
    private val endpoint: AuraChatGptTokenEndpoint,
    private val verifier: AuraChatGptIdTokenVerifier = AuraRejectUnverifiedIdTokens,
    private val store: SessionStore,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000L },
) {
    data class AccountKey(val androidUserId: Int, val issuedClientId: String) {
        init {
            require(androidUserId >= 0)
            require(issuedClientId.startsWith("oaiapp_") && issuedClientId.length <= 256)
        }
    }

    class Session(
        val key: AccountKey,
        val issuer: String,
        val subject: String,
        val accessToken: String,
        val refreshToken: String,
        val idToken: String,
        val grantedScopes: Set<String>,
        val expiresAtEpochSeconds: Long,
        val earliestRefreshAtEpochSeconds: Long?,
        val generation: Long,
    ) {
        override fun toString(): String = "Session(REDACTED)"
        val planUsageGranted: Boolean
            get() = AuraChatGptPlanPolicy.REQUIRED_SCOPE in grantedScopes
    }

    /**
     * Must store all token fields in one durable, encrypted transaction.
     * Compare-and-swap rejects stale refreshes and prevents token rollback.
     * Production implementation must preserve this across process restarts.
     */
    interface SessionStore {
        fun read(key: AccountKey): Session?
        fun replaceAtomic(key: AccountKey, expectedGeneration: Long?, session: Session): Boolean
        fun delete(key: AccountKey)
    }

    /** No credential persistence is bundled until Android Keystore is integrated. */
    @Synchronized
    fun install(
        androidUserId: Int,
        exchange: AuraChatGptOAuthPkce.CodeExchange,
        expectedReturningSubject: String? = null,
    ): Session {
        val key = AccountKey(androidUserId, exchange.clientId)
        val previous = store.read(key)
        // Returning sign-in can only replace a previously verified account.
        if (previous != null || expectedReturningSubject != null) {
            require(expectedReturningSubject != null &&
                (previous == null || previous.subject == expectedReturningSubject)) {
                "Returning account binding mismatch"
            }
        }
        val response = endpoint.exchange(AuraChatGptTokenRequest.AuthorizationCode(exchange))
        val identity = verifier.verify(
            response.idToken, exchange.clientId, exchange.expectedNonce
        )
        require(expectedReturningSubject == null || identity.subject == expectedReturningSubject) {
            "Verified account changed during OAuth"
        }
        require(previous == null || (identity.subject == previous.subject &&
            identity.issuer == previous.issuer)) { "Account binding changed" }
        val record = session(key, identity, response, (previous?.generation ?: 0L) + 1L)
        check(store.replaceAtomic(key, previous?.generation, record)) {
            "Concurrent account update: discard and reauthorize"
        }
        return record
    }

    /**
     * Refreshes are serialized per manager and atomically committed by the
     * store. A new ID token must have a cryptographically verified identity.
     * Do NOT silently keep an old refresh token when a replacement is issued.
     */
    @Synchronized
    fun refresh(key: AccountKey): Session {
        val previous = store.read(key) ?: error("No ChatGPT account to refresh")
        val current = nowSeconds()
        require(previous.earliestRefreshAtEpochSeconds == null ||
            current >= previous.earliestRefreshAtEpochSeconds) {
            "Provider has not allowed token refresh yet"
        }
        val response = endpoint.exchange(
            AuraChatGptTokenRequest.Refresh(key.issuedClientId, previous.refreshToken)
        )
        val identity = verifier.verify(response.idToken, key.issuedClientId, null)
        require(identity.subject == previous.subject && identity.issuer == previous.issuer) {
            "Refreshed credentials belong to a different ChatGPT account"
        }
        val replacement = session(key, identity, response, previous.generation + 1L)
        check(store.replaceAtomic(key, previous.generation, replacement)) {
            "Concurrent refresh: stale replacement refused"
        }
        return replacement
    }

    fun lookup(key: AccountKey): Session? = store.read(key)

    /** Local disconnect immediately removes credentials; remote revocation is separate. */
    @Synchronized
    fun disconnectLocally(key: AccountKey) = store.delete(key)

    private fun session(
        key: AccountKey,
        identity: AuraChatGptIdTokenVerifier.VerifiedIdentity,
        response: AuraChatGptTokenResponse,
        generation: Long,
    ): Session {
        val now = nowSeconds()
        require(now > 0L && now <= Long.MAX_VALUE - response.expiresInSeconds)
        return Session(
            key,
            identity.issuer,
            identity.subject,
            response.accessToken,
            response.refreshToken,
            response.idToken,
            response.grantedScopes(),
            now + response.expiresInSeconds,
            response.earliestRefreshAtEpochSeconds,
            generation,
        )
    }
}
