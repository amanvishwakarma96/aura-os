package com.aura.os.approval

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

fun main() {
    var now = 1000L
    val store = AuraApprovalGrantStore(monotonicMillis = { now })
    fun binding(uid: Int = 1000, user: Int = 0, action: String = "system.open_settings", args: String = "{}", identity: String = "sha256:trusted-signer", version: Long = 1L) =
        AuraApprovalGrantStore.Binding(uid, user, action, args.toByteArray(Charsets.UTF_8), identity, version)
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
    grant = store.issueGrant(original)
    check(!store.consume(grant, binding(identity = "sha256:other-signer")))
    grant = store.issueGrant(original)
    check(!store.consume(grant, binding(version = 2L)))
    check(runCatching { store.issueGrant(binding(identity = "")) }.isFailure)
    check(runCatching { store.issueGrant(binding(version = 0L)) }.isFailure)
    grant = store.issueGrant(original, lifetimeMillis = 100)
    now += 100
    check(!store.consume(grant, original))
    grant = store.issueGrant(original)
    store.invalidateAll()
    check(!store.consume(grant, original))
    check(runCatching { store.issueGrant(original, 0) }.isFailure)
    check(runCatching { store.issueGrant(original, 60_001) }.isFailure)
    // Bounded storage and expired grant cleanup.
    val bounded = AuraApprovalGrantStore(monotonicMillis = { now }, maxPending = 1)
    val first = bounded.issueGrant(original, 10)
    check(runCatching { bounded.issueGrant(original) }.isFailure)
    now += 10
    val replacement = bounded.issueGrant(original)
    check(!bounded.consume(first, original))
    check(bounded.consume(replacement, original))

    // Two simultaneous consumers must never both succeed.
    val concurrent = AuraApprovalGrantStore()
    val shared = concurrent.issueGrant(original)
    val start = CountDownLatch(1)
    val complete = CountDownLatch(2)
    val successes = AtomicInteger(0)
    repeat(2) {
        thread {
            try {
                start.await()
                if (concurrent.consume(shared, original)) successes.incrementAndGet()
            } finally {
                complete.countDown()
            }
        }
    }
    start.countDown()
    complete.await()
    check(successes.get() == 1)
    println("PASS: one-shot, replay, binding, expiry, bounded storage and concurrent consumption")
}
