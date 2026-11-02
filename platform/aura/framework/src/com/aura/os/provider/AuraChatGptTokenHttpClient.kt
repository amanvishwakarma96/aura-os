package com.aura.os.provider

import java.io.ByteArrayOutputStream
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection

/**
 * Public-client HTTPS token transport: no API key, no client secret, no cookies.
 *
 * The injected decoder MUST enforce JSON size/type/duplicate-field rules.
 * Production must use a trusted Android JSON library; this transport alone
 * cannot activate a ChatGPT session without a verified OIDC/JWKS adapter.
 */
class AuraChatGptTokenHttpClient(
    private val decode: (String) -> AuraChatGptTokenResponse,
) : AuraChatGptTokenEndpoint {
    override fun exchange(request: AuraChatGptTokenRequest): AuraChatGptTokenResponse {
        val connection = URL(AuraChatGptTokenRequest.ENDPOINT)
            .openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.useCaches = false
            connection.setRequestProperty(
                "Content-Type", "application/x-www-form-urlencoded"
            )
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Cache-Control", "no-store")
            val body = request.formBody().toByteArray(StandardCharsets.UTF_8)
            require(body.size <= 24_576) { "Oversized token exchange request" }
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            // Never follow token endpoint redirects or expose raw error bodies
            // to logs; the session layer handles documented terminal errors.
            check(status == 200) { "Token endpoint returned HTTP $status" }
            val contentType = connection.contentType.orEmpty().lowercase()
            require(contentType.startsWith("application/json")) {
                "Unexpected token endpoint response type"
            }
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val chunk = ByteArray(4096)
                while (true) {
                    val count = input.read(chunk)
                    if (count < 0) break
                    check(output.size() + count <= 65_536) {
                        "Oversized OAuth response"
                    }
                    output.write(chunk, 0, count)
                }
            }
            return decode(output.toString(StandardCharsets.UTF_8.name()))
        } finally {
            connection.disconnect()
        }
    }
}
