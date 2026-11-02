package com.aura.os.provider

import java.io.ByteArrayOutputStream
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection

/**
 * Public-client HTTPS token transport: no API key, no client secret, no cookies.
 *
 * The default decoder enforces JSON size/type/duplicate-field rules.
 * Production must review the JSON parsing and JWKS trust boundaries; this transport alone
 * cannot activate a ChatGPT session without a verified OIDC/JWKS adapter.
 */
class AuraChatGptTokenHttpClient(
    private val decode: (String) -> AuraChatGptTokenResponse = AuraChatGptStrictTokenDecoder::decode,
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
            val decodedUtf8 = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(output.toByteArray()))
                .toString()
            return decode(decodedUtf8)
        } finally {
            connection.disconnect()
        }
    }
}
