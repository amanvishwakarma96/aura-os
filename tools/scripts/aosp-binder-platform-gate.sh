#!/usr/bin/env bash
# Manual, self-hosted, fail-closed pinned AOSP + Cuttlefish verification gate.
# NEVER run against a physical phone or a production Android user profile.
set -euo pipefail

source_repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
: "${AURA_GATE_EXECUTE:?Explicit AURA_GATE_EXECUTE=YES required}"
[[ "$AURA_GATE_EXECUTE" == YES ]] || { echo 'FAIL: explicit gate opt-in required' >&2; exit 2; }
: "${AURA_AOSP_DIR:?Set pre-provisioned, synced AOSP checkout}"
: "${AURA_EVIDENCE_DIR:?Set isolated empty evidence output directory}"
: "${AURA_ADB_SERIAL:?Set the explicit Cuttlefish adb serial}"
: "${AURA_EXPECTED_MANIFEST_SHA256:?Provide PREAPPROVED resolved AOSP manifest SHA-256}"
: "${AURA_EXPECTED_SOURCE_COMMIT:?Supply approved AURA Git commit SHA}"
: "${AURA_LUNCH_TARGET:?Supply approved Cuttlefish lunch target}"
[[ "$AURA_EXPECTED_MANIFEST_SHA256" =~ ^[0-9a-f]{64}$ ]] || { echo 'FAIL: invalid pinned manifest SHA' >&2; exit 2; }
[[ "$AURA_EXPECTED_SOURCE_COMMIT" =~ ^[0-9a-f]{40}$ ]] || { echo 'FAIL: invalid source commit' >&2; exit 2; }
[[ "$AURA_LUNCH_TARGET" =~ ^aosp_cf_[a-zA-Z0-9_]+-aosp_[a-zA-Z0-9_]+-userdebug$ ]] || { echo 'FAIL: unsupported Cuttlefish lunch target' >&2; exit 2; }
[[ "$AURA_ADB_SERIAL" =~ ^[a-zA-Z0-9._:-]+$ ]] || { echo 'FAIL: invalid adb device serial' >&2; exit 2; }
aosp="$(cd "$AURA_AOSP_DIR" && pwd -P)"
[[ -d "$aosp/.repo" && -f "$aosp/build/envsetup.sh" ]] || { echo 'FAIL: pre-synced AOSP tree missing' >&2; exit 1; }
[[ "$(git -C "$source_repo" rev-parse HEAD)" == "$AURA_EXPECTED_SOURCE_COMMIT" ]] || {
  echo 'FAIL: AURA source SHA does not match approved commit' >&2; exit 1;
}
[[ "$(git -C "$source_repo" status --porcelain)" == '' ]] || {
  echo 'FAIL: AURA source working tree is not clean' >&2; exit 1;
}
for app in repo sha256sum adb apksigner git python3; do
  command -v "$app" >/dev/null || { echo "FAIL: missing $app" >&2; exit 1; }
done
bash "$source_repo/tools/scripts/aosp-host-readiness.sh" "$aosp"

# The evidence output must be absent or empty, never pre-populated with fake results.
mkdir -p "$AURA_EVIDENCE_DIR"
evidence="$(cd "$AURA_EVIDENCE_DIR" && pwd -P)"
[[ "$evidence" != "$aosp" && "$evidence" != "$source_repo" ]] || {
  echo 'FAIL: invalid evidence output path' >&2; exit 1;
}
[[ -z "$(find "$evidence" -mindepth 1 -maxdepth 1 -print -quit)" ]] || {
  echo 'FAIL: evidence directory must be empty' >&2; exit 1;
}

# Resolve and verify every project revision BEFORE staging/building anything.
cd "$aosp"
repo manifest -r -o "$evidence/locked-manifest.xml"
python3 "$source_repo/tools/scripts/validate-locked-manifest.py" "$evidence/locked-manifest.xml"
manifest_sha="$(sha256sum "$evidence/locked-manifest.xml" | awk '{print $1}')"
[[ "$manifest_sha" == "$AURA_EXPECTED_MANIFEST_SHA256" ]] || {
  echo 'FAIL: checkout manifest differs from APPROVED immutable baseline' >&2; exit 1;
}
# The baseline recorder captures manifest revision and host metadata separately.
git -C .repo/manifests rev-parse HEAD > "$evidence/manifest-repository-commit.txt"
printf '%s\n' "$manifest_sha" > "$evidence/manifest.sha256"
{
  printf 'Captured UTC: '; date -u +%FT%TZ
  printf 'Source SHA: %s\n' "$AURA_EXPECTED_SOURCE_COMMIT"
  printf 'AOSP manifest SHA-256: %s\n' "$manifest_sha"
  printf 'Host: '; uname -sm
} > "$evidence/metadata.txt"

