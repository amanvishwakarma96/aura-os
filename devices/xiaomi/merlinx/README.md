# AURA OS device target: Xiaomi Redmi Note 9 (merlin / merlinx)

**Status: RESEARCH / READ-ONLY INVENTORY ONLY. AURA OS IS NOT FLASHABLE ON THIS PHONE.**

Initial candidate hardware: Xiaomi Redmi Note 9, 4 GB RAM / 64 GB flash (user-reported), arm64, MediaTek Helio G85 / MT6768/MT6769 family. Xiaomi device codename is frequently `merlin`; the maintained LineageOS device tree uses the `merlinx` target. These names **do not by themselves prove** that an arbitrary phone or ROM can accept the AURA build. Device model, current stock firmware, bootloader state, active partitions and vendor image must be verified against the *actual* phone.

Existing external engineering references (to evaluate and license-check, NOT yet vendored into AURA):
- LineageOS device tree: https://github.com/LineageOS/android_device_xiaomi_merlinx
- LineageOS device information / firmware prerequisites: https://wiki.lineageos.org/devices/merlinx/
- Device kernel sources: https://github.com/LineageOS/android_kernel_xiaomi_mt6768
- Official Xiaomi bootloader-risk guidance: https://www.mi.com/global/support/faq/details/KA-07238/

## First device inventory (non-destructive)

With **USB debugging enabled**, the phone connected to a trusted developer machine and `adb devices` showing the exact phone serial, execute:

```bash
python3 tools/scripts/redmi-note9-inventory.py --serial <explicit-adb-serial>
```

The script executes **only** `adb -s <serial> shell getprop <allowlisted-property>` for basic hardware/software identity. The serial is never printed in the report. There is **no** `adb install`, `fastboot`, `unlock`, `reboot`, `root`, `dd`, `wipe`, `flash`, APK deployment, or write operation. It always produces `aura_flash_ready: false` and `physical_flash_authorized: false`, even for a compatible candidate. If the phone is not connected or a property is unexpected, fail closed rather than guessing.

Do **not** publish the resulting report if it reveals personally identifying details, custom ROM modifications, or private device data. The tool intentionally excludes IMEI, serial, Wi-Fi MAC, phone number, contacts and other personal identifiers.

## Physical flash gates (all required, none yet signed off)

1. **Identify device variant:** confirm exact marketing model, `merlin` / `merlinx` codenames, SoC, Android firmware base, bootloader state and partition layout from this actual unit.
2. **Porting provenance:** review compatible upstream device tree, vendor/proprietary blob extraction terms, kernel, recovery, board settings and hardware HALs; pin sources with immutable commits and suitable licenses.
3. **Real AOSP baseline:** build a pinned AOSP reference image and all AURA Soong modules on a qualifying Linux/KVM host. Run the real Binder/Keystore tests on Cuttlefish and review evidence; PR #13 must pass this gate.
4. **Device-specific build:** integrate the exact Redmi Note 9 board config, kernel/vendor blobs, SELinux, AVB/dm-verity and supported partition scheme. Produce deterministic AURA **device-targeted** images, not generic emulator builds.
5. **Hardware bring-up in isolated engineering environment:** boot and test touchscreen/display, GPU, Wi-Fi, Bluetooth, RIL/SIM/IMS, calls, microphone/speaker, camera, sensors, GPS, encryption, charging, suspend, storage and reboot. Log device-specific failures. A successful boot alone is NOT a daily-driver release.
6. **Recovery & data safety:** make verified external backups, verify recovery firmware provenance and a known-good return-to-stock path for the exact model, inspect anti-rollback/AVB and bootloader restrictions, ensure USB recovery can be performed. **Never auto-unlock the bootloader or relock it on a custom build.** Unlocking may wipe all user data.
7. **Controlled owner-approved test:** only after a reviewed signed image and rollback procedure exist, request separate explicit approval before any destructive or physical-device flashing. Do not extrapolate this document as authorization.
8. **Release candidate:** check performance, thermal behavior, encryption, SELinux Enforcing, battery, OTA/recovery, emergency calling, and app compatibility. Passing engineering bring-up is not equivalent to safe everyday use.

## Practical readiness

- **AURA today:** framework/security prototype + standalone Android SDK emulator tests; not a bootable Redmi Note 9 image.
- **First experimental flash:** cannot assign a defensible date until a real full-AOSP runner, device port, build artifacts and rollback tests exist.
- **Daily-driver support:** separate, later milestone; expected to require additional engineering and reliability testing.
- **4 GB RAM / 64 GB storage:** reasonable for a lean AOSP-based system, but cloud AI/Codex should be optional; do not promise an on-device large model. Memory and performance require physical profiling.

This work deliberately does not change PR #13's platform acceptance criteria or publish any flashable image.
