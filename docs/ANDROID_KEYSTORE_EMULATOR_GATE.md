# AURA Android Keystore — executable device verification gate

**Goal:** Replace source-only/static assertions with an Android SDK 35 build and actual Android 15 emulator instrumentation tests of the existing credential store. This is an **isolated test APK**, not SystemServer integration or a product login screen.

## What is wired
- `tools/android-keystore-instrumentation/`: standalone Gradle Android app which compiles the real AURA framework source and the real `AndroidKeystoreChatGptSessionStore.kt`. No separate mocked Android adapter.
- `.github/workflows/android-keystore-device.yml`: separate Android SDK compile job and emulator instrumentation job. Both must succeed before merging this PR. Tests use only synthetic credential values.
- The Android adapter uses public `Process.myUserHandle().identifier` for the current OS user/profile rather than the hidden `UserHandle.getUserId` API.
- Instrumentation tests exercise AndroidKeyStore AES/GCM persistence across fresh store instances, encrypted file contents, refresh-token CAS/generation rotation, other-profile rejection, ciphertext tamper rejection, changed subject rejection and key deletion on local disconnect.

## Explicit limits
An Android SDK emulator is **not** an AOSP/Soong full-platform build. These tests do not cover the separate real Binder IPC probe, device flash, actual hardware-backed key attestation, physical reboot, multi-Android-profile execution, provider token exchange or ChatGPT Plus allowance. A simulated other-profile ID rejection is not a real second-user instrumentation run. Later testing must include real multi-process, process restart, device lock/unlock, crash/reboot, and key-invalidated recovery.

Keep PR #13 unmerged into `develop` until the full Binder/Soong gate passes. A green Android Keystore build must not be used to claim that the complete AURA OS is bootable.

The optional user-owned ChatGPT Plus/Codex subscription path remains distinct from independent paid API-key billing. No real account credentials are used in these tests.
