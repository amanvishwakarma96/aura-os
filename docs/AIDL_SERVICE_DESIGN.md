# AURA Agent Service — AIDL/Binder Design (design only)

**Status: draft; not registered with SystemServer or available to applications.**

## Trust boundary
The Android Binder entrypoint must capture calling UID/PID before any identity-clearing operation. The server—not the request or an LLM—derives authorized capabilities from trusted Android package/signature and permission checks. Never accept supplied caller UID, risk level, approval decision or capability grants as authority.

## Proposed API
`IAuraAgentService` exposes only:
- `proposeAction(AuraActionRequest request)` -> `AuraActionProposal`
- `executeAction(AuraExecutionRequest request)` -> `AuraExecutionResult`
- `getProposalStatus(String proposalId)` -> `AuraActionProposal`

There is **no** general shell command, filesystem API, arbitrary Intent dispatcher or unrestricted Binder passthrough.

## Action lifecycle
1. Binder authenticates caller and enforces signature permission plus allowlisted UID/package and SELinux policy.
2. Copy and size-limit an incoming parcel. Validate action ID and typed arguments against the trusted registry. Deny unknown operations or malformed parameters.
3. Evaluate authoritative risk and capabilities; record a redacted audit event.
4. `ALLOW`: issue a short-lived server-side proposal bound to caller identity and canonical argument digest; execution rechecks policy.
5. `CONFIRM`: present the exact action and parameter summary in a trusted SystemUI surface. Persist a one-shot, expiring approval grant, with a proposal nonce, caller identity and parameter digest.
6. `STRONG_CONFIRM`: additionally require a trusted Android device credential / appropriate strong biometric authentication; the model or third-party app cannot assert success.
7. `DENY`: never issue an executable grant.
8. On execution, atomically consume grant, verify expiry/caller/parameters, recheck current capabilities and action registry, and dispatch only its registered deterministic handler.

The current starter service supports only the `ALLOW` decision; these confirmations remain an **unimplemented** gate.

## Replay, race, and lifecycle handling
- Unpredictable server-generated proposal IDs and nonces; no client-supplied approval flags.
- Bind to UID, package/signing identity, action ID, canonical argument bytes, policy version and expiration.
- Single use with atomic compare-and-consume; invalidate on app removal, policy change, credential reset, reboot or user switch.
- Check foreground user/profile boundaries and deny cross-user access by default.
- Avoid time-of-check/time-of-use by revalidating immediately before dispatch.
- Return opaque error codes; never echo private parameters or secrets in logs.

## AIDL parcel contract
The checked-in AIDL below is a **non-buildable sketch**: explicit validation/serialization remains necessary, and it is intentionally not wired to `Android.bp`. Payloads should have explicit versioned typed parcelables rather than raw arbitrary `Bundle` or untrusted serialization.

## Security test plan
- Reject caller-claimed UID/capabilities and unrecognized action IDs.
- Enforce signature-level caller permissions and SELinux isolation.
- Reject altered arguments after approval, replayed/expired grants and cross-UID proposals.
- Recheck permissions at execution and on user switch.
- Ensure strong confirmation cannot be spoofed by calling Binder directly.
- Fuzz parcel sizes, types, missing and duplicate fields.
- Assert audit logs omit private input and token values.
