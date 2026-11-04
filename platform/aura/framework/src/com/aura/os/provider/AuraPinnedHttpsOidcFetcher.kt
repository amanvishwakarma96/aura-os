package com.aura.os.provider

import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection

/**
 * Narrow read-only HTTPS transport for the two hard-coded OpenAI OIDC URLs.
 * Uses the system trusted TLS verifier; redirects and arbitrary URLs are
 * forbidden. No bearer token, cookies, API keys, or user data are transmitted.
 */
class AuraPinnedHttpsOidcFetcher : AuraChatGptTrustedIssuerKeys.DocumentFetcher {
    override fun fetch(uri: URI): String {
        val canonical = uri.toASCIIString()
        require(canonical == AuraChatGptTrustedIssuerKeys.DISCOVERY_URL ||
            canonical == AuraChatGptTrustedIssuerKeys.JWKS_URL) {
            "OIDC URL must exactly match the trusted production endpoint"
        }
        require(uri.scheme == "https" && uri.userInfo == null &&
            uri.fragment == null && uri.query == null) {
            "Untrusted OIDC URL components"
        }
        val connection = uri.toURL().openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            check(status == 200) { "OIDC issuer HTTP status $status" }
            val mediaType = connection.contentType?.substringBefore(';')?.trim()
                ?.lowercase()
            require(mediaType == "application/json") {
                "OIDC response content type rejected"
            }
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val bytes = ByteArray(4096)
                while (true) {
                    val count = input.read(bytes)
                    if (count < 0) break
                    check(output.size() + count <= 32_768) {
                        "OIDC issuer document too large"
                    }
                    output.write(bytes, 0, count)
                }
            }
            require(output.size() > 0) { "Empty OIDC issuer document" }
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(output.toByteArray()))
                .toString()
        } finally {
            connection.disconnect()
        }
    }
}
