"""Offline layout checks only. Passing does not prove Android Binder security."""
import pathlib
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]
BASE = ROOT / "platform/aura/android-adapter/probe"
NS = "{http://schemas.android.com/apk/res/android}"


class BinderProbeLayoutTests(unittest.TestCase):
    def test_host_service_is_separate_exported_process(self):
        service = ET.parse(BASE / "host/AndroidManifest.xml").find(".//service")
        self.assertIsNotNone(service)
        self.assertEqual(":remote", service.attrib[NS + "process"])
        self.assertEqual("true", service.attrib[NS + "exported"])

    def test_declared_permission_is_signature_protected(self):
        permission = ET.parse(BASE / "host/AndroidManifest.xml").find(".//permission")
        self.assertEqual("signature", permission.attrib[NS + "protectionLevel"])

    def test_attacker_is_not_permitted(self):
        attacker = ET.parse(BASE / "attacker/AndroidManifest.xml")
        host = ET.parse(BASE / "host/AndroidManifest.xml")
        self.assertNotEqual(attacker.getroot().attrib["package"],
                            host.getroot().attrib["package"])
        self.assertEqual([], attacker.findall(".//uses-permission"))

    def test_soong_declares_separate_test_certificates(self):
        bp = (BASE / "Android.bp").read_text()
        for module in ("AuraBinderProbeHost", "AuraBinderProbeAttacker",
                       "AuraBinderProbeTests"):
            self.assertIn('name: "' + module + '"', bp)
        self.assertIn('certificate: "testkey"', bp)
        self.assertIn('certificate: "media"', bp)
        self.assertNotIn("system_server", bp)


if __name__ == "__main__":
    unittest.main()
