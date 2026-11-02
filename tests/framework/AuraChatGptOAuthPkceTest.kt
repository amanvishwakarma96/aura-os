package com.aura.os.provider

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

private const val HOST_ID = "urn:uuid:3fc5f04a-c1eb-476f-9ec0-9d5aba61f424"
private const val CALLBACK = "http://127.0.0.1:1455/auth/callback"

private fun parameters(url: String): Map<String, String> =
    URI(url).rawQuery.split("&").associate { pair ->
        val parts = pair.split("=", limit = 2)
        URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8")
    }

private fun expectRejected(action: () -> Unit) {
    check(runCatching(action).isFailure) { "Expected fail-closed OAuth rejection" }
}

fun main() {
    val flow = AuraChatGptOAuthPkce()
    val start = flow.begin(HOST_ID, CALLBACK)
    val query = parameters(start.browserUrl)
    check(start.browserUrl.startsWith("https://auth.openai.com/api/accounts/authorize?"))
    check(query["client_id"] == "dynamic_agent_client")
    check(query["agent_name_hint"] == "AURA OS")
    check(query["ext_agent_host_id"] == HOST_ID)
    check(query["redirect_uri"] == CALLBACK)
    check(query["resource"] == AuraChatGptOAuthPkce.RESOURCE)
    check(query["scope"]!!.split(" ").contains(AuraChatGptPlanPolicy.REQUIRED_SCOPE))
    check(query["code_challenge_method"] == "S256")
    check(query["state"]!!.length >= 43)
    check(query["nonce"]!!.length >= 43)
    val state = query.getValue("state")

    expectRejected {
        flow.complete("http://localhost:1455/auth/callback?code=x&state=$state&client_id=oaiapp_test")
    }
    expectRejected {
        flow.complete("$CALLBACK?code=x&state=forged&client_id=oaiapp_test")
    }
    expectRejected {
        flow.complete("$CALLBACK?code=x&state=$state&state=$state&client_id=oaiapp_test")
    }
    expectRejected {
        flow.complete("$CALLBACK?code=x&state=$state")
    }
    // The last callback has matching state and is consumed, even if registration fails.
    expectRejected { flow.complete("$CALLBACK?code=x&state=$state&client_id=oaiapp_test") }

    val fresh = flow.begin(HOST_ID, CALLBACK)
    val freshState = parameters(fresh.browserUrl).getValue("state")
    check(freshState != state)
    val exchange = (flow.complete(
        "$CALLBACK?code=short-lived-code&state=$freshState&client_id=oaiapp_abc123"
    ) as AuraChatGptOAuthPkce.CallbackResult.Exchange).request
    check(exchange.code == "short-lived-code")
    check(exchange.clientId == "oaiapp_abc123")
    check(exchange.redirectUri == CALLBACK)
    check(exchange.resource == AuraChatGptOAuthPkce.RESOURCE)
    check(exchange.expectedNonce == parameters(fresh.browserUrl).getValue("nonce"))
    check(exchange.codeVerifier.length in 43..128)
    check("short-lived-code" !in exchange.toString())
    check(exchange.codeVerifier !in exchange.toString())
    val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
        MessageDigest.getInstance("SHA-256").digest(
            exchange.codeVerifier.toByteArray(StandardCharsets.US_ASCII)
        )
    )
    check(challenge == parameters(fresh.browserUrl).getValue("code_challenge"))
    expectRejected { flow.complete("$CALLBACK?code=replay&state=$freshState&client_id=oaiapp_abc123") }

    val returning = flow.begin(HOST_ID, CALLBACK, "oaiapp_existing")
    val returningQuery = parameters(returning.browserUrl)
    check(returningQuery["client_id"] == "oaiapp_existing")
    check(!returningQuery.containsKey("agent_name_hint"))
    val returningState = returningQuery.getValue("state")
    expectRejected {
        flow.complete("$CALLBACK?state=$returningState&code=x&client_id=oaiapp_other")
    }

    val newReturning = flow.begin(HOST_ID, CALLBACK, "oaiapp_existing")
    val reauth = flow.complete(
        "$CALLBACK?state=${parameters(newReturning.browserUrl).getValue("state")}&code=x"
    ) as AuraChatGptOAuthPkce.CallbackResult.Exchange
    check(reauth.request.clientId == "oaiapp_existing")

    val denied = flow.begin(HOST_ID, CALLBACK)
    val declined = flow.complete(
        "$CALLBACK?state=${parameters(denied.browserUrl).getValue("state")}&error=access_denied"
    ) as AuraChatGptOAuthPkce.CallbackResult.Denied
    check(declined.reason == "access_denied")
    expectRejected { flow.complete("$CALLBACK?state=x&code=y") }

    var clock = 100_000L
    val expiring = AuraChatGptOAuthPkce(nowMs = { clock })
    val waiting = expiring.begin(HOST_ID, CALLBACK)
    clock += 5 * 60 * 1000L
    expectRejected {
        expiring.complete(
            "$CALLBACK?state=${parameters(waiting.browserUrl).getValue("state")}&code=x"
        )
    }

    expectRejected { flow.begin(HOST_ID, "http://localhost:1455/auth/callback") }
    expectRejected { flow.begin(HOST_ID, "https://127.0.0.1:1455/auth/callback") }
    expectRejected { flow.begin(HOST_ID, "http://127.0.0.1:1455/not-callback") }
    expectRejected { flow.begin("not-a-host-id", CALLBACK) }
    val abandoned = flow.begin(HOST_ID, CALLBACK)
    flow.cancel()
    expectRejected { flow.complete("$CALLBACK?state=${parameters(abandoned.browserUrl).getValue("state")}&code=x") }
    println("PASS: ChatGPT subscription OAuth PKCE and callback security boundaries")
}
