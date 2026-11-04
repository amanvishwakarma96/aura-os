package com.aura.os.provider

import java.net.URI
import java.net.URLDecoder

private const val HOST_ID = "urn:uuid:3fc5f04a-c1eb-476f-9ec0-9d5aba61f424"
private const val REDIRECT = "http://127.0.0.1:1455/auth/callback"

private fun callback(): AuraChatGptOAuthPkce.CodeExchange {
    val flow = AuraChatGptOAuthPkce()
    val url = flow.begin(HOST_ID, REDIRECT).browserUrl
    val state = URI(url).rawQuery.split('&')
        .map { it.split('=', limit = 2) }
        .first { it[0] == "state" }[1]
    val result = flow.complete(
        "$REDIRECT?code=issued-code&state=$state&client_id=oaiapp_accountA"
    )
    return (result as AuraChatGptOAuthPkce.CallbackResult.Exchange).request
}

private class MemoryStore : AuraChatGptSessionManager.SessionStore {
    private val values = mutableMapOf<AuraChatGptSessionManager.AccountKey,
        AuraChatGptSessionManager.Session>()
    var rejectNextWrite = false

    @Synchronized override fun read(key: AuraChatGptSessionManager.AccountKey) = values[key]

    @Synchronized override fun replaceAtomic(
        key: AuraChatGptSessionManager.AccountKey,
        expectedGeneration: Long?,
        session: AuraChatGptSessionManager.Session,
    ): Boolean {
        if (rejectNextWrite) {
            rejectNextWrite = false
            return false
        }
        if (values[key]?.generation != expectedGeneration) return false
        values[key] = session
        return true
    }

    @Synchronized override fun delete(key: AuraChatGptSessionManager.AccountKey) {
        values.remove(key)
    }
}

private class FakeEndpoint : AuraChatGptTokenEndpoint {
    var response = result("access-A", "refresh-A")
    var lastRequest: AuraChatGptTokenRequest? = null
    var calls = 0
    override fun exchange(request: AuraChatGptTokenRequest): AuraChatGptTokenResponse {
        lastRequest = request
        calls++
        return response
    }
}

private fun result(
    access: String,
    refresh: String,
    scopes: String = "openid profile chatgpt.tokens.use.direct offline_access resource.invoke",
    type: String = "Bearer",
    earliest: Long? = null,
) = AuraChatGptTokenResponse(
    accessToken = access,
    refreshToken = refresh,
    idToken = "signed-id-jwt-placeholder",
    tokenType = type,
    expiresInSeconds = 3600L,
    scope = scopes,
    earliestRefreshAtEpochSeconds = earliest,
)

private class VerifiedStub : AuraChatGptIdTokenVerifier {
    var subject = "account-A"
    var expectedNonceObserved: String? = null
    override fun verify(
        idToken: String,
        clientId: String,
        expectedNonce: String?,
    ): AuraChatGptIdTokenVerifier.VerifiedIdentity {
        check(idToken == "signed-id-jwt-placeholder")
        check(clientId == "oaiapp_accountA")
        expectedNonceObserved = expectedNonce
        return AuraChatGptIdTokenVerifier.VerifiedIdentity(
            "https://auth.openai.com", subject
        )
    }
}

private fun rejected(action: () -> Unit) {
    check(runCatching(action).isFailure) { "Expected session rejection" }
}

