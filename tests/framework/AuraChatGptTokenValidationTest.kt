package com.aura.os.provider

import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Base64

private const val CLIENT = "oaiapp_test123"
private const val NOW = 1_800_000_000L

private fun rejected(block: () -> Any?) {
    check(runCatching { block() }.isFailure) { "An invalid token was accepted" }
}

private fun tokenJson(
    scope: String = "openid profile chatgpt.tokens.use.direct",
    exp: String = "3600",
    extra: String = "",
) = """{"access_token":"access","refresh_token":"refresh","id_token":"id.jwt.value",
    "token_type":"Bearer","expires_in":$exp,"scope":"$scope",
    "earliest_refresh_at":1790000000$extra}"""

private fun b64(input: String): String =
    Base64.getUrlEncoder().withoutPadding()
        .encodeToString(input.toByteArray(StandardCharsets.UTF_8))

private fun signed(
    key: KeyPair,
    header: String,
    claims: String,
): String {
    val data = b64(header) + "." + b64(claims)
    val sign = Signature.getInstance("SHA256withRSA")
    sign.initSign(key.private)
    sign.update(data.toByteArray(StandardCharsets.US_ASCII))
    return data + "." + Base64.getUrlEncoder().withoutPadding()
        .encodeToString(sign.sign())
}

private fun claims(
    iss: String = "https://auth.openai.com",
    aud: String = CLIENT,
    subject: String = "account-123",
    nonce: String = "original-nonce",
    exp: Long = NOW + 3600,
    iat: Long = NOW - 20,
    extra: String = "",
): String = """{"iss":"$iss","aud":"$aud","sub":"$subject",
    "nonce":"$nonce","exp":$exp,"iat":$iat$extra}"""

fun main() {
    val decoded = AuraChatGptStrictTokenDecoder.decode(tokenJson())
    check(decoded.accessToken == "access")
    check(decoded.refreshToken == "refresh")
    check(decoded.earliestRefreshAtEpochSeconds == 1_790_000_000L)
    check(decoded.grantedScopes().contains("chatgpt.tokens.use.direct"))
    check("access" !in decoded.toString())

    rejected { AuraChatGptStrictTokenDecoder.decode(tokenJson(exp = "\"3600\"")) }
    rejected { AuraChatGptStrictTokenDecoder.decode(tokenJson(exp = "3600.0")) }
    rejected { AuraChatGptStrictTokenDecoder.decode(tokenJson(exp = "0")) }
    rejected { AuraChatGptStrictTokenDecoder.decode(tokenJson(extra = ",\"scope\":\"extra\"")) }
    rejected { AuraChatGptStrictTokenDecoder.decode(tokenJson(extra = ""","unauthorized":true""")) }
    rejected { AuraChatGptStrictTokenDecoder.decode(tokenJson().dropLast(1) + "garbage") }
    rejected { AuraChatGptStrictTokenDecoder.decode("{\"access_token\":\"a\"}") }
    rejected { AuraChatGptStrictTokenDecoder.decode(tokenJson().replace("Bearer", "MAC")) }
    rejected { AuraChatGptStrictTokenDecoder.decode(tokenJson().replace("1790000000", "null")) }
    rejected { AuraChatGptStrictTokenDecoder.decode(tokenJson().replace("\"access\"", "\"\\uD800\"")) }
    rejected { AuraChatGptStrictTokenDecoder.decode(tokenJson() + " ".repeat(65_537)) }

    val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val another = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val verifier = AuraChatGptRsaIdTokenVerifier(
        keyForKid = { if (it == "trusted-2026") key.public as RSAPublicKey else null },
        nowSeconds = { NOW }
    )
    val header = """{"alg":"RS256","kid":"trusted-2026","typ":"JWT"}"""
    val valid = signed(key, header, claims())
    val identity = verifier.verify(valid, CLIENT, "original-nonce")
    check(identity.issuer == "https://auth.openai.com")
    check(identity.subject == "account-123")
    check(verifier.verify(valid, CLIENT, null).subject == "account-123")

    rejected { verifier.verify(valid, "oaiapp_other", "original-nonce") }
    rejected { verifier.verify(valid, CLIENT, "forged-nonce") }
    rejected { verifier.verify(signed(another, header, claims()), CLIENT, "original-nonce") }
    rejected { verifier.verify(signed(key,
        """{"alg":"none","kid":"trusted-2026"}""", claims()), CLIENT, "original-nonce") }
    rejected { verifier.verify(signed(key,
        """{"alg":"HS256","kid":"trusted-2026"}""", claims()), CLIENT, "original-nonce") }
    rejected { verifier.verify(signed(key,
        """{"alg":"RS256","kid":"untrusted-key"}""", claims()), CLIENT, "original-nonce") }
    rejected { verifier.verify(signed(key,
        """{"alg":"RS256","kid":"trusted-2026","jku":"https://attacker.invalid"}""",
        claims()), CLIENT, "original-nonce") }
    rejected { verifier.verify(signed(key, header,
        claims(iss = "https://attacker.invalid")), CLIENT, "original-nonce") }
    rejected { verifier.verify(signed(key, header,
        claims(aud = "oaiapp_other")), CLIENT, "original-nonce") }
    rejected { verifier.verify(signed(key, header,
        claims(exp = NOW - 1)), CLIENT, "original-nonce") }
    rejected { verifier.verify(signed(key, header,
        claims(iat = NOW + 120)), CLIENT, "original-nonce") }
    rejected { verifier.verify(signed(key, header,
        claims(extra = ",\"nbf\":1800000120")), CLIENT, "original-nonce") }
    rejected { verifier.verify(signed(key, header,
        claims(extra = ",\"sub\":\"duplicate\"")), CLIENT, "original-nonce") }
    rejected { verifier.verify(valid.substringBeforeLast('.') + ".invalid-base64$", CLIENT, null) }
    rejected { verifier.verify(valid.substringBeforeLast('.') + "." + valid.substringAfterLast('.') + "=", CLIENT, null) }

    val wrongKey = AuraChatGptRsaIdTokenVerifier({ another.public as RSAPublicKey }, { NOW })
    rejected { wrongKey.verify(valid, CLIENT, "original-nonce") }

    println("PASS: strict ChatGPT token decoding and signed OIDC claim verification")
}
