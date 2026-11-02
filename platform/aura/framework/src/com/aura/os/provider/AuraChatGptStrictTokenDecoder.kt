package com.aura.os.provider

/**
 * Exact-type decoder for the documented Sign in with ChatGPT token response.
 * No permissive coercion of numbers or strings, no duplicate JSON properties,
 * and no accidental acceptance of a JSON error object as credentials.
 */
object AuraChatGptStrictTokenDecoder {
    private val permitted = setOf(
        "access_token", "refresh_token", "id_token", "token_type",
        "expires_in", "scope", "earliest_refresh_at"
    )

    fun decode(json: String): AuraChatGptTokenResponse {
        val fields = AuraStrictJson.objectValue(json)
        require(fields.keys.all { it in permitted }) {
            "Unexpected OAuth response property"
        }
        fun required(key: String): String =
            fields[key] as? String ?: error("Missing or wrongly typed OAuth property: $key")
        fun integer(key: String): Long =
            fields[key] as? Long ?: error("Missing or wrongly typed OAuth integer: $key")
        val earliest = if (fields.containsKey("earliest_refresh_at")) {
            fields["earliest_refresh_at"] as? Long
                ?: error("Invalid earliest_refresh_at")
        } else null
        return AuraChatGptTokenResponse(
            accessToken = required("access_token"),
            refreshToken = required("refresh_token"),
            idToken = required("id_token"),
            tokenType = required("token_type"),
            expiresInSeconds = integer("expires_in"),
            scope = required("scope"),
            earliestRefreshAtEpochSeconds = earliest,
        )
    }
}
