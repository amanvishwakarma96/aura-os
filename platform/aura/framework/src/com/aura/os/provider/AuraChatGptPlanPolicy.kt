package com.aura.os.provider

/**
 * Deliberately contains no OAuth token or billing logic.
 * Identity, granted scopes, consent and usage gates must come from trusted
 * integrations, never from a model response or device-action request.
 */
object AuraChatGptPlanPolicy {
    const val REQUIRED_SCOPE = "chatgpt.tokens.use.direct"

    enum class State {
        LOCAL_ONLY,
        SIGN_IN_REQUIRED,
        CONSENT_REQUIRED,
        SESSION_EXPIRED,
        LIMIT_REACHED,
        OFFLINE,
        CLOUD_READY,
    }

    data class Snapshot(
        val optionalProviderEnabled: Boolean = false,
        val signedIn: Boolean = false,
        val grantedScopes: Set<String> = emptySet(),
        val sessionValid: Boolean = false,
        val allowanceAvailable: Boolean = false,
        val networkAvailable: Boolean = false,
    )

    fun evaluate(snapshot: Snapshot): State = when {
        !snapshot.optionalProviderEnabled -> State.LOCAL_ONLY
        !snapshot.signedIn -> State.SIGN_IN_REQUIRED
        REQUIRED_SCOPE !in snapshot.grantedScopes -> State.CONSENT_REQUIRED
        !snapshot.sessionValid -> State.SESSION_EXPIRED
        !snapshot.allowanceAvailable -> State.LIMIT_REACHED
        !snapshot.networkAvailable -> State.OFFLINE
        else -> State.CLOUD_READY
    }

    fun permitsCloudInference(snapshot: Snapshot): Boolean =
        evaluate(snapshot) == State.CLOUD_READY
}
