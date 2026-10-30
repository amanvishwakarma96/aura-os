package com.aura.os.policy

import com.aura.os.action.AuraAction
import com.aura.os.action.AuraActionRegistry
import com.aura.os.model.AuraPolicyDecision
import com.aura.os.model.AuraRiskLevel

/** Decisions derive from trusted registered action metadata, not model input. */
class AuraPolicyEngine(private val registry: AuraActionRegistry) {
    fun evaluate(action: AuraAction, grantedCapabilities: Set<String> = emptySet()): AuraPolicyDecision {
        val descriptor = registry.descriptorFor(action.id) ?: return AuraPolicyDecision.DENY
        if (!descriptor.validates(action.parameters)) return AuraPolicyDecision.DENY
        if (!grantedCapabilities.containsAll(descriptor.requiredCapabilities)) return AuraPolicyDecision.DENY
        return when (descriptor.riskLevel) {
            AuraRiskLevel.LOW -> AuraPolicyDecision.ALLOW
            AuraRiskLevel.MEDIUM -> AuraPolicyDecision.CONFIRM
            AuraRiskLevel.HIGH -> AuraPolicyDecision.STRONG_CONFIRM
            AuraRiskLevel.CRITICAL -> AuraPolicyDecision.DENY
        }
    }
}
