package com.aura.os.provider

import java.net.URI
import java.security.interfaces.RSAPublicKey

/**
 * Production OpenAI identity trust boundary. The discovery issuer and signing
 * key endpoint must BOTH match the documented production endpoints exactly.
 * Never follow a JWKS URL supplied by an ID token.
 *
 * An injected document fetcher is used for deterministic host security tests.
 * For real use inject [AuraPinnedHttpsOidcFetcher], which enforces TLS and
 * rejects redirects. No Android session becomes active just by creating this.
 */
class AuraChatGptTrustedIssuerKeys(
    private val fetcher: DocumentFetcher,
    private val clockMs: () -> Long = System::currentTimeMillis,
) {
    fun interface DocumentFetcher {
        fun fetch(uri: URI): String
    }

    companion object {
        const val ISSUER = "https://auth.openai.com"
        const val DISCOVERY_URL = "https://auth.openai.com/.well-known/openid-configuration"
        const val JWKS_URL = "https://auth.openai.com/.well-known/jwks.json"
        private const val CACHE_TTL_MS = 10L * 60L * 1000L
        private const val UNKNOWN_KEY_RETRY_MS = 30L * 1000L

        fun production(clockMs: () -> Long = System::currentTimeMillis) =
            AuraChatGptTrustedIssuerKeys(AuraPinnedHttpsOidcFetcher(), clockMs)
    }

    private data class Snapshot(
        val keys: Map<String, RSAPublicKey>,
        val loadedAtMs: Long,
        val expiresAtMs: Long,
    )

    private var current: Snapshot? = null
    private var lastFetchAttemptMs: Long? = null

    @Synchronized
    fun keyForKid(kid: String): RSAPublicKey? {
        require(kid.length in 1..256 && kid.all {
            it.isLetterOrDigit() && it.code < 128 || it in "_.-:"
        }) { "Invalid signing key ID" }
        val now = clockMs()
        require(now > 0 && now <= Long.MAX_VALUE - CACHE_TTL_MS) {
            "Invalid OIDC trust clock"
        }
        val cached = current
        val fresh = cached != null && now >= cached.loadedAtMs && now < cached.expiresAtMs
        if (fresh) {
            val known = cached!!.keys[kid]
            if (known != null) return known
        }

        // Bound untrusted 'kid' pressure to one forced reload per 30 seconds;
        // never trust an unknown key merely because its name was in a JWT.
        val last = lastFetchAttemptMs
        if (last != null && now >= last && now - last < UNKNOWN_KEY_RETRY_MS) {
            return null
        }
        lastFetchAttemptMs = now

        val metadata = AuraStrictJson.objectValue(
            fetcher.fetch(URI(DISCOVERY_URL)), 32_768
        )
        require(metadata["issuer"] == ISSUER) { "Untrusted OIDC discovery issuer" }
        require(metadata["jwks_uri"] == JWKS_URL) { "Untrusted OIDC JWKS location" }
        val loadedKeys = AuraChatGptRsaJwks.parseTrustedDocument(
            fetcher.fetch(URI(JWKS_URL))
        )
        // Atomic replacement only after both issuer and keys validate.
        current = Snapshot(loadedKeys, now, now + CACHE_TTL_MS)
        return loadedKeys[kid]
    }

    fun verifier(clockSeconds: () -> Long = { System.currentTimeMillis() / 1000L }) =
        AuraChatGptRsaIdTokenVerifier(::keyForKid, clockSeconds)
}
