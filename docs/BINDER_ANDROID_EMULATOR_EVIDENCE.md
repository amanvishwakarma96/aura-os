# AURA OS — Real Binder IPC emulator verification milestone

**Purpose:** Build the existing AURA Binder identity adapter, remote-process host, independently signed attacker APK, and instrumentation tests with Android SDK 35. Run real Android 15 Binder transactions before considering any production integration.

## Previous-work gate verified before this milestone
Parent branch `feature/binder-real-ipc-probe` was at `6927a106dacd1399a2d7fd8f2643372f0aaa1998`, with completed successful foundation, manifest, reference-harness, and Android Keystore device workflows (4/4). The Keystore test actually ran on Android 15; it did not test the Binder subsystem.

## Device test design
- `tools/android-binder-instrumentation/` builds **the existing** framework, Binder resolver, Android platform adapter, Binder host service and attacker service, without copying or mocking those production sources.
- The **host** is an Android test-only APK with the `com.aura.os.permission.ACCESS_AGENT_SERVICE` signature permission and a service in a distinct `:remote` process. Instrumentation invokes it over Binder, checks the authenticated UID and confirms a different service PID.
- The **attacker** is an independent package with a distinct **ephemeral test certificate** generated on the CI runner (never committed) and a distinct Android UID. It sends a forged trusted package string. Test code confirms the cert mismatch and that the signature permission is NOT granted. Authorization must reject the attacker. A missing connection or unknown result fails the test rather than counting as successful denial.
- The Android Binder platform's `userIdForUid` derives the AOSP user partition via `uid / 100_000`, instead of the SDK-hidden `UserHandle.getUserId`. This is a **specific AOSP UID-layout dependency**, not an Android-wide public API guarantee.
- No `SystemServer` registration, no device-admin privilege, no physical device modifications, no real ChatGPT credential or subscription usage.

## CI implementation
`.github/workflows/android-binder-ipc.yml` has a required compile job and a separate dependent Android 15 emulator instrumentation job. The SDK build job must pass before emulator testing. Instrumentation test reports are uploaded even on failed tests. The workflow deliberately does not merge by itself: the maintainer must inspect and confirm both successful jobs and all other required branch checks.

## Remaining production/AOSP gates
**Even if this standalone Android SDK test is green**, PR #13 must not be represented as validated on a complete AOSP image. The separate Soong modules in `platform/aura/android-adapter/probe/Android.bp` have not been built against a pinned AOSP checkout or run on Cuttlefish. Full platform build, vendor/device compatibility, SELinux context behavior, app signing/installation, and SystemServer integration (if ever needed) remain separate release gates. Test-only exported services must never ship in a production OS image.

Verification is staged:
1. **Host Kotlin + static CI** — existing evidence.
2. **Android SDK host/attacker/test build and emulator IPC** — this milestone, when workflow passes.
3. **Pinned AOSP Soong module build + Cuttlefish actual IPC** — future milestone, not implied by SDK tests.

The AI provider remains optional, with eligible ChatGPT Plus/Codex plan-backed authorization separate from Android action approval and not billed via an independently supplied API key.
