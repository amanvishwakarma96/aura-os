package com.aura.os.provider

import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

private const val HOST = "urn:uuid:3fc5f04a-c1eb-476f-9ec0-9d5aba61f424"

private fun stateFrom(url: String): String {
    val raw = URI(url).rawQuery.split('&').first { it.startsWith("state=") }
    return URLDecoder.decode(raw.substringAfter('='), "UTF-8")
}

private data class CapturedCallback(
    val result: AuraChatGptOAuthPkce.CallbackResult?,
    val error: Throwable?,
    val httpResponse: String,
)

private fun probe(
    listener: AuraChatGptLoopbackListener,
    transaction: AuraChatGptOAuthPkce,
    target: String,
    method: String = "GET",
    hostHeader: String? = null,
): CapturedCallback {
    val outcome = AtomicReference<AuraChatGptOAuthPkce.CallbackResult?>()
    val failure = AtomicReference<Throwable?>()
    val server = thread(name = "aura-loopback-test", isDaemon = true) {
        try {
            outcome.set(listener.awaitCallback(transaction))
        } catch (exception: Throwable) {
            failure.set(exception)
        }
    }
    val port = URI(listener.redirectUri).port
    val response = Socket("127.0.0.1", port).use { socket ->
        socket.soTimeout = 5_000
        val host = hostHeader ?: "127.0.0.1:$port"
        val bytes = (
            "$method $target HTTP/1.1\r\n" +
            "Host: $host\r\n" +
            "Connection: close\r\n\r\n"
        ).toByteArray(Charsets.US_ASCII)
        socket.getOutputStream().write(bytes)
        socket.getOutputStream().flush()
        socket.getInputStream().bufferedReader().readText()
    }
    server.join(5_000)
    check(!server.isAlive) { "Callback server did not finish" }
    return CapturedCallback(outcome.get(), failure.get(), response)
}

fun main() {
    val flow = AuraChatGptOAuthPkce()
    AuraChatGptLoopbackListener().use { listener ->
        check(listener.boundToLoopback)
        val authorization = flow.begin(HOST, listener.redirectUri)
        val state = stateFrom(authorization.browserUrl)
        val result = probe(
            listener, flow,
            "/auth/callback?code=secret-code&state=$state&client_id=oaiapp_test"
        )
        check(result.error == null) { "Expected callback success: ${result.error}" }
        val exchange = (result.result as AuraChatGptOAuthPkce.CallbackResult.Exchange).request
        check(exchange.code == "secret-code")
        check(exchange.redirectUri == listener.redirectUri)
        check(result.httpResponse.startsWith("HTTP/1.1 200 OK"))
        check("Cache-Control: no-store" in result.httpResponse)
        check("Referrer-Policy: no-referrer" in result.httpResponse)
        check("Content-Security-Policy:" in result.httpResponse)
        check("secret-code" !in result.httpResponse)
        check(state !in result.httpResponse)
        check(runCatching { listener.awaitCallback(flow) }.isFailure)
    }

    AuraChatGptLoopbackListener().use { listener ->
        val state = stateFrom(flow.begin(HOST, listener.redirectUri).browserUrl)
        val result = probe(listener, flow,
            "/auth/callback?state=$state&error=access_denied")
        check(result.error == null)
        check(result.result is AuraChatGptOAuthPkce.CallbackResult.Denied)
        check("Sign-in declined" in result.httpResponse)
    }

    AuraChatGptLoopbackListener().use { listener ->
        val state = stateFrom(flow.begin(HOST, listener.redirectUri).browserUrl)
        val result = probe(listener, flow,
            "/auth/callback?state=$state&code=secret&client_id=oaiapp_test",
            hostHeader = "attacker.example")
        check(result.error != null)
        check(result.httpResponse.startsWith("HTTP/1.1 400 Bad Request"))
        check("secret" !in result.httpResponse)
    }

    AuraChatGptLoopbackListener().use { listener ->
        val state = stateFrom(flow.begin(HOST, listener.redirectUri).browserUrl)
        val result = probe(listener, flow,
            "/auth/callback?state=$state&code=secret&client_id=oaiapp_test",
            method = "POST")
        check(result.error != null)
        check(result.httpResponse.startsWith("HTTP/1.1 400 Bad Request"))
    }

    AuraChatGptLoopbackListener().use { listener ->
        val state = stateFrom(flow.begin(HOST, listener.redirectUri).browserUrl)
        val result = probe(listener, flow,
            "/auth/callback?state=forged&code=secret&client_id=oaiapp_test")
        check(result.error != null)
        check(result.httpResponse.startsWith("HTTP/1.1 400 Bad Request"))
        check(state !in result.httpResponse)
    }

    check(runCatching { AuraChatGptLoopbackListener(0) }.isFailure)
    check(runCatching { AuraChatGptLoopbackListener(400_000) }.isFailure)
    println("PASS: real loopback socket callback acceptance, denial, host/method/state checks")
}
