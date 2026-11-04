"""Host-only tests: no Android hardware, USB connection, unlock or flashing."""
import importlib.util
import pathlib
import subprocess
import unittest
from types import SimpleNamespace

ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "tools/scripts/redmi-note9-inventory.py"
spec = importlib.util.spec_from_file_location("aura_redmi_note9_inventory", SCRIPT)
inventory = importlib.util.module_from_spec(spec)
spec.loader.exec_module(inventory)


def fixture(**values):
    data = {
        "device": "merlin",
        "vendor_device": "merlin",
        "product_name": "merlin",
        "model": "Redmi Note 9",
        "architecture": "arm64-v8a",
        "board": "mt6768",
        "hardware": "mt6768",
        "android_version": "12",
        "sdk": "31",
        "bootloader_locked": "1",
        "verified_boot_state": "green",
        "ota_ab": "false",
    }
    data.update(values)
    return data


class RedmiNote9InventoryTests(unittest.TestCase):
    def test_known_note_9_is_candidate_but_never_flash_ready(self):
        report = inventory.assess(fixture())
        self.assertTrue(report["hardware_candidate"])
        self.assertFalse(report["aura_flash_ready"])
        self.assertFalse(report["physical_flash_authorized"])
        self.assertEqual("locked", report["bootloader_state"])
        self.assertIn("No device-specific AURA OS build", " ".join(report["blockers"]))

    def test_merlinx_lineage_codename_is_acceptable_as_candidate(self):
        self.assertTrue(inventory.assess(fixture(
            device="merlinx", vendor_device="merlinx"
        ))["hardware_candidate"])

    def test_different_device_codename_cannot_pass(self):
        self.assertFalse(inventory.assess(fixture(
            device="ginkgo", vendor_device="ginkgo"
        ))["hardware_candidate"])
        self.assertFalse(inventory.assess(fixture(
            device="merlin", vendor_device="joyeuse"
        ))["hardware_candidate"])
        self.assertFalse(inventory.assess(fixture(
            device="", vendor_device=""
        ))["hardware_candidate"])

    def test_note_9_pro_and_incompatible_arch_cannot_pass(self):
        self.assertFalse(inventory.assess(fixture(
            model="Redmi Note 9 Pro"
        ))["hardware_candidate"])
        self.assertFalse(inventory.assess(fixture(
            architecture="armeabi-v7a"
        ))["hardware_candidate"])
        self.assertFalse(inventory.assess(fixture(
            board="unknown", hardware="unknown"
        ))["hardware_candidate"])

    def test_missing_or_reported_unlocked_does_not_enable_flashing(self):
        report = inventory.assess(fixture(bootloader_locked="0"))
        self.assertEqual("unlocked_reported", report["bootloader_state"])
        self.assertFalse(report["aura_flash_ready"])
        report = inventory.assess(fixture(bootloader_locked=""))
        self.assertEqual("unknown", report["bootloader_state"])
        self.assertFalse(report["physical_flash_authorized"])

    def test_only_allowlisted_read_only_getprop_commands(self):
        requests = []
        values = fixture()

        def fake_run(args, **kwargs):
            requests.append((args, kwargs))
            self.assertEqual(["adb", "-s", "test-device", "shell", "getprop"],
                             args[:5])
            self.assertIn(args[5], inventory.PROPERTIES.values())
            self.assertTrue(kwargs["check"])
            self.assertGreater(kwargs["timeout"], 0)
            key = next(k for k, v in inventory.PROPERTIES.items() if v == args[5])
            return SimpleNamespace(stdout=values[key] + "\n")

        result = inventory.read_properties("test-device", run=fake_run)
        self.assertEqual(values, result)
        self.assertEqual(len(inventory.PROPERTIES), len(requests))
        self.assertFalse(any("fastboot" in " ".join(args) for args, _ in requests))

    def test_invalid_serial_fails_before_any_adb_call(self):
        def forbidden(*args, **kwargs):
            raise AssertionError("ADB must not execute")
        for serial in ("", "../dev", "test device", "serial;rm", "x" * 129):
            with self.subTest(serial=serial):
                with self.assertRaises(ValueError):
                    inventory.read_properties(serial, run=forbidden)

    def test_adb_failure_never_activates_flash_path(self):
        def failed(args, **kwargs):
            raise subprocess.CalledProcessError(1, args)
        with self.assertRaises(subprocess.CalledProcessError):
            inventory.read_properties("test-device", run=failed)

    def test_report_never_claims_flashing_is_authorized(self):
        for case in (
            fixture(), fixture(bootloader_locked="0"),
            fixture(device="ginkgo", vendor_device="ginkgo"),
            fixture(board="unknown", hardware="unknown"),
            fixture(model="Redmi Note 9 Pro"),
        ):
            report = inventory.assess(case)
            self.assertIs(report["aura_flash_ready"], False)
            self.assertIs(report["physical_flash_authorized"], False)


if __name__ == "__main__":
    unittest.main()
