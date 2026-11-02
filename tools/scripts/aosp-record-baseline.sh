#!/usr/bin/env bash
set -euo pipefail
root="${1:?Usage: aosp-record-baseline.sh /path/to/aosp /path/to/evidence}"
out="${2:?Usage: aosp-record-baseline.sh /path/to/aosp /path/to/evidence}"
[[ -d "$root/.repo" ]] || { echo "ERROR: not an AOSP repo checkout"; exit 1; }
mkdir -p "$out"
cd "$root"
command -v repo >/dev/null
repo manifest -r -o "$out/locked-manifest.xml"
git -C .repo/manifests rev-parse HEAD > "$out/manifest-repository-commit.txt"
{
  printf 'Captured UTC: '; date -u +%FT%TZ
  printf 'Host: '; uname -a
  printf 'Manifest repository SHA: '; cat "$out/manifest-repository-commit.txt"
  printf 'Git version: '; git --version
  printf 'Repo version: '; repo version | head -n 5
} > "$out/metadata.txt"
echo "Recorded resolved revisions to $out; inspect before sharing publicly."
