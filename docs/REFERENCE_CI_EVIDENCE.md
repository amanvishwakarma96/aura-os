# Reference harness CI evidence

This milestone adds a lightweight GitHub Actions workflow that executes Bash syntax checks and uses a stub `repo`, `git` and `adb` to exercise source-recording and read-only observation helpers. It also checks rejection of missing AOSP checkout structure.

**This does not run an AOSP checkout, Cuttlefish virtual device, or actual Binder calls.** A simulated `adb shell getenforce` response is only an assertion that the script captures text; it is not SELinux evidence from Android.

## Validation layers
1. Lightweight GitHub Actions workflow (`reference-harness.yml`): syntax and stub-contract checks; result pending until a real successful run is observed.
2. Supported Linux host: actual preflight and recorded `repo manifest -r` from a synced checkout.
3. Reference Cuttlefish build: reproducible lunch/build/launch instructions and observed `boot_completed=1` plus enforcing SELinux mode.
4. Real Binder tests: separate-process caller with signature/capability/cross-user deny cases.

**Merge gate:** Verify CI results for both relevant PR heads, then review stacked PRs in dependency order. No platform compile or security claim may be inferred from this workflow alone.
