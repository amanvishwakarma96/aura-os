package com.aura.os.tests
import com.aura.os.action.*
import com.aura.os.model.*
import com.aura.os.policy.AuraPolicyEngine

private class StubHandler(override val actionId: String) : AuraActionHandler {
    override fun validate(action: AuraAction): Result<Unit> = Result.success(Unit)
    override fun execute(action: AuraAction) = AuraActionResult(true, "ok")
}
fun main() {
    val registry = AuraActionRegistry()
    check(registry.supportedActions().isEmpty())
    check(registry.handlerFor("system.open_settings") == null)
    registry.register(StubHandler("system.open_settings"))
    check(registry.handlerFor("system.open_settings") != null)
    check(registry.supportedActions() == setOf("system.open_settings"))
    check(runCatching { registry.register(StubHandler("system.open_settings")) }.isFailure)
    check(runCatching { registry.register(StubHandler("")) }.isFailure)
    val policy = AuraPolicyEngine()
    val expected = mapOf(
        AuraRiskLevel.LOW to AuraPolicyDecision.ALLOW,
        AuraRiskLevel.MEDIUM to AuraPolicyDecision.CONFIRM,
        AuraRiskLevel.HIGH to AuraPolicyDecision.STRONG_CONFIRM,
        AuraRiskLevel.CRITICAL to AuraPolicyDecision.DENY,
    )
    for ((risk, decision) in expected) {
        check(policy.evaluate(AuraAction("test.action", "smoke test", risk)) == decision)
    }
    println("PASS: AURA framework registry and policy smoke tests")
}
