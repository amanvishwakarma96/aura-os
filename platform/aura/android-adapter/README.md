# AURA Android adapter

This directory is intentionally isolated from Soong builds while the reference AOSP revision remains unpinned. Sources are preliminary and are **not a registered platform service**.

The Binder identity resolver must only be called from a real incoming Binder transaction, before clearing calling identity. It validates a remote caller's identity and permissions through an Android-specific platform abstraction.

Tests currently use a fake Platform implementation; they do not prove real cross-process Binder permission enforcement. The next gate is an emulator-backed test application/service and explicit Android.bp/AIDL build integration once the AOSP baseline is locked.

See `docs/AOSP_REFERENCE_VALIDATION.md` and `docs/BINDER_ADAPTER_VALIDATION.md`.
