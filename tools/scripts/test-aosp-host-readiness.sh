#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
bash -n "$root/tools/scripts/aosp-host-readiness.sh"
if AURA_MIN_RAM_GIB=999999 AURA_MIN_DISK_GIB=999999 bash "$root/tools/scripts/aosp-host-readiness.sh" /tmp >/dev/null 2>&1; then
  echo 'FAIL: oversized resource requirements were allowed'; exit 1
fi
if AURA_MIN_RAM_GIB=invalid bash "$root/tools/scripts/aosp-host-readiness.sh" /tmp >/dev/null 2>&1; then
  echo 'FAIL: invalid threshold was allowed'; exit 1
fi
if bash "$root/tools/scripts/aosp-host-readiness.sh" /definitely-not-a-directory >/dev/null 2>&1; then
  echo 'FAIL: missing target was allowed'; exit 1
fi
echo 'PASS: syntax and negative resource-gate tests'
