package com.aura.os.action

import com.aura.os.model.AuraRiskLevel

data class AuraAction(
    val id: String,
    val description: String,
    val riskLevel: AuraRiskLevel,
    val parameters: Map<String, Any?> = emptyMap(),
)
