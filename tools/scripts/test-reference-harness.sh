#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
for script in "$root"/tools/scripts/aosp-*.sh; do bash -n "$script"; done
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/fakebin" "$tmp/aosp" "$tmp/evidence"
# Negative path must refuse a directory without an initialized AOSP repo.
if bash "$root/tools/scripts/aosp-record-baseline.sh" "$tmp/aosp" "$tmp/evidence"; then
  echo "FAIL: accepted checkout without .repo" >&2; exit 1
fi
# Emulator inspection must refuse a directory without a synced AOSP tree.
if bash "$root/tools/scripts/aosp-verify-cuttlefish.sh" "$tmp/aosp" "$tmp/evidence"; then
  echo "FAIL: accepted checkout without envsetup" >&2; exit 1
fi
# Deterministic fake commands: no network or Android device required.
mkdir -p "$tmp/aosp/.repo/manifests" "$tmp/aosp/build"
touch "$tmp/aosp/build/envsetup.sh"
cat > "$tmp/fakebin/repo" <<'EOF'
#!/usr/bin/env bash
case "$1" in
 manifest) printf '<manifest><project name="test" revision="abcdef"/></manifest>\n';;
 version) printf 'repo test stub\n';;
 *) exit 2;;
esac
EOF
cat > "$tmp/fakebin/git" <<'EOF'
#!/usr/bin/env bash
case "$*" in
 *rev-parse*) printf '0123456789abcdef\n';;
 *--version*) printf 'git test stub\n';;
 *) exit 2;;
esac
EOF
cat > "$tmp/fakebin/adb" <<'EOF'
#!/usr/bin/env bash
case "$*" in
 'devices -l') printf 'List of devices attached\ntest-device device\n';;
 'shell getprop ro.build.fingerprint') echo test/fingerprint;;
 'shell getprop sys.boot_completed') echo 1;;
 'shell getenforce') echo Enforcing;;
 'shell uname -m') echo x86_64;;
 *) exit 2;;
esac
EOF
chmod +x "$tmp/fakebin/"*
PATH="$tmp/fakebin:$PATH" bash "$root/tools/scripts/aosp-record-baseline.sh" "$tmp/aosp" "$tmp/evidence"
test -s "$tmp/evidence/locked-manifest.xml"
grep -q 0123456789abcdef "$tmp/evidence/manifest-repository-commit.txt"
PATH="$tmp/fakebin:$PATH" bash "$root/tools/scripts/aosp-verify-cuttlefish.sh" "$tmp/aosp" "$tmp/evidence"
grep -q Enforcing "$tmp/evidence/cuttlefish-observations.txt"
bash "$root/tools/scripts/test-aosp-host-readiness.sh"
echo "PASS: reference harness syntax, negative gates and stub evidence"
