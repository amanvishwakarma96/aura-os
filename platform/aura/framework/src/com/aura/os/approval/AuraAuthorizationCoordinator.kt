package com.aura.os.approval

import com.aura.os.action.AuraAction
import com.aura.os.action.AuraActionRegistry
import com.aura.os.action.AuraActionResult
import com.aura.os.model.AuraPolicyDecision
import com.aura.os.policy.AuraPolicyEngine

/**
 * Standalone authorization lifecycle. NOT an Android caller-identity or user-auth boundary.
 * Only trusted platform code may create CallerContext or invoke recordTrustedApproval.
 */
class AuraAuthorizationCoordinator(
    private val registry: AuraActionRegistry,
    private val policy: AuraPolicyEngine,
    private val grants: AuraApprovalGrantStore,
) {
    data class CallerContext(
        val uid: Int,
        val userId: Int,
        val signingIdentityDigest: String,
        val policyVersion: Long,
        val capabilities: Set<String>,
    )

    private fun binding(action: AuraAction, caller: CallerContext) =
        AuraApprovalGrantStore.Binding(
            callerUid = caller.uid,
            userId = caller.userId,
            actionId = action.id,
            canonicalArguments = AuraCanonicalArguments.encode(action.parameters),
            signingIdentityDigest = caller.signingIdentityDigest,
            policyVersion = caller.policyVersion,
        )

    fun propose(action: AuraAction, caller: CallerContext): AuraPolicyDecision =
        policy.evaluate(action, caller.capabilities)

    /**
     * Must be invoked exclusively by a future trusted UI/auth coordinator AFTER
     * verifying the specific proposal. No model/client-facing method may issue grants.
     */
    internal fun recordTrustedApproval(action: AuraAction, caller: CallerContext, strongAuthentication: Boolean): String? {
        val decision = policy.evaluate(action, caller.capabilities)
        if (decision != AuraPolicyDecision.CONFIRM && decision != AuraPolicyDecision.STRONG_CONFIRM) return null
        if (decision == AuraPolicyDecision.STRONG_CONFIRM && !strongAuthentication) return null
        return runCatching { grants.issueGrant(binding(action, caller)) }.getOrNull()
    }

    fun execute(action: AuraAction, caller: CallerContext, approvalToken: String? = null): AuraActionResult {
        val decision = policy.evaluate(action, caller.capabilities)
        if (decision == AuraPolicyDecision.DENY) return AuraActionResult(false, "Denied by policy")
        if (decision != AuraPolicyDecision.ALLOW) {
            val token = approvalToken ?: return AuraActionResult(false, "Approval required")
            val approved = runCatching { grants.consume(token, binding(action, caller)) }.getOrDefault(false)
            if (!approved) return AuraActionResult(false, "Approval invalid")
        }
        val handler = registry.handlerFor(action.id) ?: return AuraActionResult(false, "Unsupported action")
        val validation = handler.validate(action)
        if (validation.isFailure) return AuraActionResult(false, "Invalid action parameters")
        return handler.execute(action)
    }
}
