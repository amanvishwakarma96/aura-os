# Architecture Decision Log

## ADR-001: Android-compatible base
**Status:** Accepted

AURA OS will build on AOSP/Android-compatible foundations rather than designing a new kernel/userspace mobile ecosystem from zero.

## ADR-002: AI is a system capability, not an authorization layer
**Status:** Accepted

The LLM may propose typed actions but cannot authorize or directly execute them.

## ADR-003: Emulator/reference device before Redmi Note 9
**Status:** Accepted

AURA framework development is validated on a reproducible AOSP target before device-specific bring-up, reducing device-bricking and debugging risk.

## ADR-004: AOSP version is explicit
**Status:** Accepted

Start from `android-latest-release` for platform development unless device compatibility forces a pinned branch. Any pin must be documented with reason and migration plan.
