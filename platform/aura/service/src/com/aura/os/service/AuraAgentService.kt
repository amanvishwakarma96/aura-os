package com.aura.os.service

import android.content.Context
import android.util.Slog
import com.aura.os.action.AuraAction
import com.aura.os.action.AuraActionRegistry
import com.aura.os.action.AuraActionResult
import com.aura.os.model.AuraPolicyDecision
import com.aura.os.policy.AuraPolicyEngine

/**
 * Initial service-domain skeleton.
 *
 * This is intentionally NOT wired to SystemServer yet. Binder/AIDL, caller checks,
 * boot registration and SELinux policy are Phase 2 tasks and must be implemented
 * together so the service is never exposed insecurely.
 */
class AuraAgentService(
    private val context: Context,
    private val registry: AuraActionRegistry,
    private val policy: AuraPolicyEngine,
) {
    companion object {
        private const val TAG = "AuraAgentService"
    }

    fun propose(action: AuraAction): AuraPolicyDecision = policy.evaluate(action)

    fun executeApproved(action: AuraAction): AuraActionResult {
        val decision = policy.evaluate(action)
        if (decision != AuraPolicyDecision.ALLOW) {
            return AuraActionResult(false, "Action requires approval or is denied: $decision")
        }

        val handler = registry.handlerFor(action.id)
            ?: return AuraActionResult(false, "Unsupported action: ${action.id}")

        val validation = handler.validate(action)
        if (validation.isFailure) {
            return AuraActionResult(false, validation.exceptionOrNull()?.message ?: "Validation failed")
        }

        Slog.i(TAG, "Executing approved action id=${action.id}")
        return handler.execute(action)
    }
}
