# AURA OS — ChatGPT Plus session storage (Keystore + AES-GCM)

**Status:** Proposed Android adapter with **host-tested encrypted session core**. Android Keystore integration has **NOT** been compiled in an AOSP image or tested on Cuttlefish, and no real ChatGPT access token has been stored. The existing optional ChatGPT Plus/Codex-plan authorization contract remains unchanged (no separate API-key billing).

## Previous work verified before this milestone

At the start of this PR the `feature/binder-real-ipc-probe` parent was at `f039e94738634dea56fe0d617ba76b59054cb5a0` and its three CI runs were completed successfully. Earlier PRs #14–#18 have already merged into that branch. However, *host-only Kotlin/manifest/static checks do not prove Android platform test APKs build*; PR #13 must remain gated.

## Storage architecture

- The **platform-neutral** `AuraEncryptedSessionStore` implements `AuraChatGptSessionManager.SessionStore` over a pluggable `KeySource` and `AtomicRecords` backend.
- `AuraSealedSessionCodec` uses AES/GCM/NoPadding, a fresh **96-bit random IV**, **128-bit GCM authentication tag**, a versioned binary envelope, and authenticated additional data tied to the exact Android user/profile ID and issued OAuth client ID. The encrypted payload holds access token, rotating refresh token, ID token, verified account issuer/subject, granted scopes and generation. No plaintext credentials go to logs or filenames.
- `replaceAtomic` takes a **cross-process account lock** from `AtomicRecords`, rereads the current record, rejects unexpected generations and account-subject changes, and writes the entire newly encrypted session as one atomic record. A stale refresh cannot overwrite a newer session under normal store operation.
- `delete` invalidates the Keystore key first, then removes the encrypted record. Even if interrupted, a remaining ciphertext should not be decryptable. **Local disconnect is not remote OAuth revocation**; revocation and in-flight authentication cancellation are separate future work.
- The Android-only `AndroidKeystoreChatGptSessionStore` requires Android 15+, refuses direct-boot device-protected storage, enforces same Android user/profile as its process, stores files under the app's **no-backup** credential-encrypted directory, uses `AtomicFile` for crash-safe replacement and `FileChannel.lock()` for multi-process updates. The AES-256 key stays inside AndroidKeyStore, one alias per account/profile, with GCM and randomized encryption required.
- Uses `setUnlockedDeviceRequired(true)` on Android 15+; per-use biometric authentication is *not* required because background token refresh could be needed after unlock. Reevaluate this policy based on product security requirements. Android documentation warns of older-platform caveats, so this adapter rejects versions below 35.

## Validation actually performed by CI

- Host-only JVM tests encrypt/decrypt tokens, assert ciphertext doesn't contain token plaintext, confirm separation across Android users and OAuth clients, simulate reopening the storage backend, and check AES-GCM tamper/wrong-key rejection.
- They verify refresh generation compare-and-swap, stale replay rejection, simulated interruption before atomic commit, and two concurrent writers where only one rotation succeeds.
- They verify local disconnect removes both encrypted record and key in a fake Keystore, and identity-only consent remains insufficient for plan usage.
- Offline Android adapter source checks confirm keyguard, no-backup, encryption and lock/atomic storage structures. They do **not** compile or instrument the Android-specific file.

## Security caveats (release blockers)

1. **No actual AOSP/Soong build or emulator test:** The platform adapter is not registered or used by the OS, and its API compatibility is not proven. An explicit test-only Soong module and emulator verification must compile this source before deployment.
2. **Refresh rotation and power loss:** Once the remote provider rotates a refresh token, a crash before the new encrypted record is durably committed may make the old token unusable. Fail closed and require reauthorization rather than resurrecting a stale token. No remote transaction is implied.
3. **File rollback:** AES-GCM authenticates ciphertext, but does not itself prevent replaying an **older valid encrypted file** by a filesystem attacker. A hardware-backed rollback-resistant monotonic state or appropriate anti-rollback design is required for stronger filesystem-compromise assumptions.
4. **Recovery:** Key invalidation, loss of device credential, profile removal, app removal, or failed decrypt must never cause transparent cloud access. Wipe/unlink local tokens and present sign-in recovery only after user consent.
5. **Device backup, secret memory, malware:** File exclusion from backup is a precaution, not protection against root compromise or process memory extraction. The host API retains immutable Kotlin strings; production must minimize their lifetime and avoid log/analytics/crash data disclosure.
6. **Reauthorization after disconnect:** In-flight OAuth/refresh operations require a per-account revocation epoch or cancellation token so a stale operation cannot restore a disconnected session. This is not yet implemented.
7. **Multi-process robustness:** The host test uses real file locking but not Android `AtomicFile`. The full device/process restart, interruption and multi-user tests remain mandatory.
8. **OAuth scope:** Storage of an identity-only token is not permission to use plan allowance. The existing `AuraChatGptPlanPolicy` must still validate separately granted `chatgpt.tokens.use.direct`, session validity, user consent and account allowance before cloud inference.

No ChatGPT credentials, tokens, private keys or proprietary Android components are committed.

## Next integration requirements

1. Compile the adapter against a **pinned AOSP Android 15+ reference target** with explicit Soong configuration.
2. Add instrumented tests for KeyStore-backed durability, permission/user/profile separation, logout and crash/recovery behavior.
3. Add anti-resurrection cancellation semantics, robust reauthorization UI, remote revoke/reauth workflow, and an independent security review.
4. Only after these succeed, wire the optional **Continue with ChatGPT Plus** flow and its plan-scoped Responses/Codex provider without granting the AI Android privileges.

References:
- https://developer.android.com/privacy-and-security/keystore
- https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder
- https://developer.android.com/reference/android/util/AtomicFile
