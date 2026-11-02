import importlib.util
import hashlib
import pathlib
import tempfile
import unittest

SCRIPT = pathlib.Path(__file__).resolve().parents[2] / "tools/scripts/verify-aosp-evidence.py"
spec = importlib.util.spec_from_file_location("evidence", SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class EvidenceTests(unittest.TestCase):
    def test_missing_fails(self):
        with tempfile.TemporaryDirectory() as temp:
            self.assertTrue(module.verify(pathlib.Path(temp)))

    def test_matching_fixture_passes_then_detects_tamper(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            manifest = root / "locked-manifest.xml"
            manifest.write_text('<manifest><project name="platform/build" revision="' + 'a'*40 + '"/></manifest>')
            (root / "manifest.sha256").write_text(hashlib.sha256(manifest.read_bytes()).hexdigest())
            (root / "manifest-repository-commit.txt").write_text("b"*40)
            (root / "metadata.txt").write_text("fake metadata")
            (root / "cuttlefish-observations.txt").write_text("Boot complete:\n1\nSELinux mode:\nEnforcing\n")
            self.assertEqual(module.verify(root), [])
            manifest.write_text("<manifest/>")
            self.assertTrue(module.verify(root))

if __name__ == "__main__":
    unittest.main()
