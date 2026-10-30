#!/usr/bin/env bash
set -euo pipefail
root="${1:?Usage: aosp-verify-cuttlefish.sh /path/to/aosp /path/to/evidence}"
out="${2:?Usage: aosp-verify-cuttlefish.sh /path/to/aosp /path/to/evidence}"
[[ -f "$root/build/envsetup.sh" ]] || { echo "ERROR: AOSP checkout not synced"; exit 1; }
command -v adb >/dev/null || { echo "ERROR: adb missing"; exit 1; }
mkdir -p "$out"
{
  echo "Checking emulator connection (no launch performed)"
  adb devices -l
  echo "Build fingerprint:"
  adb shell getprop ro.build.fingerprint
  echo "Boot complete:"
  adb shell getprop sys.boot_completed
  echo "SELinux mode:"
  adb shell getenforce
  echo "Kernel architecture:"
  adb shell uname -m
} | tee "$out/cuttlefish-observations.txt"
echo "Manual review required: must see a trusted Cuttlefish target, boot_completed=1 and Enforcing."
