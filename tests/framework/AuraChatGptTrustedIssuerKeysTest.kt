package com.aura.os.provider

import java.net.URI
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Base64

private fun mustReject(block: () -> Any?) {
    check(runCatching(block).isFailure) { "Untrusted issuer material was accepted" }
}

private fun jwks(kid: String, key: KeyPair): String {
    val rsa = key.public as RSAPublicKey
    fun encoded(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(
            bytes.dropWhile { it == 0.toByte() }.toByteArray()
        )
    return """{"keys":[{"kid":"$kid","kty":"RSA","use":"sig","alg":"RS256",
        "n":"${encoded(rsa.modulus.toByteArray())}",
        "e":"${encoded(rsa.publicExponent.toByteArray())}"}]}"""
}

private class TestFetcher(
    var discovery: String,
    var keys: String,
) : AuraChatGptTrustedIssuerKeys.DocumentFetcher {
    var discoveryCount = 0
    var keysCount = 0
    var fail = false
    val urls = mutableListOf<String>()

    override fun fetch(uri: URI): String {
        urls.add(uri.toASCIIString())
        if (fail) error("Simulated offline OIDC issuer")
        return when (uri.toASCIIString()) {
            AuraChatGptTrustedIssuerKeys.DISCOVERY_URL -> {
                discoveryCount++
                discovery
            }
            AuraChatGptTrustedIssuerKeys.JWKS_URL -> {
                keysCount++
                keys
            }
            else -> error("Unexpected issuer URL")
        }
    }
}

private fun sign(key: KeyPair, kid: String): String {
    val header = """{"alg":"RS256","typ":"JWT","kid":"$kid"}"""
    val claims = """{"iss":"https://auth.openai.com","aud":"oaiapp_verified",
        "sub":"verified-account","nonce":"nonce-123","iat":1799999900,
        "exp":1800003600}"""
    fun b64(value: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toByteArray(Charsets.UTF_8))
    val input = b64(header) + "." + b64(claims)
    val signature = Signature.getInstance("SHA256withRSA").apply {
        initSign(key.private)
        update(input.toByteArray(Charsets.US_ASCII))
    }.sign()
    return input + "." + Base64.getUrlEncoder().withoutPadding()
        .encodeToString(signature)
}

fun main() {
    val pairA = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        .generateKeyPair()
    val pairB = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        .generateKeyPair()
    val validDiscovery = """{"issuer":"https://auth.openai.com",
        "jwks_uri":"https://auth.openai.com/.well-known/jwks.json"}"""
    val fixture = TestFetcher(validDiscovery, jwks("key-A", pairA))
    var clockMs = 1_000_000L
    val trusted = AuraChatGptTrustedIssuerKeys(fixture) { clockMs }

    check(trusted.keyForKid("key-A")?.modulus ==
        (pairA.public as RSAPublicKey).modulus)
    check(fixture.urls == listOf(
        AuraChatGptTrustedIssuerKeys.DISCOVERY_URL,
        AuraChatGptTrustedIssuerKeys.JWKS_URL
    ))
    check(fixture.discoveryCount == 1 && fixture.keysCount == 1)

    // Reuse a validated, unexpired key without network traffic.
    check(trusted.keyForKid("key-A") != null)
    check(fixture.keysCount == 1)

    val verified = trusted.verifier { 1_800_000_000L }
        .verify(sign(pairA, "key-A"), "oaiapp_verified", "nonce-123")
    check(verified.subject == "verified-account")

    // Unknown key cannot trigger a network request on every incoming token.
    check(trusted.keyForKid("key-B") == null)
    check(fixture.keysCount == 1)
    clockMs += 30_001L
    fixture.keys = jwks("key-B", pairB)
    check(trusted.keyForKid("key-B")?.modulus ==
        (pairB.public as RSAPublicKey).modulus)
    check(fixture.keysCount == 2)
    mustReject {
        trusted.verifier { 1_800_000_000L }
            .verify(sign(pairA, "key-A"), "oaiapp_verified", "nonce-123")
    }
    check(trusted.verifier { 1_800_000_000L }
        .verify(sign(pairB, "key-B"), "oaiapp_verified", "nonce-123")
        .subject == "verified-account")

    // Expired trust cache cannot silently serve stale RSA keys when offline.
    clockMs += 600_001L
    fixture.fail = true
    mustReject { trusted.keyForKid("key-B") }

    fun fromDiscovery(value: String): AuraChatGptTrustedIssuerKeys {
        val source = TestFetcher(value, jwks("key-A", pairA))
        return AuraChatGptTrustedIssuerKeys(source) { 10_000_000L }
    }
    mustReject { fromDiscovery(
        """{"issuer":"https://attacker.invalid",
            "jwks_uri":"https://auth.openai.com/.well-known/jwks.json"}"""
    ).keyForKid("key-A") }
    mustReject { fromDiscovery(
        """{"issuer":"https://auth.openai.com",
            "jwks_uri":"https://attacker.invalid/keys"}"""
    ).keyForKid("key-A") }
    mustReject { fromDiscovery(
        """{"issuer":"https://auth.openai.com",
            "issuer":"https://auth.openai.com",
            "jwks_uri":"https://auth.openai.com/.well-known/jwks.json"}"""
    ).keyForKid("key-A") }
    mustReject { fromDiscovery(validDiscovery).keyForKid("bad/key") }
    mustReject { AuraChatGptTrustedIssuerKeys(
        TestFetcher(validDiscovery, """{"keys":[]}""")
    ) { 1000L }.keyForKid("key-A") }

    // The production fetcher must reject arbitrary URLs BEFORE networking.
    val fetcher = AuraPinnedHttpsOidcFetcher()
    mustReject { fetcher.fetch(URI("http://auth.openai.com/.well-known/jwks.json")) }
    mustReject { fetcher.fetch(URI("https://attacker.invalid/jwks.json")) }
    mustReject { fetcher.fetch(URI(
        "https://auth.openai.com/.well-known/jwks.json?redirect=1"
    )) }
    mustReject { fetcher.fetch(URI(
        "https://auth.openai.com@attacker.invalid/.well-known/jwks.json"
    )) }
    println("PASS: trusted issuer discovery, JWKS rotation and fail-closed key expiry")
}
