# AURA OS — core OS development and release gates

**Priority:** Finish and verify the reusable AURA OS first. Device-specific ports, including Redmi Note 9 / merlinx, are **deferred**. The source-audit branch for that device is not merged.

This document reports verification levels, not a claim that AURA OS is currently bootable or ready for flashing. The current codebase includes HAL/kernel-adapter prototypes, Binder caller-identity enforcement, isolated Android SDK instrumented tests, optional ChatGPT plan-backed authentication primitives, and guarded AOSP source/build tests.

## Core milestones and objective exit gates

| Stage | Deliverable | Required evidence | Current status |
|---|---|---|---|
| 1. Foundation | HAL/kernel abstraction, identity resolver and tests | Host unit/negative tests | Repository CI passed |
| 2. Android component isolation | Binder cross-process authorization, Keystore encrypted store | Real Android emulator APKs, including independently signed hostile caller | Android 15 SDK emulator CI passed on PR #13 |
| 3. Pinned AOSP reference | Immutable manifest, reproducible full AOSP image, AURA Soong modules | Protected Linux/KVM build run, full logs, Soong APKs, fingerprint-matched Cuttlefish | **Blocked: runner and real AOSP run not yet available** |
| 4. Core OS integration | Platform-scoped AURA services, validated permission and SELinux policies, stable startup | Booted Cuttlefish, clean SELinux, service lifecycle and negative IPC tests | **Not implemented or verified as a bootable AURA OS** |
| 5. First-run and system UX | Setup, user/account/profile separation, privacy settings, opt-in cloud AI | UI/device instrumentation, accessibility, network/consent tests | **Not yet verified** |
| 6. Optional ChatGPT Plus/Codex | Supported Sign in with ChatGPT, secure session, limits and typed action proposals | Real OAuth/OIDC, consent, token revocation, encrypted storage, denied privileged actions | Auth/security components in development; **not live** |
| 7. Release engineering | Reproducible system artifacts, update and recovery policy, complete test matrix | Signed images, cold-boot regression, OTA/recovery validation, security review | **Not ready** |

## What the new Soong preflight can and cannot do

The offline `tools/scripts/verify-aosp-soong-stage.py` checks the known test-only AURA Soong modules, their declared names, source file layouts, distinct test APK signer aliases, manifests, and absence of production package registration. The protected AOSP gate invokes it before staging and again after copying the sources to an isolated `vendor/aura/verification` directory.

**Passing a preflight proves only source layout consistency.** It does not parse the complete Android Blueprint language, resolve dependencies from the selected AOSP checkout, compile native code, build a system image, boot Cuttlefish, establish SELinux correctness, or demonstrate end-user OS functionality. Only a real full-platform build and instrumented boot can supply those results.

## Merge and scope policy

- Core-OS-only, host-verified preparatory fixes may merge into PR #13's isolated feature branch after checks pass. They do **not** release new OS images.
- The parent PR #13 must **not** merge into `develop` until its full pinned AOSP/Soong/Cuttlefish gate is completed, evidence is reviewed, and there are no unresolved security problems.
- Do not enable a privileged Android action simply because a ChatGPT account is signed in. AI account consent and Android action permissions are separate.
- No silent use of independently billed OpenAI API keys; a user's existing eligible ChatGPT plan allowance is the optional intended path.
- Do not auto-unlock, flash, repartition, or alter any personal phone. Physical ports are a separate later project phase with explicit owner approval.

## External infrastructure dependency

The missing near-term blocker is a **trusted Linux x86_64 self-hosted runner** with a sufficiently provisioned pinned AOSP source checkout, KVM/Cuttlefish support, controlled access, and reviewed manifest checksum. The repository contains a protected manual execution gate and documentation in `docs/AOSP_BINDER_PLATFORM_GATE.md`; no host is created or billed by this milestone.
