package com.aura.os.approval

fun main() {
    val ordered = AuraCanonicalArguments.encode(mapOf("alpha" to 1, "beta" to true, "name" to "AURA"))
    val shuffled = AuraCanonicalArguments.encode(linkedMapOf("name" to "AURA", "beta" to true, "alpha" to 1))
    check(ordered.contentEquals(shuffled))
    check(!ordered.contentEquals(AuraCanonicalArguments.encode(mapOf("alpha" to 2, "beta" to true, "name" to "AURA"))))
    check(!AuraCanonicalArguments.encode(mapOf("x" to "1")).contentEquals(AuraCanonicalArguments.encode(mapOf("x" to 1))))
    check(runCatching { AuraCanonicalArguments.encode(mapOf("x" to null)) }.isFailure)
    check(runCatching { AuraCanonicalArguments.encode(mapOf("x" to 1L)) }.isFailure)
    check(runCatching { AuraCanonicalArguments.encode(mapOf("x" to "x".repeat(4097))) }.isFailure)
    check(runCatching { AuraCanonicalArguments.encode(mapOf("x" to listOf(1))) }.isFailure)
    println("PASS: deterministic, type-bound canonical argument encoding")
}
