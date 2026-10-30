# AOSP host readiness gate

A full AOSP reference build requires a suitable host. The resource thresholds in this initial preflight are **planning defaults**, not a guarantee or an official minimum for every target: 64 GiB RAM, 400 GiB free disk, Linux, repo, and accessible KVM for accelerated Cuttlefish.

Run `bash tools/scripts/aosp-host-readiness.sh /path/to/checkout-parent`. The script fails when prerequisites are missing and does not download sources, build, boot or flash anything. Override resource thresholds with `AURA_MIN_RAM_GIB` and `AURA_MIN_DISK_GIB` only after documenting an appropriate target-specific capacity decision.

Run `bash tools/scripts/test-aosp-host-readiness.sh` for offline failure-path checks.

## Current execution environment (2026-10-30)
The environment available to the assistant had roughly 5.8 GiB RAM and 30 GiB free disk, with no `repo`, no accessible /dev/kvm and no network DNS access to github.com. Host preflight returned **BLOCKED**. The negative-path tests and Bash syntax checks passed locally. This is **not** an AOSP or Cuttlefish build result.

Next actionable dependency: provide a configured Linux/KVM build host with appropriate disk, RAM, repo tool, network and AOSP source checkout. Record actual manifest revision, target and build logs before accepting platform integration.
