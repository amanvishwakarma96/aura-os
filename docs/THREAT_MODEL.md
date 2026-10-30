# Phase 0 Threat Model (draft)

## Assets and boundaries
Assets: user data, device settings, credentials, remote inference secrets, audit logs and OEM firmware/partitions.

Boundaries:
1. Untrusted LLM output -> action proposal and schema validation.
2. Untrusted caller -> Binder UID/signature checks and SELinux.
3. Proposed action -> authoritative registry/policy -> trusted user confirmation -> dispatch.
4. Local system -> optional authenticated/encrypted remote compute.

## Threats, mitigations and implementation state

| Threat | Required control | State |
|---|---|---|
| Prompt injection | Strict registered action schemas, deny unknown operations | Not implemented |
| Spoofed action risk | Risk/capability metadata defined by trusted registry, not caller | Not implemented |
| Confirmation bypass/replay | Caller- and parameter-bound one-shot approval tokens | Not implemented |
| Binder caller impersonation | Signature permission, UID validation, SELinux | Not implemented |
| Secret exfiltration | Redaction, consent, local/remote data classification | Not implemented |
| Malicious remote node | Secure pairing, authentication and encrypted transport | Not implemented |
| Device flash damage | Model/partition verification, backups, recovery | Not implemented |

## Critical finding
The current `AuraPolicyEngine` trusts `AuraAction.riskLevel`, which is supplied with the action. Therefore an untrusted caller could label a sensitive request as LOW. Do not expose the starter's `executeApproved` method through Binder or SystemServer. First implement trusted action descriptors, parameter schemas, capability checks and confirmation binding.

## Exit criteria
Phase 1 must deny unknown action IDs, reject unrecognized fields, get immutable risk/capability data from a trusted descriptor, and recheck authorization at execution. Include negative tests for prompt injection, tampered parameters, substituted actions and replayed approvals.
