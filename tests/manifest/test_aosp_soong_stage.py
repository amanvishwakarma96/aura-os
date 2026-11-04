"""Offline AURA core Soong staging contract tests; no build or device side effects."""
import importlib.util
import pathlib
import shutil
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "tools/scripts/verify-aosp-soong-stage.py"
spec = importlib.util.spec_from_file_location("aura_soong_preflight", SCRIPT)
preflight = importlib.util.module_from_spec(spec)
spec.loader.exec_module(preflight)


class AuraSoongStageTests(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.folder.name)
        shutil.copytree(ROOT / "platform" / "aura", self.root / "platform" / "aura")

    def tearDown(self):
        self.folder.cleanup()

    def test_real_repository_layout_has_no_static_graph_errors(self):
        self.assertEqual([], preflight.inspect(ROOT))

    def test_isolated_staged_layout_has_no_static_graph_errors(self):
        self.assertEqual([], preflight.inspect(self.root))

    def test_missing_framework_blueprint_is_rejected(self):
        (self.root / "platform/aura/framework/Android.bp").unlink()
        self.assertTrue(preflight.inspect(self.root))

    def test_missing_real_binder_adapter_sources_is_rejected(self):
        shutil.rmtree(self.root / "platform/aura/android-adapter/src/com/aura/os/binder")
        self.assertTrue(preflight.inspect(self.root))

    def test_same_signer_for_host_and_attacker_is_rejected(self):
        path = self.root / "platform/aura/android-adapter/probe/Android.bp"
        text = path.read_text()
        path.write_text(text.replace('certificate: "media"', 'certificate: "testkey"'))
        self.assertTrue(preflight.inspect(self.root))

    def test_unknown_or_duplicate_soong_module_is_rejected(self):
        path = self.root / "platform/aura/android-adapter/Android.bp"
        path.write_text(path.read_text() +
            '\nfilegroup { name: "aura-framework-core", srcs: [] }\n')
        self.assertTrue(preflight.inspect(self.root))

    def test_test_only_modules_cannot_register_production_packages(self):
        path = self.root / "platform/aura/android-adapter/probe/Android.bp"
        path.write_text(path.read_text() +
            '\n// PRODUCT_PACKAGES += AuraBinderProbeHost\n')
        self.assertTrue(preflight.inspect(self.root))

    def test_instrumentation_cannot_target_different_host(self):
        path = self.root / "platform/aura/android-adapter/probe/Android.bp"
        path.write_text(path.read_text().replace(
            'instrumentation_for: "AuraBinderProbeHost"',
            'instrumentation_for: "OtherAndroidHost"'))
        self.assertTrue(preflight.inspect(self.root))

    def test_missing_test_host_manifest_is_rejected(self):
        (self.root / "platform/aura/android-adapter/probe/host/AndroidManifest.xml").unlink()
        self.assertTrue(preflight.inspect(self.root))

    def test_source_symlink_is_rejected(self):
        folder = self.root / "platform/aura/android-adapter/probe/host/src"
        shutil.rmtree(folder)
        folder.symlink_to(ROOT / "platform/aura/android-adapter/probe/host/src", target_is_directory=True)
        self.assertTrue(preflight.inspect(self.root))

    def test_empty_root_rejected(self):
        self.assertTrue(preflight.inspect(self.root / "missing"))


if __name__ == "__main__":
    unittest.main()
