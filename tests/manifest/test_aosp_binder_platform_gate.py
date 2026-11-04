"""Offline contract tests only; never claim a real AOSP build from these fixtures."""
import hashlib
import importlib.util
import os
import pathlib
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "tools/scripts/verify-aosp-binder-platform-evidence.py"
GATE = ROOT / "tools/scripts/aosp-binder-platform-gate.sh"
spec = importlib.util.spec_from_file_location("aosp_binder_gate", SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
SOURCE_SHA = "e" * 40


def fixture(root):
    manifest = (b'<manifest><project name="platform/build" path="build/make" '
                b'revision="' + b"a" * 40 + b'"/></manifest>')
    manifest_sha = hashlib.sha256(manifest).hexdigest()
    (root / "locked-manifest.xml").write_bytes(manifest)
    (root / "manifest.sha256").write_text(manifest_sha + "\n")
    (root / "manifest-repository-commit.txt").write_text("b" * 40 + "\n")
    (root / "metadata.txt").write_text("Captured UTC: 2026-11-03T00:00:00Z\n")
    (root / "cuttlefish-observations.txt").write_text(
        "Build fingerprint:\naosp_cf/fingerprint\n"
        "Boot complete:\n1\nSELinux mode:\nEnforcing\n"
    )
    (root / "built-image-fingerprint.txt").write_text("aosp_cf/fingerprint\n")
    (root / "cuttlefish-platform-properties.txt").write_text(
        "product=aosp_cf_x86_64_only_phone\nhardware=cutf_cvm\nbuild_type=userdebug\n"
    )
    (root / "aosp-binder-build-target.txt").write_text(
        f"source_commit={SOURCE_SHA}\n"
        "lunch=aosp_cf_x86_64_only_phone-aosp_current-userdebug\n"
        f"manifest_sha={manifest_sha}\nfull_build=PASS\nsoong_modules=PASS\n"
    )
    (root / "aosp-binder-instrumentation.txt").write_text(
        "INSTRUMENTATION_STATUS: class=com.aura.os.binderprobe.tests.BinderSecurityIpcTest\n"
        "OK (2 tests)\nINSTRUMENTATION_CODE: -1\n"
    )
    (root / "apks").mkdir()
    hashes = {}
    for role, name in module.APKS.items():
        file = root / "apks" / name
        file.write_bytes(b"test-only-" + role.encode())
        hashes[role] = hashlib.sha256(file.read_bytes()).hexdigest()
    (root / "apk-sha256.txt").write_text(
        "".join(f"{role}={value}\n" for role, value in hashes.items())
    )
    (root / "signer-sha256.txt").write_text(
        f"host={'a' * 64}\nattacker={'b' * 64}\ntests={'a' * 64}\n"
    )
    return manifest_sha


class AospBinderPlatformGateTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.temp.name)
        self.sha = fixture(self.root)

    def tearDown(self):
        self.temp.cleanup()

    def issues(self):
        return module.verify(self.root, self.sha, SOURCE_SHA)

    def test_complete_fixture_passes_consistency_only(self):
        self.assertEqual([], self.issues())

    def test_wrong_manifest_expected_hash_fails(self):
        self.assertTrue(module.verify(self.root, "c" * 64, SOURCE_SHA))
        (self.root / "manifest.sha256").write_text("0" * 64)
        self.assertTrue(self.issues())

    def test_unpinned_manifest_rejected_even_with_matching_hash(self):
        manifest = self.root / "locked-manifest.xml"
        manifest.write_text('<manifest><project name="platform/build" revision="main"/></manifest>')
        self.sha = hashlib.sha256(manifest.read_bytes()).hexdigest()
        (self.root / "manifest.sha256").write_text(self.sha + "\n")
        self.assertTrue(self.issues())

    def test_incomplete_device_or_permissive_selinux_rejected(self):
        observation = self.root / "cuttlefish-observations.txt"
        observation.write_text("Boot complete:\n0\nSELinux mode:\nEnforcing\n")
        self.assertTrue(self.issues())
        observation.write_text("Boot complete:\n1\nSELinux mode:\nPermissive\n")
        self.assertTrue(self.issues())

    def test_stale_or_different_running_image_fails_closed(self):
        original = (self.root / "cuttlefish-observations.txt").read_text()
        observations = self.root / "cuttlefish-observations.txt"
        observations.write_text(original.replace(
            "aosp_cf/fingerprint", "aosp_cf/stale-other-build"
        ))
        self.assertTrue(self.issues())
        observations.write_text(original)
        built = self.root / "built-image-fingerprint.txt"
        built.write_text("aosp_cf/build-from-different-checkout\n")
        self.assertTrue(self.issues())

    def test_fingerprint_missing_or_multiple_lines_is_rejected(self):
        built = self.root / "built-image-fingerprint.txt"
        built.unlink()
        self.assertTrue(self.issues())
        built.write_text("aosp_cf/fingerprint\naosp_cf/other\n")
        self.assertTrue(self.issues())
        built.write_text("aosp_cf/fingerprint\n")
        observations = self.root / "cuttlefish-observations.txt"
        observations.write_text(
            observations.read_text().replace("Build fingerprint:", "Fingerprint:")
        )
        self.assertTrue(self.issues())

    def test_generic_emulator_cannot_masquerade_as_cuttlefish(self):
        props = self.root / "cuttlefish-platform-properties.txt"
        props.write_text("product=sdk_gphone\nhardware=ranchu\nbuild_type=userdebug\n")
        self.assertTrue(self.issues())

    def test_incomplete_or_failed_instrumentation_is_rejected(self):
        instrument = self.root / "aosp-binder-instrumentation.txt"
        instrument.write_text("OK (1 test)\nINSTRUMENTATION_CODE: -1\n")
        self.assertTrue(self.issues())
        instrument.write_text("OK (2 tests)\nINSTRUMENTATION_CODE: 0\n")
        self.assertTrue(self.issues())
        instrument.write_text(
            "OK (2 tests)\nFAILURES!!!\nINSTRUMENTATION_CODE: -1\n"
        )
        self.assertTrue(self.issues())

    def test_signer_identity_and_apk_integrity_rejected(self):
        (self.root / "signer-sha256.txt").write_text(
            f"host={'a' * 64}\nattacker={'a' * 64}\ntests={'a' * 64}\n"
        )
        self.assertTrue(self.issues())
        (self.root / "signer-sha256.txt").write_text(
            f"host={'a' * 64}\nattacker={'b' * 64}\ntests={'a' * 64}\n"
        )
        (self.root / "apks" / module.APKS["attacker"]).write_bytes(b"tampered")
        self.assertTrue(self.issues())

    def test_forged_build_fields_or_mismatched_source_rejected(self):
        target = self.root / "aosp-binder-build-target.txt"
        original = target.read_text()
        target.write_text(original.replace("soong_modules=PASS", "soong_modules=SKIPPED"))
        self.assertTrue(self.issues())
        target.write_text(original.replace("source_commit=" + SOURCE_SHA, "source_commit=" + "a" * 40))
        self.assertTrue(self.issues())
        target.write_text(original + "full_build=PASS\n")
        self.assertTrue(self.issues())

    def test_missing_or_symlinked_evidence_rejected(self):
        file = self.root / "aosp-binder-instrumentation.txt"
        file.unlink()
        self.assertTrue(self.issues())
        file.symlink_to(self.root / "metadata.txt")
        self.assertTrue(self.issues())

    def test_shell_comparison_happens_before_install(self):
        script = GATE.read_text(encoding="utf-8")
        fingerprint = script.index('device_fingerprint="$(getprop ro.build.fingerprint)"')
        compare = script.index('"$device_fingerprint" == "$built_fingerprint"')
        installer = script.index('adb -s "$AURA_ADB_SERIAL" install')
        self.assertLess(fingerprint, compare)
        self.assertLess(compare, installer)
        self.assertIn('built-image-fingerprint.txt', script)
    def test_execution_script_fails_closed_without_explicit_opt_in(self):
        env = os.environ.copy()
        for name in (
            "AURA_GATE_EXECUTE", "AURA_AOSP_DIR", "AURA_EVIDENCE_DIR",
            "AURA_ADB_SERIAL", "AURA_EXPECTED_MANIFEST_SHA256",
            "AURA_EXPECTED_SOURCE_COMMIT", "AURA_LUNCH_TARGET",
        ):
            env.pop(name, None)
        run = subprocess.run(
            ["bash", str(GATE)], env=env, capture_output=True, text=True, timeout=8
        )
        self.assertNotEqual(0, run.returncode)
        self.assertIn("AURA_GATE_EXECUTE", run.stderr)


if __name__ == "__main__":
    unittest.main()
