package com.aura.os.tests

import com.aura.os.action.*
import com.aura.os.model.*
import com.aura.os.policy.AuraPolicyEngine

private class StubHandler(override val actionId: String) : AuraActionHandler {
    override fun validate(action: AuraAction): Result<Unit> = Result.success(Unit)
    override fun execute(action: AuraAction): AuraActionResult = AuraActionResult(true, "ok")
}

fun main() {
    val registry = AuraActionRegistry()
    val policy = AuraPolicyEngine(registry)
    check(registry.supportedActions().isEmpty())
    check(policy.evaluate(AuraAction("unknown.action", "unknown", AuraRiskLevel.LOW)) == AuraPolicyDecision.DENY)

    val settings = AuraActionDescriptor("system.open_settings", "Open settings", AuraRiskLevel.LOW)
    registry.register(settings, StubHandler(settings.id))
    check(registry.handlerFor(settings.id) != null)
    check(registry.supportedActions() == setOf(settings.id))
    check(runCatching { registry.register(settings, StubHandler(settings.id)) }.isFailure)
    check(runCatching { registry.register(settings, StubHandler("bad.id")) }.isFailure)
    check(runCatching { AuraActionDescriptor("", "bad", AuraRiskLevel.LOW) }.isFailure)
    check(policy.evaluate(AuraAction(settings.id, "spoofed", AuraRiskLevel.CRITICAL)) == AuraPolicyDecision.ALLOW)

    for ((risk, decision) in mapOf(
        AuraRiskLevel.LOW to AuraPolicyDecision.ALLOW,
        AuraRiskLevel.MEDIUM to AuraPolicyDecision.CONFIRM,
        AuraRiskLevel.HIGH to AuraPolicyDecision.STRONG_CONFIRM,
        AuraRiskLevel.CRITICAL to AuraPolicyDecision.DENY
    )) {
        val id = "test.${risk.name.lowercase()}"
        registry.register(AuraActionDescriptor(id, "Test", risk), StubHandler(id))
        // Caller tries spoofing LOW; trusted risk classification wins.
        check(policy.evaluate(AuraAction(id, "spoofed", AuraRiskLevel.LOW)) == decision)
    }

    val guardedId = "test.guarded"
    registry.register(
        AuraActionDescriptor(
            guardedId, "Guarded", AuraRiskLevel.LOW,
            requiredCapabilities = setOf("aura.capability.read"),
            parameters = mapOf("name" to AuraParameterType.STRING, "enabled" to AuraParameterType.BOOLEAN)
        ), StubHandler(guardedId)
    )
    val valid = AuraAction(guardedId, "guarded", AuraRiskLevel.LOW, mapOf("name" to "safe", "enabled" to true))
    check(policy.evaluate(valid) == AuraPolicyDecision.DENY)
    check(policy.evaluate(valid, setOf("aura.capability.read")) == AuraPolicyDecision.ALLOW)
    for (bad in listOf(
        mapOf("name" to "safe"),
        mapOf("name" to "safe", "enabled" to "true"),
        mapOf("name" to "safe", "enabled" to true, "extra" to 123),
        mapOf("name" to null, "enabled" to true),
        mapOf("name" to "x".repeat(4097), "enabled" to true)
    )) {
        check(policy.evaluate(valid.copy(parameters = bad), setOf("aura.capability.read")) == AuraPolicyDecision.DENY)
    }
    println("PASS: AURA framework registry, schema and trusted policy tests")
}
