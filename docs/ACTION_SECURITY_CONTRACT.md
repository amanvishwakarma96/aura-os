# Phase 1 action security contract (incremental)

Each registered handler must be paired with a trusted, code-owned `AuraActionDescriptor` specifying action ID, risk, capabilities and an exact parameter schema. Model-supplied `AuraAction.riskLevel` is **not authoritative**; policy uses the registry descriptor. Unknown action IDs, malformed parameters and missing capabilities are denied. Caller identity and capability grants must come from authenticated Android/Binder context when Phase 2 is wired; never accept model-provided capability claims.

The policy currently supports only string (maximum 4096 characters), boolean, and 32-bit integer parameter types. All declared parameters are required and extra keys are rejected. This is an intentionally narrow first pass; semantic validations (e.g. component allowlists, valid phone numbers) still belong to handlers and cannot be skipped.

**Important:** This milestone does not implement user confirmation tokens, privileged Android caller authentication, Binder/AIDL or SELinux. Only `ALLOW` actions may execute through the service skeleton. `CONFIRM` and `STRONG_CONFIRM` must remain non-executable until an authenticated, parameter-bound, one-shot approval flow exists. No AOSP platform integration is authorized by this change.

## Remaining security risks
- `AuraAction` still contains a legacy caller-supplied risk field for compatibility, ignored in enforcement.
- No immutable approval token or authoritative Android UID binding.
- No capability resolution in the service skeleton. Until trusted caller-context integration, it passes an empty capability set, denying actions requiring a capability.
- Schema validation verifies types and shape but not values beyond length; handlers must validate semantics.
