#!/usr/bin/env bash
set -euo pipefail

DEST="${1:-$HOME/aura/aosp}"
mkdir -p "$DEST"
cd "$DEST"

if ! command -v repo >/dev/null 2>&1; then
  echo "ERROR: repo tool is required. Install it using the official AOSP instructions." >&2
  exit 1
fi

if [ ! -d .repo ]; then
  repo init -u https://android.googlesource.com/platform/manifest -b android-latest-release
else
  echo "Existing .repo detected; not reinitializing."
fi

cat <<'MSG'
AOSP manifest initialized.
Next (large download):
  repo sync -c -j$(nproc 2>/dev/null || sysctl -n hw.ncpu)

Before syncing/building, verify your host meets the current official AOSP requirements.
MSG
