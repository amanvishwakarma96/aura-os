# Android Binder identity adapter — verification gate

**Status:** Source and negative instrumentation-test code only. NOT registered with SystemServer; no device/emulator instrumentation run or Soong build has been verified.

The isolated `platform/aura/android-adapter` source uses Android Binder calling UID/PID and Context permission checks. It resolves UID packages, rejects ambiguous/shared UID ownership, verifies exact allowlisted SHA-256 APK signing certificates, denies non-primary Android users and constructs a trusted framework identity with server-held capabilities and policy generation.

Security constraints:
- Invoke `resolve()` synchronously inside a genuine incoming Binder transaction before `Binder.clearCallingIdentity()`; it rejects same-process calls.
- `allowedSigners`, capabilities and policyVersion must come from trusted, immutable platform configuration; never from parcel values.
- The digest list used here reflects current APK content signers; review certificate rotation, Android package visibility and multi-signer identity semantics before shipping.
- The primary-user-only policy is deliberate and must be reviewed for system/managed-profile use.
- `AuraTrustedCaller` is a data container, not cryptographic authentication.
- Current tests use fake Platform inputs under AndroidJUnit4. **They do not validate actual Binder identity propagation, permission enforcement between real processes, package signing lookup or SELinux access.**
- Add an Android.bp target and an AOSP reference-emulator instrumentation target only after the platform branch/version is pinned and API compatibility has been checked.
- Do not accept untrusted AIDL calls, implement SystemServer registration, or flash devices as part of this change.

Outstanding: real multi-process permission-denial tests, package-certificate rotation tests, AOSP compile, SELinux enforcement verification, trusted confirmation UI and end-to-end replay protection.
