import importlib.util
import pathlib
import tempfile
import unittest

SCRIPT = pathlib.Path(__file__).resolve().parents[2] / "tools/scripts/validate-locked-manifest.py"
spec = importlib.util.spec_from_file_location("manifest_validator", SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
SHA = "a" * 40

class ManifestValidationTests(unittest.TestCase):
    def verify(self, contents):
        with tempfile.TemporaryDirectory() as folder:
            path = pathlib.Path(folder) / "manifest.xml"
            path.write_text(contents)
            return module.validate(path)

    def test_pinned(self):
        count, errors = self.verify(f'<manifest><project name="platform/build" revision="{SHA}"/></manifest>')
        self.assertEqual((count, errors), (1, []))

    def test_branch_rejected(self):
        _, errors = self.verify('<manifest><project name="platform/build" revision="main"/></manifest>')
        self.assertTrue(errors)

    def test_missing_revision_rejected(self):
        _, errors = self.verify('<manifest><project name="platform/build"/></manifest>')
        self.assertTrue(errors)

    def test_empty_rejected(self):
        _, errors = self.verify('<manifest/>')
        self.assertTrue(errors)

    def test_unresolved_include_rejected(self):
        _, errors = self.verify(f'<manifest><include name="other.xml"/><project name="a" revision="{SHA}"/></manifest>')
        self.assertTrue(errors)

    def test_duplicate_project_path_rejected(self):
        _, errors = self.verify(f'<manifest><project name="a" path="same" revision="{SHA}"/><project name="a" path="same" revision="{SHA}"/></manifest>')
        self.assertTrue(errors)

if __name__ == "__main__":
    unittest.main()