fun main() {
    val exchange = callback()
    val endpoint = FakeEndpoint()
    val verifier = VerifiedStub()
    val store = MemoryStore()
    var clock = 2000L
    val manager = AuraChatGptSessionManager(endpoint, verifier, store) { clock }
    val key = AuraChatGptSessionManager.AccountKey(0, exchange.clientId)

    check(AuraChatGptTokenRequest.ENDPOINT ==
        "https://auth.openai.com/api/accounts/oauth/token")
    val grant = AuraChatGptTokenRequest.AuthorizationCode(exchange)
    val fields = grant.fields()
    check(fields["client_id"] == "oaiapp_accountA")
    check(fields["grant_type"] == "authorization_code")
    check(fields["code"] == "issued-code")
    check(fields["code_verifier"] == exchange.codeVerifier)
    check(fields["redirect_uri"] == REDIRECT)
    check(fields["resource"] == "https://api.openai.com/v1")
    check("client_secret" !in fields)
    check("api_key" !in fields)
    check("issued-code" !in grant.toString())
    check(grant.formBody().contains("grant_type=authorization_code"))

    val connected = manager.install(0, exchange)
    check(connected.planUsageGranted)
    check(connected.key == key)
    check(connected.generation == 1L)
    check(connected.expiresAtEpochSeconds == 5600L)
    check(verifier.expectedNonceObserved == exchange.expectedNonce)
    check(connected.subject == "account-A")
    check("access-A" !in connected.toString())
    check("refresh-A" !in connected.toString())
    check(manager.lookup(key) === connected)

    rejected { manager.install(0, exchange) } // No silent returning-account overwrite.
    rejected { manager.install(0, exchange, expectedReturningSubject = "account-B") }
    check(manager.lookup(key) === connected)

    endpoint.response = result("access-B", "refresh-B")
    val rotated = manager.refresh(key)
    check(rotated.accessToken == "access-B")
    check(rotated.refreshToken == "refresh-B")
    check(rotated.generation == 2L)
    check(manager.lookup(key)?.refreshToken == "refresh-B")
    check(endpoint.lastRequest is AuraChatGptTokenRequest.Refresh)
    val refreshFields = endpoint.lastRequest!!.fields()
    check(refreshFields["refresh_token"] == "refresh-A")
    check(refreshFields["client_id"] == "oaiapp_accountA")
    check(refreshFields["grant_type"] == "refresh_token")
    check("scope" !in refreshFields)
    check("code" !in refreshFields)
    check("client_secret" !in refreshFields)
    check("refresh-A" !in endpoint.lastRequest.toString())
    check(verifier.expectedNonceObserved == null)

    verifier.subject = "account-B"
    endpoint.response = result("evil", "evil-refresh")
    rejected { manager.refresh(key) }
    check(manager.lookup(key) === rotated)
    verifier.subject = "account-A"

    store.rejectNextWrite = true
    rejected { manager.refresh(key) }
    check(manager.lookup(key) === rotated)

    val otherUser = AuraChatGptSessionManager.AccountKey(10, exchange.clientId)
    check(manager.lookup(otherUser) == null)
    rejected { manager.refresh(otherUser) }
    rejected { AuraChatGptSessionManager.AccountKey(-1, exchange.clientId) }
    rejected { AuraChatGptSessionManager.AccountKey(0, "dynamic_agent_client") }

    // Identity only is allowed to remain signed-in, but must NOT allow plan usage.
    endpoint.response = result(
        "access-id-only", "refresh-id-only", scopes = "openid profile email"
    )
    val identityOnly = manager.install(10, exchange)
    check(!identityOnly.planUsageGranted)
    check(manager.lookup(otherUser) === identityOnly)
    check(!identityOnly.grantedScopes.contains(AuraChatGptPlanPolicy.REQUIRED_SCOPE))

    // Simulated refresh-earliest bound.
    endpoint.response = result("late", "late-refresh", earliest = clock + 200L)
    val delayed = manager.refresh(key)
    val before = endpoint.calls
    rejected { manager.refresh(key) }
    check(endpoint.calls == before)
    clock += 201L
    endpoint.response = result("after", "after-refresh")
    check(manager.refresh(key).generation == delayed.generation + 1L)

    manager.disconnectLocally(key)
    check(manager.lookup(key) == null)

    // No real OIDC verifier installed: default must fail closed.
    val unverified = AuraChatGptSessionManager(endpoint,
        store = MemoryStore(), nowSeconds = { clock })
    rejected { unverified.install(0, exchange) }

    rejected { result("a", "b", type = "MAC") }
    rejected { result("a", "b", scopes = "") }
    check("access-A" !in endpoint.response.toString())

    println("PASS: ChatGPT public OAuth grants, verified identity, scope and atomic refresh")
}
