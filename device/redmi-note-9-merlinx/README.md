# Redmi Note 9 (`merlinx`) Bring-up

This folder intentionally contains **no proprietary Xiaomi/MediaTek blobs**.

## Before flashing anything
- Confirm exact device model and codename using multiple device properties.
- Back up user data.
- Back up device-specific calibration/persist/NVRAM information using established device-community guidance.
- Record stock firmware version and obtain a known-good recovery path.
- Never guess partitions.
- Never use destructive formatting/partition commands without explicit verification and backup.

## Bring-up checklist
- [ ] bootloader state documented
- [ ] recovery path verified
- [ ] kernel source/reference identified
- [ ] device tree/reference identified
- [ ] vendor blobs extraction method documented
- [ ] boot + adb
- [ ] display + touch
- [ ] encryption/data
- [ ] Wi-Fi
- [ ] Bluetooth
- [ ] RIL/calls/SMS/mobile data
- [ ] audio/mic
- [ ] sensors
- [ ] GPS
- [ ] fingerprint
- [ ] camera/video
- [ ] USB/MTP/ADB
- [ ] charging/battery stats
- [ ] thermal/power
- [ ] SELinux enforcing

Do not claim support until each item has device-tested evidence.
