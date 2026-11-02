# AURA real Binder IPC probe — emulator-only milestone

**Status:** Test-only source, manifests, Soong build declarations and cross-process instrumentation source added. No AOSP build, APK install, Cuttlefish boot or real Binder instrumentation result has been verified.

The test host is a standalone APK with a signature permission. Its Binder service runs in the `:remote` process and checks identity *inside* the transaction. The second APK has a different package/UID/signing certificate, lacks the signature permission, and sends a forged trusted package string. The host ignores this claim. Tests require an actual remote UID to be accepted and a forged, untrusted caller to be denied. Inconclusive binding failures must fail tests rather than be treated as successful security denial.

The harness deliberately uses a test-only, exported component to measure rejection. Never ship the APKs, expose the component on production devices, or wire any of this into `SystemServer`. Signed test certificates (`testkey` and `media`) are build/test fixtures only, not production trust anchors. The host includes only its own installed signing digest in the allowlist; an independent app cannot supply its own signer or capabilities.

On a supported, pinned AOSP host, verify the Soong modules exist, compile them, install all three APKs on a **reference emulator**, then execute instrumentation and inspect results:
```bash
m AuraBinderProbeHost AuraBinderProbeAttacker AuraBinderProbeTests
adb -s <Cuttlefish-serial> install -r <built-host.apk>
adb -s <Cuttlefish-serial> install -r <built-attacker.apk>
adb -s <Cuttlefish-serial> install -r <built-test.apk>
adb -s <Cuttlefish-serial> shell am instrument -w com.aura.os.binderprobe.tests/androidx.test.runner.AndroidJUnitRunner
```
The APK paths depend on the pinned Soong output tree and must be located from actual build artifacts. Record results and inspect ADB PID/UID, signing certificates, permission states, SELinux status and error logs. These commands are *instructions*, not completed test evidence.

Potential Soong/API compatibility issues must be resolved on the pinned AOSP branch; no assumptions about Android SDK/platform binary compatibility can substitute for compilation. The harness currently tests trusted remote execution and untrusted package spoofing. A complete security sign-off requires further real-device test cases for cross-user/profile behavior, permission revocation, signer changes, stale policy generations and replay, and does not authorize flashing a phone.
