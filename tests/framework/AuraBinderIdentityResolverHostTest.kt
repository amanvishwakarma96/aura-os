package com.aura.os.binder

private class FakeCaller(
    var uid: Int = 10001,
    var pid: Int = 42001,
    var ownPid: Int = 100,
    var user: Int = 0,
    var permission: Boolean = true,
    var packages: List<String> = listOf("com.aura.trusted"),
    var signatures: Set<String> = setOf("a".repeat(64)),
) : AuraBinderIdentityResolver.Platform {
    override fun callerUid() = uid
    override fun callerPid() = pid
    override fun servicePid() = ownPid
    override fun userIdForUid(uid: Int) = user
    override fun hasCallingPermission(permission: String) = this.permission
    override fun packagesForUid(uid: Int) = packages
    override fun currentSigningDigests(packageName: String) = signatures
}

fun main() {
    val signer = "a".repeat(64)
    fun resolver(fake: FakeCaller, policyVersion: Long = 1L) =
        AuraBinderIdentityResolver(
            fake,
            mapOf("com.aura.trusted" to setOf(signer)),
            mapOf("com.aura.trusted" to setOf("read.settings")),
            { policyVersion },
        )
    val trusted = resolver(FakeCaller()).resolve()
    check(trusted.uid == 10001)
    check(trusted.capabilities == setOf("read.settings"))
    check(runCatching { resolver(FakeCaller(permission = false)).resolve() }.isFailure)
    check(runCatching { resolver(FakeCaller(packages = emptyList())).resolve() }.isFailure)
    check(runCatching { resolver(FakeCaller(packages = listOf("com.other"))).resolve() }.isFailure)
    check(runCatching { resolver(FakeCaller(packages = listOf("com.aura.trusted", "com.other"))).resolve() }.isFailure)
    check(runCatching { resolver(FakeCaller(signatures = setOf("b".repeat(64)))).resolve() }.isFailure)
    check(runCatching { resolver(FakeCaller(user = 10)).resolve() }.isFailure)
    check(runCatching { resolver(FakeCaller(pid = 100)).resolve() }.isFailure)
    check(runCatching { resolver(FakeCaller(), policyVersion = 0).resolve() }.isFailure)
    println("PASS: Binder identity resolver fake-platform negative tests")
}
