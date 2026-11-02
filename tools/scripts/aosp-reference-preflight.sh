#!/usr/bin/env bash
set -euo pipefail
if [[ "$(uname -s)" != Linux ]]; then echo "ERROR: AOSP reference builds require a supported Linux host"; exit 1; fi
for tool in git python3 java repo; do command -v "$tool" >/dev/null || { echo "ERROR: missing $tool"; exit 1; }; done
if [[ ! -e /dev/kvm ]]; then echo "WARNING: /dev/kvm absent; Cuttlefish acceleration unavailable"; fi
printf 'Host: '; uname -sm
printf 'Memory KiB: '; awk '/MemTotal:/ {print $2}' /proc/meminfo
printf 'Disk available bytes: '; df -B1 --output=avail "${1:-$PWD}" | tail -1
echo 'Preflight only: no checkout, build, flashing or device modification performed.'
