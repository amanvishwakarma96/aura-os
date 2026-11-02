package com.aura.os.provider

import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets

/**
 * A single-use, loopback-only OAuth callback transport.
 *
 * Bind BEFORE generating the authorization URL / launching a system browser.
 * Call awaitCallback from a background thread. Never expose this listener on
 * 0.0.0.0, persist a code, or put an OAuth callback in a logging system.
 *
 * This listener does not perform a token exchange, OIDC verification or
 * ChatGPT subscription authorization.
 */
class AuraChatGptLoopbackListener(
    acceptTimeoutMs: Int = 180_000,
) : AutoCloseable {
    init {
        require(acceptTimeoutMs in 1..300_000) { "Invalid callback timeout" }
    }

    private val listener = ServerSocket(
        0,
        1,
        InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
    ).apply {
        soTimeout = acceptTimeoutMs
    }

    val redirectUri: String = "http://127.0.0.1:${listener.localPort}/auth/callback"
    val boundToLoopback: Boolean = listener.inetAddress.isLoopbackAddress

    private var consumed = false

    /**
     * At most one request is processed. Invalid requests fail closed rather
     * than allowing a remote web page to brute-force the callback state.
     */
    fun awaitCallback(
        transaction: AuraChatGptOAuthPkce,
    ): AuraChatGptOAuthPkce.CallbackResult {
        synchronized(this) {
            check(!consumed) { "OAuth callback listener already consumed" }
            consumed = true
        }
        try {
            listener.accept().use { peer ->
                peer.soTimeout = 5_000
                require(peer.inetAddress.isLoopbackAddress) { "Non-loopback callback rejected" }
                return try {
                    val target = parseRequest(peer)
                    val outcome = transaction.complete("http://127.0.0.1:${listener.localPort}$target")
                    when (outcome) {
                        is AuraChatGptOAuthPkce.CallbackResult.Exchange ->
                            sendResponse(peer, 200, "Sign-in received. You can return to AURA OS.")
                        is AuraChatGptOAuthPkce.CallbackResult.Denied ->
                            sendResponse(peer, 200, "Sign-in declined. You can return to AURA OS.")
                    }
                    outcome
                } catch (failure: Exception) {
                    // Never include the callback URL, state, authorization code,
                    // verifier or exception message in the browser response.
                    runCatching { sendResponse(peer, 400, "Sign-in was not accepted.") }
                    throw failure
                }
            }
        } finally {
            close()
        }
    }

    private fun parseRequest(peer: Socket): String {
        val header = ByteArrayOutputStream()
        var lastFour = 0
        while (header.size() < 8192) {
            val byte = peer.getInputStream().read()
            require(byte >= 0) { "Incomplete OAuth HTTP request" }
            require(byte >= 32 || byte == 13 || byte == 10 || byte == 9) {
                "Invalid HTTP header byte"
            }
            header.write(byte)
            lastFour = (lastFour shl 8) or byte
            if (lastFour == 0x0d0a0d0a) break
        }
        require(lastFour == 0x0d0a0d0a) { "Oversized or incomplete HTTP headers" }
        val lines = header.toString(StandardCharsets.US_ASCII.name()).split("\r\n")
        val request = lines.firstOrNull()?.split(' ') ?: emptyList()
        require(request.size == 3 && request[0] == "GET" && request[2] == "HTTP/1.1") {
            "Only HTTP/1.1 GET is permitted"
        }
        val hostHeaders = lines.drop(1).filter {
            it.startsWith("Host:", ignoreCase = true)
        }
        require(hostHeaders.size == 1) { "Expected exactly one Host header" }
        require(
            hostHeaders.single().substringAfter(':').trim() ==
                "127.0.0.1:${listener.localPort}"
        ) { "Unexpected callback Host" }
        val target = request[1]
        require(target.startsWith("/auth/callback?") && target.length <= 8192) {
            "Unexpected callback target"
        }
        return target
    }

    private fun sendResponse(peer: Socket, code: Int, message: String) {
        val body = ("<!doctype html><meta charset=\"utf-8\">" +
            "<title>AURA OS</title><p>$message</p>").toByteArray(StandardCharsets.UTF_8)
        val heading = if (code == 200) "200 OK" else "400 Bad Request"
        val headers = (
            "HTTP/1.1 $heading\r\n" +
            "Content-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Referrer-Policy: no-referrer\r\n" +
            "X-Content-Type-Options: nosniff\r\n" +
            "Content-Security-Policy: default-src 'none'; base-uri 'none'; frame-ancestors 'none'\r\n" +
            "Connection: close\r\n\r\n"
        ).toByteArray(StandardCharsets.US_ASCII)
        peer.getOutputStream().write(headers)
        peer.getOutputStream().write(body)
        peer.getOutputStream().flush()
    }

    override fun close() {
        listener.close()
    }
}
