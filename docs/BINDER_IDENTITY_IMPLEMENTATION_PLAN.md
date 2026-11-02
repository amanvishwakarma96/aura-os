# Binder caller identity integration gate

Status: **design + standalone pure Kotlin validation only**. No Binder endpoint is registered and no caller is authenticated by this code.

## Required platform adapter

At the actual Binder entrypoint, obtain the calling UID/PID directly from Binder before clearing identity. Verify the caller's signature-level permission, trusted application package ownership under that UID, signing identity, user/profile boundary and requested action capabilities against authoritative platform policy. Never accept any of those values, including policy version, from the request or model output.

Construct an immutable `AuraTrustedCaller` only after the Android checks succeed. The DTO validates basic shape; it does **not** authenticate a caller. Convert to the coordinator context only inside trusted platform code.

## Mandatory implementation gates

1. Pin and compile an AOSP target; verify the actual Android API/Soong configuration.
2. Implement platform-specific Binder identity resolver and signature permission check.
3. Deny UID/package mismatch, isolated/untrusted callers, cross-user access, missing capabilities, and revoked signing identity.
4. Use SystemUI-owned authentication/confirmation and scoped, expiring approval grants; do not expose approval issuance to callers.
5. Add tests using Android instrumentation with simulated unauthorized UIDs and permission changes, plus audit redaction.
6. Verify SELinux enforcing and neverallow compliance before exposing the service.

## Current limitations

The current framework coordinator still takes a plain caller context and a module-internal approval method. Those are **not safe** as boundaries against untrusted app code. No real identity resolver, Android package manager query, signature verification, Binder permission enforcement, or SELinux rules have been implemented. The user must not flash the Redmi Note 9 based on this milestone.
