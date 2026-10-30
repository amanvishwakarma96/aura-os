# Phase 0 Host and AOSP Build Plan

The master engineering prompt selects official AOSP `android-latest-release` as the starting **reference/emulator** baseline. It is a moving reference: pin an exact manifest revision in an ADR before claiming reproducibility.

Use Google's official current AOSP setup instructions for a supported Linux host, disk/RAM/JDK requirements, `repo` and emulator build configuration. The repository's `tools/scripts/bootstrap-aosp.sh` initializes a checkout directory but does not sync, build, flash, or boot Android.

On a host with Kotlin command-line tools, run:

```bash
bash tools/scripts/validate-foundation.sh
```

This checks **framework-only Kotlin** and runs lightweight assertions. It does **not** validate the Soong `Android.bp` modules, Android-dependent service, AOSP compatibility, emulator, SELinux or the Redmi Note 9.

Next validation gate: pin a manifest revision, sync AOSP on a supported host, record target/toolchain information, build a reference/emulator target, then validate the AURA Soong modules.
