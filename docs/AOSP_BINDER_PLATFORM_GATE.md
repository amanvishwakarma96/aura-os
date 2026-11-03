# AURA OS — Milestone #22: pinned AOSP/Soong/Cuttlefish platform gate

**Status:** A real execution script, protected manual GitHub workflow, and offline fail-closed evidence tests have been added. **The full platform build and Cuttlefish test have NOT been run or verified.** No assertion that AURA OS boots is warranted.

## Verified baseline
PR #13 feature branch was checked at `3624b3222ff8565997d09c0465f672bd0c97e113`. All five workflows passed: framework Kotlin, immutable manifest/static tests, reference harness, Android 15 real Binder IPC emulator tests, and Android 15 Keystore emulator tests. Those existing emulator checks compile AURA's Android SDK modules and run isolated test APKs. They are **not** AOSP/Soong or Cuttlefish tests.

## Capacity and approval
Google's AOSP workstation guidance calls for **64 GB RAM, 400 GB free disk for a fresh checkout/build**, Linux x86_64, and an appropriate Cuttlefish/KVM host. This tool cannot use ordinary GitHub-hosted runners for the complete platform task. The session's available workspace is not a qualifying AOSP host.

GitHub requires the workflow filename to exist on its default `main` branch before manual `workflow_dispatch`. PR #23 registered the **inert** default-branch version with `permissions: {}` and a permanently skipped job. Selecting `main` cannot run an AOSP build; choose `feature/binder-real-ipc-probe` explicitly when manually dispatching the **separately reviewed branch implementation**. The inert registration does **not** install a runner or prove any platform tests passed.

The manual job in `.github/workflows/aosp-binder-platform-gate.yml` is deliberately restricted:
- Only `workflow_dispatch` on `feature/binder-real-ipc-probe`, with the exact typed input `CONFIRM_AOSP_PLATFORM_GATE`.
- Self-hosted runner with labels `self-hosted`, `Linux`, `X64`, `aura-aosp`; protected GitHub environment `aura-aosp-verification`.
- A pre-provisioned, **synced and reviewed** AOSP tree. The script does not run `repo init`, `repo sync`, `sudo`, `fastboot`, `adb root`, repartition, flash firmware or register a production `SystemServer` service.
- Required runner/repository environment variables (set before manual dispatch):
  - `AURA_AOSP_DIR` — absolute directory of a pre-synced AOSP source tree
  - `AURA_LOCKED_MANIFEST_SHA256` — reviewed 64-character SHA-256 of `repo manifest -r` output for this exact AOSP checkout
  - `AURA_CUTTLEFISH_SERIAL` — exact ADB device serial for an *isolated Cuttlefish VM*, never a personal phone
  - `AURA_AOSP_LUNCH_TARGET` — approved Cuttlefish userdebug target, for example `aosp_cf_x86_64_only_phone-aosp_current-userdebug` **only when present in the pinned AOSP source**
- A trusted operator must initialize/sync AOSP separately from Google's official manifest and create a locked revision file. `android-latest-release` is **not** a reproducible pin. First review the resolved manifest and verify that every project has an immutable SHA, then provision `AURA_LOCKED_MANIFEST_SHA256` before running.
- Set GitHub environment protection to limit who can launch code on the expensive, privileged self-hosted runner. Avoid untrusted fork PR code in any runner's shared AOSP checkout. Protect the host's credentials and keep test evidence private.

## What the manually approved script does
1. Requires `AURA_GATE_EXECUTE=YES` and all independent expected identifiers, and checks an entirely clean AURA source commit.
2. Calls the strict Linux/KVM/RAM/disk host readiness gate; does **not** sync a moving AOSP branch.
3. Records `repo manifest -r` and validates both every project's immutable revision and the externally preapproved manifest SHA-256 **before any build**.
4. Copies source files into the previously absent, isolated AOSP path `vendor/aura/verification/platform/aura`. It refuses to overwrite an existing staging directory and does not update product packages or modify `SystemServer`.
5. Sources `build/envsetup.sh`, selects the operator-approved Cuttlefish lunch target, runs a full Android `m` build, then runs Soong `m` for `aura-framework-core`, `AuraBinderProbeHost`, `AuraBinderProbeAttacker`, and `AuraBinderProbeTests`.
6. Requires newly generated APKs and records actual APK SHA-256 and signing certificate digests. The host and attacker must have **different** signing certs.
7. Before installing anything, checks the explicit ADB device is booted, SELinux is Enforcing, and its product/hardware/type identify the approved Cuttlefish userdebug VM. **Never run this against a physical phone.**
8. Installs only the test APKs on that selected Cuttlefish VM and executes the existing Binder instrumented tests; requires **both** successful test result and an actual successful instrumentation exit.
9. Runs `verify-aosp-binder-platform-evidence.py` against the real manifest, APK files, recorded signatures, build metadata, device properties and two-test instrumentation output.

**Evidence remains only in the self-hosted runner's private filesystem.** It is not automatically uploaded with GitHub Actions; logs may contain host metadata or ADB serials and must be reviewed/redacted before sharing.

## Fail-closed limitations
- The workflow's *offline* contract job tests fabricated positive/negative evidence and the manual opt-in guard. Passing **does not** mean the self-hosted job ever executed.
- A preapproved manifest checksum is necessary but not equivalent to supply-chain signature verification or device hardware attestation.
- The APK finder requires a unique newly built APK for each module; if Soong produces multiple output copies or different output locations the gate will **fail** until the actual pinned AOSP layout is investigated. No fallback to stale APK files is allowed.
- Soong module definitions have not been confirmed on a real pinned AOSP checkout. Compilation failures must be fixed from the true Soong errors, not hidden by downgrading to SDK-only builds.
- The script expects a **previously booted** Cuttlefish userdebug VM and is intentionally unable to launch an arbitrary physical device. A separate operator must install Cuttlefish host packages and boot an image built from the approved source.
- These checks verify test APK authorization boundaries, **not** a privileged AURA SystemServer integration. Device policy and Android action approvals remain separate.
- Existing AOSP startup documentation and any future target changes must be reconciled to the actual manifest: do not blindly select an unverified lunch target.

## When PR #13 can merge into develop
Require independently reviewed proof of the **actual self-hosted job** completing: full pinned AOSP build, Soong module compilation, Cuttlefish device evidence, real negative Binder tests, and no unresolved security findings, plus successful existing GitHub checks on the merge candidate. Until then PR #13 must remain unmerged into `develop`.

## Official references
- https://source.android.com/docs/setup/start/requirements
- https://source.android.com/docs/setup/build/building
- https://source.android.com/docs/devices/cuttlefish/get-started
- https://source.android.com/docs/core/tests/development/blueprints
