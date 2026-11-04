package com.aura.os.provider

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

/**
 * Pure-JVM portion of ChatGPT plan OAuth Authorization Code + PKCE.
 *
 * This deliberately does not open a browser, listen on HTTP, exchange tokens,
 * validate a JWT, or assert subscription eligibility. Those are independent
 * platform/network integration gates. Only call complete() with a callback
 * delivered by a local listener bound to 127.0.0.1.
 */
class AuraChatGptOAuthPkce(
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
) {
    companion object {
        private const val AUTH_URL = "https://auth.openai.com/api/accounts/authorize"
        const val RESOURCE = "https://api.openai.com/v1"
        const val PLAN_SCOPE = AuraChatGptPlanPolicy.REQUIRED_SCOPE
        const val REQUESTED_SCOPES =
            "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct"
        private const val DYNAMIC_CLIENT = "dynamic_agent_client"
        private const val TIMEOUT_MS = 5L * 60L * 1000L
    }

    data class Authorization(val browserUrl: String, val expiresAtEpochMs: Long)

    /** Sensitive verifier and authorization code: never log, serialize or persist. */
    class CodeExchange internal constructor(
        val code: String,
        val clientId: String,
        val codeVerifier: String,
        val redirectUri: String,
        val resource: String,
        val expectedNonce: String,
    ) {
        override fun toString(): String = "CodeExchange(REDACTED)"
    }

    sealed class CallbackResult {
        class Denied(val reason: String) : CallbackResult()
        class Exchange(val request: CodeExchange) : CallbackResult()
    }

    private class Pending(
        val state: String,
        val nonce: String,
        val verifier: String,
        val redirect: String,
        val existingClientId: String?,
        val startedAtMs: Long,
    )

    private var pending: Pending? = null

    @Synchronized
    fun begin(
        hostId: String,
        redirectUri: String,
        existingClientId: String? = null,
    ): Authorization {
        require(hostId.startsWith("urn:uuid:")) { "Host ID must use urn:uuid" }
        require(runCatching { UUID.fromString(hostId.removePrefix("urn:uuid:")) }.isSuccess) {
            "Invalid persistent host ID"
        }
        val redirect = URI(redirectUri)
        require(
            redirect.scheme == "http" &&
                redirect.host == "127.0.0.1" &&
                redirect.port in 1..65535 &&
                redirect.path == "/auth/callback" &&
                redirect.rawQuery == null &&
                redirect.rawFragment == null &&
                redirect.userInfo == null &&
                redirect.toASCIIString() == redirectUri
        ) { "Callback must be an exact 127.0.0.1 loopback URL on /auth/callback" }
        require(
            existingClientId == null ||
                (existingClientId.startsWith("oaiapp_") && existingClientId.length <= 256 &&
                    existingClientId.all { it.isLetterOrDigit() || it == '_' || it == '-' })
        ) { "Invalid issued client ID" }

        val state = secret(32)
        val nonce = secret(32)
        val verifier = secret(32)
        val current = nowMs()
        pending = Pending(state, nonce, verifier, redirectUri, existingClientId, current)
        val parameters = linkedMapOf(
            "client_id" to (existingClientId ?: DYNAMIC_CLIENT),
            "ext_agent_host_id" to hostId,
            "response_type" to "code",
            "redirect_uri" to redirectUri,
            "scope" to REQUESTED_SCOPES,
            "resource" to RESOURCE,
            "state" to state,
            "nonce" to nonce,
            "code_challenge_method" to "S256",
            "code_challenge" to base64url(MessageDigest.getInstance("SHA-256").digest(
                verifier.toByteArray(StandardCharsets.US_ASCII)
            )),
        )
        if (existingClientId == null) parameters["agent_name_hint"] = "AURA OS"
        val query = parameters.entries.joinToString("&") { (key, value) ->
            form(key) + "=" + form(value)
        }
        return Authorization("$AUTH_URL?$query", current + TIMEOUT_MS)
    }

    /**
     * A matching state is consumed exactly once, including on access_denied.
     * Mismatched callbacks cannot consume the user's ongoing attempt.
     */
    @Synchronized
    fun complete(callbackUrl: String): CallbackResult {
        val attempt = pending ?: error("No pending ChatGPT sign-in")
        if (nowMs() < attempt.startedAtMs || nowMs() - attempt.startedAtMs >= TIMEOUT_MS) {
            pending = null
            error("ChatGPT authorization attempt expired")
        }
        require(callbackUrl.length <= 8192) { "Oversized OAuth callback" }
        val uri = URI(callbackUrl)
        require(uri.rawFragment == null && callbackUrl.startsWith(attempt.redirect + "?")) {
            "Unexpected OAuth callback URI"
        }
        require(uri.scheme == "http" && uri.host == "127.0.0.1" &&
            uri.toASCIIString().substringBefore('?') == attempt.redirect) {
            "OAuth callback URI mismatch"
        }
        val parameters = parseUnique(uri.rawQuery ?: error("Missing callback parameters"))
        val actualState = parameters["state"] ?: error("Missing OAuth state")
        require(MessageDigest.isEqual(
            actualState.toByteArray(StandardCharsets.UTF_8),
            attempt.state.toByteArray(StandardCharsets.UTF_8)
        )) { "OAuth state mismatch" }

        pending = null
        val oauthError = parameters["error"]
        if (oauthError != null) return CallbackResult.Denied(oauthError.take(80))
        val code = parameters["code"]?.takeIf { it.isNotBlank() && it.length <= 4096 }
            ?: error("Missing authorization code")
        val callbackClient = parameters["client_id"]
        val clientId = if (attempt.existingClientId == null) {
            require(
                callbackClient != null && callbackClient.startsWith("oaiapp_") &&
                    callbackClient.length <= 256 &&
                    callbackClient.all { it.isLetterOrDigit() || it == '_' || it == '-' }
            ) { "Dynamic registration must supply an issued client ID" }
            callbackClient
        } else {
            require(callbackClient == null || callbackClient == attempt.existingClientId) {
                "Unexpected client ID for returning account"
            }
            attempt.existingClientId
        }
        return CallbackResult.Exchange(CodeExchange(
            code, clientId, attempt.verifier, attempt.redirect, RESOURCE, attempt.nonce
        ))
    }

    @Synchronized
    fun cancel() {
        pending = null
    }

    private fun secret(bytes: Int): String {
        val buffer = ByteArray(bytes)
        random.nextBytes(buffer)
        return base64url(buffer)
    }

    private fun base64url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun form(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun parseUnique(query: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (pair in query.split('&')) {
            require(pair.isNotBlank() && pair.contains('=')) { "Invalid callback parameter" }
            val pieces = pair.split('=', limit = 2)
            val key = URLDecoder.decode(pieces[0], StandardCharsets.UTF_8.name())
            val value = URLDecoder.decode(pieces[1], StandardCharsets.UTF_8.name())
            require(key.isNotBlank() && key.length <= 80 && value.length <= 4096)
            require(!result.containsKey(key)) { "Duplicate OAuth parameter" }
            result[key] = value
        }
        return result
    }
}
