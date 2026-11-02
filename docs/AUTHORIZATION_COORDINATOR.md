# Authorization coordinator prototype

The standalone Kotlin coordinator combines registered action policy, canonical arguments, and one-use approval grants. Its test covers approval requirements, replay rejection, and argument changes.

This is NOT a trusted Android authentication implementation. CallerContext is not populated by Binder, and recordTrustedApproval accepts a placeholder Boolean rather than verified Android authentication. Module-internal visibility does not enforce caller authorization.

Do not expose these methods to untrusted clients or wire them into SystemServer. Future work must derive identities from Binder, verify trusted SystemUI confirmation, protect grant issuance, implement lifecycle invalidation, and run emulator security tests. CI and AOSP builds are not yet verified.