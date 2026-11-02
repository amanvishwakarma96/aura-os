# AOSP evidence enforcement hardening

This change closes two correctness gaps in the reference harness:

- Baseline recording generates `manifest.sha256`, which the existing evidence verifier requires.
- Device observation requires an explicitly selected online adb serial and fails when `sys.boot_completed` is not `1` or SELinux is not `Enforcing`.

The harness now includes negative stub cases for unbooted, permissive, and absent devices. These are simulated tests only and cannot establish real Cuttlefish provenance or a successful AOSP build. The selected serial must be checked against the actual Cuttlefish instance by a human or a trusted host orchestrator. The scripts do not launch the emulator or modify devices.

## Validation gates

Run `bash tools/scripts/test-reference-harness.sh` on Linux and inspect GitHub Actions `Reference harness static verification`. Next, capture a genuine resolved manifest, verify with `validate-locked-manifest.py` and `verify-aosp-evidence.py`, and run the device check with an explicit serial. Do not merge or claim platform readiness unless CI passes.

## Executed regression verification — 2026-11-01

The reference harness regression script was exercised in the available isolated Linux environment with fake repo, git, and adb commands. Command: `bash tools/scripts/test-reference-harness.sh`. Exit status: **0 (PASS)**. Negative tests rejected absent checkout, unbooted device, permissive SELinux, unknown adb serial, and invalid host resource thresholds; checksum generation matched the locked-manifest bytes. This is local simulated validation, **not** a GitHub Actions result or real Android emulator verification. The tests are not proof of Cuttlefish provenance or AOSP compatibility.
