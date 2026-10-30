package com.aura.os.approval

fun main() {
    val valid = AuraTrustedCaller(10001, 0, "verified-signing-digest", 1L, setOf("settings.read"))
    check(valid.toContext().uid == 10001)
    check(valid.toContext().capabilities == setOf("settings.read"))
    check(runCatching { AuraTrustedCaller(-1, 0, "digest", 1, emptySet()) }.isFailure)
    check(runCatching { AuraTrustedCaller(1, -1, "digest", 1, emptySet()) }.isFailure)
    check(runCatching { AuraTrustedCaller(1, 0, "", 1, emptySet()) }.isFailure)
    check(runCatching { AuraTrustedCaller(1, 0, "digest", 0, emptySet()) }.isFailure)
    check(runCatching { AuraTrustedCaller(1, 0, "digest", 1, setOf("")) }.isFailure)
    println("PASS: trusted caller context validation")
}
