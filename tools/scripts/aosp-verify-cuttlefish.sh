#!/usr/bin/env bash
set -euo pipefail
root="${1:?Usage: aosp-verify-cuttlefish.sh /path/to/aosp /path/to/evidence}"
out="${2:?Usage: aosp-verify-cuttlefish.sh /path/to/aosp /path/to/evidence}"
serial="${3:-${AURA_ADB_SERIAL:-}}"
[[ -f "$root/build/envsetup.sh" ]] || { echo "ERROR: AOSP checkout not synced" >&2; exit 1; }
command -v adb >/dev/null || { echo "ERROR: adb missing" >&2; exit 1; }
[[ -n "$serial" ]] || { echo "ERROR: explicit adb serial required as third argument or AURA_ADB_SERIAL" >&2; exit 1; }
[[ "$serial" =~ ^[A-Za-z0-9._:-]+$ ]] || { echo "ERROR: invalid serial" >&2; exit 1; }
mkdir -p "$out"
devices="$(adb devices)"
printf '%s\n' "$devices" | grep -Fqx "$serial"$'\t'"device" || { echo "ERROR: selected device not online" >&2; exit 1; }
fingerprint="$(adb -s "$serial" shell getprop ro.build.fingerprint | tr -d '\r')"
boot="$(adb -s "$serial" shell getprop sys.boot_completed | tr -d '\r')"
selinux="$(adb -s "$serial" shell getenforce | tr -d '\r')"
arch="$(adb -s "$serial" shell uname -m | tr -d '\r')"
{
  printf 'Device serial: %s\n' "$serial"
  printf 'Build fingerprint:\n%s\n' "$fingerprint"
  printf 'Boot complete:\n%s\n' "$boot"
  printf 'SELinux mode:\n%s\n' "$selinux"
  printf 'Kernel architecture:\n%s\n' "$arch"
} > "$out/cuttlefish-observations.txt"
[[ -n "$fingerprint" && "$boot" == "1" && "$selinux" == "Enforcing" && -n "$arch" ]] || {
  echo "FAIL: device not booted or SELinux not enforcing" >&2; exit 1;
}
echo "PASS: selected Android device reports boot complete and Enforcing; verify Cuttlefish provenance separately."
