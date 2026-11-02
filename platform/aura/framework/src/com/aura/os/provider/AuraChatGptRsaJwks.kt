package com.aura.os.provider

import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

/**
 * Strictly parses RSA verification keys from a JWKS document that the caller
 * has ALREADY obtained from a trusted, issuer-bound HTTPS OIDC JWKS endpoint.
 *
 * WARNING: JSON origin, TLS, discovery provenance, cache lifetime and key
 * rotation are NOT established here. Never pass a JWT-supplied jwk/jku/x5u.
 */
object AuraChatGptRsaJwks {
    fun parseTrustedDocument(json: String): Map<String, RSAPublicKey> {
        val root = AuraStrictJson.objectValue(json, 32_768)
        val list = root["keys"] as? List<*> ?: error("JWKS keys missing")
        require(list.isNotEmpty() && list.size <= 32) { "Invalid JWKS key count" }
        val keys = linkedMapOf<String, RSAPublicKey>()
        for (raw in list) {
            val item = raw as? Map<*, *> ?: error("Invalid JWK")
            if (item["kty"] != "RSA") continue
            if (item["use"] != null && item["use"] != "sig") continue
            if (item["alg"] != null && item["alg"] != "RS256") continue
            val kid = item["kid"] as? String ?: error("Missing RSA JWK kid")
            require(kid.length in 1..256 &&
                kid.all { it.isLetterOrDigit() || it in "_-.:" }) {
                "Invalid RSA JWK key ID"
            }
            require(!keys.containsKey(kid)) { "Duplicate trusted signing key" }
            val modulus = positiveInteger(item["n"])
            val exponent = positiveInteger(item["e"])
            require(modulus.bitLength() in 2048..8192 &&
                exponent >= BigInteger.valueOf(3) &&
                exponent <= BigInteger.valueOf(65_537) &&
                exponent.testBit(0)) {
                "Invalid RSA signing key parameters"
            }
            val key = KeyFactory.getInstance("RSA")
                .generatePublic(RSAPublicKeySpec(modulus, exponent))
            keys[kid] = key as RSAPublicKey
        }
        require(keys.isNotEmpty()) { "JWKS has no acceptable RS256 keys" }
        return keys.toMap()
    }

    private fun positiveInteger(value: Any?): BigInteger {
        val text = value as? String ?: error("Missing RSA key parameter")
        require(text.length in 1..1536 && text.all {
            (it in 'A'..'Z') || (it in 'a'..'z') ||
                (it in '0'..'9') || it in "-_"
        }) { "Invalid RSA key encoding" }
        val bytes = Base64.getUrlDecoder().decode(text)
        require(bytes.isNotEmpty() &&
            Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) == text &&
            bytes.first() != 0.toByte()) {
            "Noncanonical RSA key encoding"
        }
        return BigInteger(1, bytes)
    }
}
