#!/usr/bin/env bash
set -euo pipefail
root="${1:?Usage: aosp-record-baseline.sh /path/to/aosp /path/to/evidence}"
out="${2:?Usage: aosp-record-baseline.sh /path/to/aosp /path/to/evidence}"
[[ -d "$root/.repo" ]] || { echo "ERROR: not an AOSP repo checkout" >&2; exit 1; }
command -v repo >/dev/null
command -v sha256sum >/dev/null
mkdir -p "$out"
out="$(cd "$out" && pwd)"
cd "$root"
repo manifest -r -o "$out/locked-manifest.xml"
test -s "$out/locked-manifest.xml"
git -C .repo/manifests rev-parse HEAD > "$out/manifest-repository-commit.txt"
sha256sum "$out/locked-manifest.xml" | awk '{print $1}' > "$out/manifest.sha256"
{
  printf 'Captured UTC: '; date -u +%FT%TZ
  printf 'Host: '; uname -a
  printf 'Manifest repository SHA: '; cat "$out/manifest-repository-commit.txt"
  printf 'Git version: '; git --version
  printf 'Repo version: '; repo version | head -n 5
} > "$out/metadata.txt"
echo "Recorded resolved revisions and SHA-256 checksum to $out; inspect before sharing."
