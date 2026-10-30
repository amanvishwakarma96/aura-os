package com.aura.os.approval

import com.aura.os.action.*
import com.aura.os.model.*
import com.aura.os.policy.AuraPolicyEngine

private class TestActionHandler(override val actionId: String) : AuraActionHandler {
    var calls = 0
    override fun validate(action: AuraAction): Result<Unit> = Result.success(Unit)
    override fun execute(action: AuraAction): AuraActionResult {
        calls++
        return AuraActionResult(true, "ok")
    }
}

fun main() {
    val registry = AuraActionRegistry()
    val engine = AuraAuthorizationCoordinator(registry, AuraPolicyEngine(registry), AuraApprovalGrantStore())
    val handler = TestActionHandler("test.review")
    registry.register(AuraActionDescriptor(handler.actionId, "Test", AuraRiskLevel.MEDIUM,
        parameters = mapOf("name" to AuraParameterType.STRING)), handler)
    val caller = AuraAuthorizationCoordinator.CallerContext(1000, 0, "trusted-signer", 1L, emptySet())
    val action = AuraAction(handler.actionId, "Test", AuraRiskLevel.LOW, mapOf("name" to "AURA"))
    check(engine.propose(action, caller) == AuraPolicyDecision.CONFIRM)
    check(!engine.execute(action, caller).success)
    val token = engine.recordTrustedApproval(action, caller, false)!!
    check(engine.execute(action, caller, token).success)
    check(!engine.execute(action, caller, token).success)
    check(handler.calls == 1)
    val next = engine.recordTrustedApproval(action, caller, false)!!
    check(!engine.execute(action.copy(parameters = mapOf("name" to "different")), caller, next).success)
    check(!engine.execute(action, caller, next).success)
    println("PASS: coordinator requires approval, rejects replay and changed arguments")
}
