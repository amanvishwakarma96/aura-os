package com.aura.os.policy

import com.aura.os.action.AuraAction
import com.aura.os.model.AuraPolicyDecision
import com.aura.os.model.AuraRiskLevel

class AuraPolicyEngine {
    fun evaluate(action: AuraAction): AuraPolicyDecision = when (action.riskLevel) {
        AuraRiskLevel.LOW -> AuraPolicyDecision.ALLOW
        AuraRiskLevel.MEDIUM -> AuraPolicyDecision.CONFIRM
        AuraRiskLevel.HIGH -> AuraPolicyDecision.STRONG_CONFIRM
        AuraRiskLevel.CRITICAL -> AuraPolicyDecision.DENY
    }
}
