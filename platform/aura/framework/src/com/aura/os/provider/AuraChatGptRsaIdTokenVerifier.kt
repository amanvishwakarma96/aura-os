package com.aura.os.provider

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Base64

/**
 * Verifies RS256 OpenID Connect ID tokens using *already trusted* issuer keys.
 *
 * keyForKid MUST resolve exclusively from a verified HTTPS OpenAI OIDC JWKS
 * source with a bounded cache; it must not use a JWK or URL from the JWT header.
 * This class performs no discovery, networking, or Android credential storage.
 *
 * Production deployment requires independent JOSE/OIDC security review and
 * a tested trusted-JWKS implementation. Unsupported algorithms fail closed.
 */
class AuraChatGptRsaIdTokenVerifier(
    private val keyForKid: (String) -> RSAPublicKey?,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000L },
) : AuraChatGptIdTokenVerifier {
    companion object {
        const val ISSUER = "https://auth.openai.com"
        private const val MAX_JWT = 32_768
        private const val CLOCK_SKEW_SECONDS = 60L
    }

    override fun verify(
        idToken: String,
        clientId: String,
        expectedNonce: String?,
    ): AuraChatGptIdTokenVerifier.VerifiedIdentity {
        require(clientId.startsWith("oaiapp_") && clientId.length <= 256) {
            "Unexpected OAuth client ID"
        }
        require(idToken.length in 100..MAX_JWT) { "Invalid ID token length" }
        val parts = idToken.split('.')
        require(parts.size == 3 && parts.all { it.isNotBlank() }) {
            "ID token must contain three segments"
        }
        val header = AuraStrictJson.objectValue(readPart(parts[0], 4096), 4096)
        require(header["alg"] == "RS256") { "Unsupported ID token algorithm" }
        val kid = header["kid"] as? String ?: error("Missing signing key ID")
        require(kid.length in 1..256 &&
            kid.all { it.isLetterOrDigit() || it in "_-.:" }) {
            "Invalid signing key ID"
        }
        require(header["typ"] == null || header["typ"] == "JWT") {
            "Unsupported JOSE type"
        }
        require(listOf("jwk", "jku", "x5u", "x5c", "crit", "b64", "zip", "cty", "enc")
            .none { header.containsKey(it) }) {
            "Untrusted JOSE header extension"
        }
        val key = keyForKid(kid) ?: error("Unknown trusted signing key")
        require(key.modulus.bitLength() >= 2048) { "RSA signing key too short" }
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initVerify(key)
        signature.update((parts[0] + "." + parts[1])
            .toByteArray(StandardCharsets.US_ASCII))
        require(signature.verify(decodePart(parts[2], 1024))) {
            "Invalid signed ID token"
        }

        // JSON claims are interpreted only AFTER signature verification.
        val claims = AuraStrictJson.objectValue(readPart(parts[1], 16_384), 16_384)
        require(claims["iss"] == ISSUER) { "Unexpected identity issuer" }
        val audiences = when (val audience = claims["aud"]) {
            is String -> listOf(audience)
            is List<*> -> audience
            else -> error("ID token audience is missing")
        }
        require(audiences.size == 1 && audiences.single() == clientId) {
            "ID token was issued to a different client"
        }
        val subject = claims["sub"] as? String ?: error("Missing verified subject")
        require(subject.isNotBlank() && subject.length <= 512) {
            "Invalid verified subject"
        }

        val now = nowSeconds()
        require(now > 0) { "Invalid verification clock" }
        val expiration = claims["exp"] as? Long ?: error("Missing expiration")
        val issuedAt = claims["iat"] as? Long ?: error("Missing issued-at")
        require(expiration > now && issuedAt <= now + CLOCK_SKEW_SECONDS &&
            issuedAt > 0L && issuedAt < expiration) {
            "Expired or not-yet-issued ID token"
        }
        val notBefore = claims["nbf"]
        require(notBefore == null || (notBefore is Long &&
            notBefore <= now + CLOCK_SKEW_SECONDS)) {
            "ID token not yet valid"
        }
        if (expectedNonce != null) {
            val nonce = claims["nonce"] as? String ?: error("Missing OIDC nonce")
            require(MessageDigest.isEqual(
                nonce.toByteArray(StandardCharsets.UTF_8),
                expectedNonce.toByteArray(StandardCharsets.UTF_8)
            )) { "OIDC nonce mismatch" }
        }
        return AuraChatGptIdTokenVerifier.VerifiedIdentity(ISSUER, subject)
    }

    private fun readPart(encoded: String, maxBytes: Int): String =
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(decodePart(encoded, maxBytes)))
            .toString()

    private fun decodePart(encoded: String, maxBytes: Int): ByteArray {
        require(encoded.length <= maxBytes * 2 &&
            encoded.all { it.isLetterOrDigit() && it.code < 128 || it in "-_" }) {
            "Invalid JWS base64url segment"
        }
        val result = Base64.getUrlDecoder().decode(encoded)
        require(result.size in 1..maxBytes) { "Oversized JWS segment" }
        require(Base64.getUrlEncoder().withoutPadding().encodeToString(result) == encoded) {
            "Noncanonical JWS segment"
        }
        return result
    }
}
