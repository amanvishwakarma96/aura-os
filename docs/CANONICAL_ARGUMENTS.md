# Canonical Action Argument Encoding (Phase 1)

`AuraCanonicalArguments.encode` provides deterministic, versioned, length-prefixed binary encoding for a narrow argument schema: UTF-8 strings, booleans and 32-bit integers. Field order is sorted. Unsupported values, nulls, excessive fields and excessive payload lengths are rejected.

Use this only **after** registry descriptor/schema validation. The output may be bound into the grant store's `canonicalArguments` field, but the service is **not yet integrated** with this encoder or the approval store. Nothing in this change permits an LLM or third-party process to issue a trusted approval.

Security follow-ups:
- Trust caller UID/user ID only as resolved from Android Binder (not provided by the model).
- Add signature identity, policy version and approved scope to immutable grant bindings.
- Ensure the exact same canonical payload is produced at proposal, trusted approval and execution.
- Confirm semantic handler validation, capability checks and policy re-evaluation before any dispatch.
- Add fuzz/property tests and enforce service-level resource limits; concurrency tests should be run in CI.
- Review encoding and AIDL serialization compatibility after pinning AOSP.

Current CI status: GitHub workflow runs for the prior design and approval branch heads were not visible through the connector when this file was authored. Source commits are not proof that tests passed.
