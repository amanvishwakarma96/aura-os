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
 manifest) [[ "$2" == '-r' && "$3" == '-o' ]] || exit 2; printf '<manifest><project name="test" revision="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"/></manifest>\n' > "$4";;
 version) printf 'repo test stub\n';;
 *) exit 2;;
esac
EOF
cat > "$tmp/fakebin/git" <<'EOF'
#!/usr/bin/env bash
case "$*" in
 *rev-parse*) printf '0123456789abcdef0123456789abcdef01234567\n';;
 *--version*) printf 'git test stub\n';;
 *) exit 2;;
esac
EOF
cat > "$tmp/fakebin/adb" <<'EOF'
#!/usr/bin/env bash
case "$*" in
 'devices') printf 'List of devices attached\ntest-device\tdevice\n';;
 '-s test-device shell getprop ro.build.fingerprint') echo test/fingerprint;;
 '-s test-device shell getprop sys.boot_completed') echo "${TEST_BOOT:-1}";;
 '-s test-device shell getenforce') echo "${TEST_SELINUX:-Enforcing}";;
 '-s test-device shell uname -m') echo x86_64;;
 *) exit 2;;
esac
EOF
chmod +x "$tmp/fakebin/"*
PATH="$tmp/fakebin:$PATH" bash "$root/tools/scripts/aosp-record-baseline.sh" "$tmp/aosp" "$tmp/evidence"
test -s "$tmp/evidence/locked-manifest.xml"
grep -q 0123456789abcdef "$tmp/evidence/manifest-repository-commit.txt"
test "$(sha256sum "$tmp/evidence/locked-manifest.xml" | cut -d\  -f1)" = "$(cat "$tmp/evidence/manifest.sha256")"
PATH="$tmp/fakebin:$PATH" bash "$root/tools/scripts/aosp-verify-cuttlefish.sh" "$tmp/aosp" "$tmp/evidence" test-device
grep -q Enforcing "$tmp/evidence/cuttlefish-observations.txt"
if PATH="$tmp/fakebin:$PATH" TEST_BOOT=0 bash "$root/tools/scripts/aosp-verify-cuttlefish.sh" "$tmp/aosp" "$tmp/evidence" test-device; then exit 1; fi
if PATH="$tmp/fakebin:$PATH" TEST_SELINUX=Permissive bash "$root/tools/scripts/aosp-verify-cuttlefish.sh" "$tmp/aosp" "$tmp/evidence" test-device; then exit 1; fi
if PATH="$tmp/fakebin:$PATH" bash "$root/tools/scripts/aosp-verify-cuttlefish.sh" "$tmp/aosp" "$tmp/evidence" unknown; then exit 1; fi
bash "$root/tools/scripts/test-aosp-host-readiness.sh"
echo "PASS: reference harness syntax, negative gates and stub evidence"
