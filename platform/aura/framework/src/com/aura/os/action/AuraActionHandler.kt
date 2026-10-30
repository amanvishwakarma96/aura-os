package com.aura.os.action

interface AuraActionHandler {
    val actionId: String
    fun validate(action: AuraAction): Result<Unit>
    fun execute(action: AuraAction): AuraActionResult
}

data class AuraActionResult(
    val success: Boolean,
    val message: String,
)
