package com.aura.os.approval

fun main() {
    var now = 1000L
    val store = AuraApprovalGrantStore(monotonicMillis = { now })
    fun binding(uid: Int = 1000, user: Int = 0, action: String = "system.open_settings", args: String = "{}") =
        AuraApprovalGrantStore.Binding(uid, user, action, args.toByteArray(Charsets.UTF_8))
    val original = binding()
    var grant = store.issueGrant(original)
    check(store.consume(grant, original))
    check(!store.consume(grant, original)) // replay
    grant = store.issueGrant(original)
    check(!store.consume(grant, binding(uid = 1001)))
    check(!store.consume(grant, original))
    grant = store.issueGrant(original)
    check(!store.consume(grant, binding(user = 10)))
    grant = store.issueGrant(original)
    check(!store.consume(grant, binding(action = "system.reset")))
    grant = store.issueGrant(original)
    check(!store.consume(grant, binding(args = "{\"x\":1}")))
    grant = store.issueGrant(original, lifetimeMillis = 100)
    now += 100
    check(!store.consume(grant, original))
    grant = store.issueGrant(original)
    store.invalidateAll()
    check(!store.consume(grant, original))
    check(runCatching { store.issueGrant(original, 0) }.isFailure)
    check(runCatching { store.issueGrant(original, 60_001) }.isFailure)
    println("PASS: one-shot, replay, caller/user/action/argument binding, expiry and invalidation")
}
