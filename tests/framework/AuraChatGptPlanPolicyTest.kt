package com.aura.os.provider

fun main() {
    val ready = AuraChatGptPlanPolicy.Snapshot(
        optionalProviderEnabled = true,
        signedIn = true,
        grantedScopes = setOf(AuraChatGptPlanPolicy.REQUIRED_SCOPE),
        sessionValid = true,
        allowanceAvailable = true,
        networkAvailable = true,
    )
    fun rejects(snapshot: AuraChatGptPlanPolicy.Snapshot, expected: AuraChatGptPlanPolicy.State) {
        check(AuraChatGptPlanPolicy.evaluate(snapshot) == expected)
        check(!AuraChatGptPlanPolicy.permitsCloudInference(snapshot))
    }
    rejects(AuraChatGptPlanPolicy.Snapshot(), AuraChatGptPlanPolicy.State.LOCAL_ONLY)
    rejects(ready.copy(signedIn = false), AuraChatGptPlanPolicy.State.SIGN_IN_REQUIRED)
    rejects(ready.copy(grantedScopes = setOf("openid", "profile")),
        AuraChatGptPlanPolicy.State.CONSENT_REQUIRED)
    rejects(ready.copy(sessionValid = false), AuraChatGptPlanPolicy.State.SESSION_EXPIRED)
    rejects(ready.copy(allowanceAvailable = false), AuraChatGptPlanPolicy.State.LIMIT_REACHED)
    rejects(ready.copy(networkAvailable = false), AuraChatGptPlanPolicy.State.OFFLINE)
    check(AuraChatGptPlanPolicy.permitsCloudInference(ready))
    println("PASS: ChatGPT Plus plan usage consent and availability gates")
}
