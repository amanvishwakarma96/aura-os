# AOSP reference validation — execution plan

Status: **not yet executed**. No pinned manifest SHA, AOSP checkout, emulator boot, Soong build, or real-process Binder instrumentation has been verified.

## Prerequisites
A supported Linux AOSP build host with current Google-recommended RAM, storage, JDK and repo prerequisites. Determine and document the exact AOSP branch and manifest revision; use a reference emulator target consistent with that branch.

## Phase gate
1. Create a clean reference checkout from Google's official Android manifest. Record immutable manifest revision and selected lunch target.
2. Build an unmodified platform reference image and boot it in the emulator; retain build and adb evidence.
3. Add the adapter only to an isolated test module; do not register an AURA service with SystemServer.
4. Compile `AndroidBinderPlatform` against the selected platform API surface, resolving compile/API differences with documented rationale.
5. Install isolated target/test APKs on the reference emulator. Verify legitimate remote Binder caller UID/PID.
6. Use separate signed/unsigned client test processes for permission-denial and package-identity checks.
7. Run cross-user/profile and signature-change negative tests where supported.
8. Run SELinux policy analysis and verify enforcing mode before proposing privileged integration.
9. Capture exact commands, exit status, logs and artifacts in an evidence report.

## Hard stop
Do not flash any physical phone, expose privileged Binder interfaces, add permissive SELinux rules or claim platform compatibility before the above gates pass.

## Current risks
The initial adapter is not wired to a build target, and its mock instrumentation tests only simulate identities. Signing-certificate rotation and shared UID policy require platform-specific review.
