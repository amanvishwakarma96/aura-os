#!/usr/bin/env bash
set -euo pipefail
TARGET="${1:-$PWD}"
MIN_RAM_GIB="${AURA_MIN_RAM_GIB:-64}"
MIN_DISK_GIB="${AURA_MIN_DISK_GIB:-400}"
[[ "$MIN_RAM_GIB" =~ ^[0-9]+$ && "$MIN_DISK_GIB" =~ ^[0-9]+$ ]] || { echo 'FAIL: invalid thresholds'; exit 2; }
fail=0
if [[ "$(uname -s)" != Linux ]]; then echo 'FAIL: Linux required'; fail=1; else echo 'PASS: Linux'; fi
for tool in git python3 java repo; do
  if command -v "$tool" >/dev/null 2>&1; then echo "PASS: $tool available"; else echo "FAIL: $tool missing"; fail=1; fi
done
if [[ -c /dev/kvm && -r /dev/kvm && -w /dev/kvm ]]; then echo 'PASS: KVM accessible'; else echo 'FAIL: /dev/kvm not accessible'; fail=1; fi
if [[ ! -d "$TARGET" ]]; then echo "FAIL: target directory missing: $TARGET"; exit 1; fi
RAM_KIB="$(awk '/^MemTotal:/ {print $2}' /proc/meminfo)"
DISK_BYTES="$(df -B1 --output=avail "$TARGET" | tail -n1 | tr -d ' ')"
[[ "$RAM_KIB" =~ ^[0-9]+$ && "$DISK_BYTES" =~ ^[0-9]+$ ]] || { echo 'FAIL: unable to determine host resources'; exit 1; }
RAM_REQUIRED=$((MIN_RAM_GIB * 1024 * 1024))
DISK_REQUIRED=$((MIN_DISK_GIB * 1024 * 1024 * 1024))
if (( RAM_KIB >= RAM_REQUIRED )); then echo 'PASS: memory threshold'; else echo "FAIL: memory ${RAM_KIB} KiB < ${MIN_RAM_GIB} GiB"; fail=1; fi
if (( DISK_BYTES >= DISK_REQUIRED )); then echo 'PASS: disk threshold'; else echo "FAIL: disk ${DISK_BYTES} bytes < ${MIN_DISK_GIB} GiB"; fail=1; fi
if (( fail )); then echo 'BLOCKED: host is not ready for reference build'; exit 1; fi
echo 'READY: basic host resources available; actual AOSP build and boot still unverified'
