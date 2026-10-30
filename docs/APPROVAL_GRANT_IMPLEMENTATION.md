# Approval Grant Store: standalone implementation

Status: **locally compiled/tested; no Android Binder or trusted UI integration**.

`AuraApprovalGrantStore` keeps short-lived grants only in process memory. It uses cryptographically random token identifiers, SHA-256 binding to the supplied canonical argument bytes, caller UID, user ID and action ID, plus an injected monotonic clock. Grants are removed atomically on any consume attempt, including mismatch and expiry. Service restart clears grants; `invalidateAll` supports explicit revocation.

Security limitation: `issueGrant` is `internal` for Kotlin module organization, NOT a hardened privilege boundary. A future trusted platform coordinator must authenticate a user approval and must be the only path to issuance. The caller UID/user ID must be derived from Binder, never from the request. The argument serialization must be canonical and must include all security-significant fields; otherwise equivalent/tampered payloads may be mishandled. Re-evaluate policy and capabilities at execution after consuming the grant. Do not accept caller-supplied risk level or approval assertions.

No SystemServer registration, protected Binder method, SELinux policy, trusted SystemUI approval, biometric confirmation, or device flash has been added. The existing service skeleton is deliberately not connected to this class.

Test command (host with Kotlin CLI):
```bash
kotlinc -Werror platform/aura/framework/src/com/aura/os/approval/AuraApprovalGrantStore.kt tests/framework/AuraApprovalGrantStoreTest.kt -d /tmp/aura-approval-test.jar
kotlin -classpath /tmp/aura-approval-test.jar com.aura.os.approval.AuraApprovalGrantStoreTestKt
```

The initial tests passed locally before this additional change. **The new capacity and concurrency tests have not yet been verified by a GitHub Actions run.**

Outstanding tokens are now bounded (default 1024) and expired entries are pruned on issuance. A two-thread contention assertion was added; successful GitHub CI execution is still unverified. Remaining: canonical encoding and signing identity, stronger randomized concurrency stress testing, pressure/DoS review, and integration only behind a verified trusted Android approval coordinator.