# Write to ONLY a new isolated vendor/AURA verification path in the AOSP tree.
stage="$aosp/vendor/aura/verification"
[[ ! -e "$stage" && ! -L "$stage" ]] || {
  echo "FAIL: isolated Soong stage exists; inspect and manually clear $stage" >&2; exit 1;
}
mkdir -p "$stage"
cp -a "$source_repo/platform" "$stage/platform"
echo 'AURA Soong test-only modules staged; no SystemServer or PRODUCT_PACKAGES changes.'
[[ "${AURA_JOBS:-8}" =~ ^[1-9][0-9]?$ ]] || { echo 'FAIL: jobs must be 1-99' >&2; exit 2; }
jobs="${AURA_JOBS:-8}"
# Official AOSP environment; target-specific values must be operator approved.
# shellcheck disable=SC1091
source build/envsetup.sh
lunch "$AURA_LUNCH_TARGET"
build_marker="$evidence/build-start-marker"
touch "$build_marker"
set -o pipefail
m -j "$jobs" 2>&1 | tee "$evidence/full-aosp-build.log"
m -j "$jobs" aura-framework-core AuraBinderProbeHost AuraBinderProbeAttacker AuraBinderProbeTests \
  2>&1 | tee "$evidence/soong-binder-modules.log"
product_out="$(get_build_var PRODUCT_OUT)"
[[ -d "$product_out" ]] || { echo 'FAIL: AOSP product output missing' >&2; exit 1; }

mkdir "$evidence/apks"
declare -A modules=(
  [host]=AuraBinderProbeHost [attacker]=AuraBinderProbeAttacker
  [tests]=AuraBinderProbeTests
)
for role in host attacker tests; do
  module="${modules[$role]}"
  mapfile -d '' -t matched < <(find "$product_out" -type f -name "$module.apk" \
    -newer "$build_marker" -print0)
  [[ "${#matched[@]}" == 1 ]] || {
    echo "FAIL: expected exactly one newly built $module.apk in product output" >&2; exit 1;
  }
  cp -- "${matched[0]}" "$evidence/apks/$module.apk"
  digest="$(sha256sum "$evidence/apks/$module.apk" | awk '{print $1}')"
  printf '%s=%s\n' "$role" "$digest" >> "$evidence/apk-sha256.txt"
  signer="$(apksigner verify --print-certs "$evidence/apks/$module.apk" \
    | awk '/Signer #1 certificate SHA-256 digest:/ {print tolower($NF)}' | head -n 1)"
  [[ "$signer" =~ ^[0-9a-f]{64}$ ]] || { echo "FAIL: signer missing for $module" >&2; exit 1; }
  printf '%s=%s\n' "$role" "$signer" >> "$evidence/signer-sha256.txt"
done

# Inspect a specifically named Cuttlefish device BEFORE installing any test APK.
bash "$source_repo/tools/scripts/aosp-verify-cuttlefish.sh" "$aosp" "$evidence" "$AURA_ADB_SERIAL"
getprop() { adb -s "$AURA_ADB_SERIAL" shell getprop "$1" | tr -d '\r\n'; }
product="$(getprop ro.product.name)"
hardware="$(getprop ro.hardware)"
type="$(getprop ro.build.type)"
expected_product="${AURA_LUNCH_TARGET%%-aosp_*}"
[[ "$product" == "$expected_product" && "${hardware,,}" == *cutf* && "$type" == userdebug ]] || {
  echo "FAIL: selected adb target is NOT the approved Cuttlefish userdebug product" >&2; exit 1;
}
{
  printf 'product=%s\n' "$product"
  printf 'hardware=%s\n' "$hardware"
  printf 'build_type=%s\n' "$type"
} > "$evidence/cuttlefish-platform-properties.txt"

# Only APK installs to this checked, explicitly selected Cuttlefish serial.
for role in host attacker tests; do
  adb -s "$AURA_ADB_SERIAL" install -r "$evidence/apks/${modules[$role]}.apk"
done
adb -s "$AURA_ADB_SERIAL" shell am instrument -w \
  com.aura.os.binderprobe.tests/androidx.test.runner.AndroidJUnitRunner \
  | tee "$evidence/aosp-binder-instrumentation.txt"

{
  printf 'source_commit=%s\n' "$AURA_EXPECTED_SOURCE_COMMIT"
  printf 'lunch=%s\n' "$AURA_LUNCH_TARGET"
  printf 'manifest_sha=%s\n' "$manifest_sha"
  printf 'full_build=PASS\n'
  printf 'soong_modules=PASS\n'
} > "$evidence/aosp-binder-build-target.txt"
python3 "$source_repo/tools/scripts/verify-aosp-binder-platform-evidence.py" \
  "$evidence" --expected-manifest-sha "$AURA_EXPECTED_MANIFEST_SHA256" \
  --expected-source-commit "$AURA_EXPECTED_SOURCE_COMMIT"
echo 'PASS: full AOSP + Soong + selected Cuttlefish Binder test evidence recorded.'
echo 'No physical phone flashed, no production service registered.'
