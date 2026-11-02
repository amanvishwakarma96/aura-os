package com.aura.os.provider

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** OpenAI public-client token grant; it NEVER uses an API key or client secret. */
sealed class AuraChatGptTokenRequest {
    companion object {
        const val ENDPOINT = "https://auth.openai.com/api/accounts/oauth/token"
        const val RESOURCE = "https://api.openai.com/v1"
        private fun encode(v: String) = URLEncoder.encode(v, StandardCharsets.UTF_8.name())
    }

    abstract fun fields(): Map<String, String>

    fun formBody(): String = fields().entries.joinToString("&") { (k, v) ->
        encode(k) + "=" + encode(v)
    }

    // Never let standard logging interpolate OAuth secrets.
    final override fun toString(): String = "AuraChatGptTokenRequest(REDACTED)"

    class AuthorizationCode(
        private val exchange: AuraChatGptOAuthPkce.CodeExchange,
    ) : AuraChatGptTokenRequest() {
        override fun fields(): Map<String, String> = linkedMapOf(
            "grant_type" to "authorization_code",
            "client_id" to exchange.clientId,
            "code" to exchange.code,
            "code_verifier" to exchange.codeVerifier,
            "redirect_uri" to exchange.redirectUri,
            "resource" to exchange.resource,
        )
    }

    class Refresh(
        private val clientId: String,
        private val refreshToken: String,
    ) : AuraChatGptTokenRequest() {
        override fun fields(): Map<String, String> = linkedMapOf(
            "grant_type" to "refresh_token",
            "client_id" to clientId,
            "refresh_token" to refreshToken,
            "resource" to RESOURCE,
        )
    }
}

/**
 * Already-decoded token response. Never log or serialize this object into analytics.
 * A trusted HTTP JSON decoder must validate property types, duplicates and size bounds.
 */
class AuraChatGptTokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val idToken: String,
    val tokenType: String,
    val expiresInSeconds: Long,
    val scope: String,
    val earliestRefreshAtEpochSeconds: Long? = null,
) {
    init {
        require(accessToken.isNotBlank() && accessToken.length <= 16384)
        require(refreshToken.isNotBlank() && refreshToken.length <= 16384)
        require(idToken.isNotBlank() && idToken.length <= 32768)
        require(tokenType == "Bearer") { "Unsupported OAuth token type" }
        require(expiresInSeconds in 1L..86_400L) { "Unexpected access token lifetime" }
        require(scope.length in 1..2048)
        require(earliestRefreshAtEpochSeconds == null || earliestRefreshAtEpochSeconds > 0)
    }

    fun grantedScopes(): Set<String> {
        val items = scope.split(' ').filter { it.isNotBlank() }
        require(items.isNotEmpty() && items.all { it.length <= 128 && it.all { c ->
            c.isLetterOrDigit() || c in "._:-" } })
        return items.toSet()
    }

    override fun toString(): String = "AuraChatGptTokenResponse(REDACTED)"
}

/**
 * This boundary must return ONLY an identity backed by verified OIDC JWT signature.
 * The implementation MUST verify JWKS signature, alg allowlist, issuer, audience,
 * exp/iat/nbf, and an exact nonce for authorization-code grants.
 * expectedNonce=null is reserved for validated refresh grant identity checks.
 */
interface AuraChatGptIdTokenVerifier {
    fun verify(idToken: String, clientId: String, expectedNonce: String?): VerifiedIdentity

    data class VerifiedIdentity(val issuer: String, val subject: String) {
        init {
            require(issuer == "https://auth.openai.com")
            require(subject.isNotBlank() && subject.length <= 512)
        }
    }
}

/** Intentionally cannot activate a session until trusted JOSE validation is wired. */
object AuraRejectUnverifiedIdTokens : AuraChatGptIdTokenVerifier {
    override fun verify(idToken: String, clientId: String, expectedNonce: String?) =
        error("A verified OIDC/JWKS adapter has not been installed")
}

interface AuraChatGptTokenEndpoint {
    /** Must use trusted TLS, bounded response size, JSON parsing and no redirects. */
    fun exchange(request: AuraChatGptTokenRequest): AuraChatGptTokenResponse
}
