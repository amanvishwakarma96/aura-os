# AOSP Reference Build & Binder Test Harness — Phase Gate

**Status (2026-11-03):** Host preflight, baseline capture, and strict observation scripts exist. Separately, Android 15 SDK emulator Binder IPC (2 tests) and Keystore (3 tests) instrumentation passed on PR #13's feature branch. **No full AOSP/Soong build or Cuttlefish run has yet been verified.** Milestone #22 adds an opt-in self-hosted full-platform gate (see `docs/AOSP_BINDER_PLATFORM_GATE.md`).

## Baseline decision
Use Google's official `android-latest-release` only for initial checkout selection. This alias is moving. After `repo sync`, execute `repo manifest -r` and archive the locked revisions before declaring any particular AOSP baseline reproducible. A concrete branch/revision is **not pinned yet**.

## Reference workflow (supported Linux/KVM host)
1. `bash tools/scripts/aosp-reference-preflight.sh /path/to/checkout-parent`
2. Use the official AOSP instructions to initialize and sync an isolated checkout on a supported Linux host.
3. `bash tools/scripts/aosp-record-baseline.sh /path/to/aosp /path/to/evidence`
4. Follow Google's documented Cuttlefish build/lunch target for the captured AOSP branch, build reference images, then launch Cuttlefish on a KVM-capable host.
5. `bash tools/scripts/aosp-verify-cuttlefish.sh /path/to/aosp /path/to/evidence`
6. Only after the actual platform API surface is known, add a Soong `android_test` module and Android instrumentation runner for the adapter.
7. Add a separate Binder test service and **separate-process** callers for permission-denial cases. A fake platform test is insufficient.

## Release gate
Require a reviewed immutable manifest checksum, successful full AOSP/Soong build, booted Cuttlefish with SELinux Enforcing, and Soong-built signed APK instrumentation for the real Binder negative tests. The current adapter is not registered in SystemServer. GitHub-hosted SDK tests are not sufficient to satisfy this gate.

## Safety
The scripts do not download or flash firmware. Evidence can contain host/device identifiers; review before publication. Never upload proprietary blobs or private keys.

References:
- https://source.android.com/docs/setup/start
- https://source.android.com/docs/setup/build/building
- https://source.android.com/docs/devices/cuttlefish/get-started
- https://source.android.com/docs/core/tests/development/blueprints
