# Foundation Validation Evidence

## Scope
Initial source-level validation of the Kotlin framework. No AOSP checkout, Soong build, Android instrumentation, emulator boot, Redmi Note 9 flashing or device validation.

## Local test
The registry/policy smoke test compiles framework sources with `kotlinc -Werror` and executes assertions for:
- unknown/registered action lookup
- blank and duplicate registration rejection
- all four risk-to-policy mappings

Observed output on the local validation environment:

```text
PASS: AURA framework registry and policy smoke tests
```

GitHub Actions CI is defined in `.github/workflows/foundation.yml` but a successful GitHub-hosted workflow run is not yet verified.

## Blockers
Caller-supplied risk levels, absent authoritative action schema/permissions, absent Binder caller validation, no approval tokens, no SELinux, and unverified Soong/AOSP compatibility.

## Phase 0 state
Basic foundation validation and threat-model draft complete. Pinned AOSP manifest, reproducible emulator build and platform validation remain pending.
