#!/usr/bin/env python3
"""Non-destructive hardware inventory for AURA's Redmi Note 9 port.

This tool NEVER unlocks, flashes, boots, installs or modifies the connected
Android phone. It cannot declare an AURA image flash-ready.
"""
import argparse
import json
import re
import subprocess
import sys

# Carefully limited to non-identifying properties needed for hardware gating.
PROPERTIES = {
    "device": "ro.product.device",
    "vendor_device": "ro.product.vendor.device",
    "product_name": "ro.product.name",
    "model": "ro.product.model",
    "architecture": "ro.product.cpu.abi",
    "board": "ro.board.platform",
    "hardware": "ro.hardware",
    "android_version": "ro.build.version.release",
    "sdk": "ro.build.version.sdk",
    "bootloader_locked": "ro.boot.flash.locked",
    "verified_boot_state": "ro.boot.verifiedbootstate",
    "ota_ab": "ro.build.ab_update",
}
SERIAL_PATTERN = re.compile(r"[A-Za-z0-9._:-]{1,128}\Z")
ACCEPTED_CODENAMES = {"merlin", "merlinx"}
MEDIA_TEK_PLATFORMS = {"mt6768", "mt6769", "mt6769z"}


def read_properties(serial: str, run=subprocess.run) -> dict[str, str]:
    """Use ONLY read-only adb shell getprop calls with an explicit device serial."""
    if not SERIAL_PATTERN.fullmatch(serial):
        raise ValueError("Explicit ADB serial format rejected")
    result = {}
    for name, prop in PROPERTIES.items():
        response = run(
            ["adb", "-s", serial, "shell", "getprop", prop],
            text=True, capture_output=True, timeout=15, check=True,
        )
        value = response.stdout.strip()
        if len(value) > 256 or any(ord(c) < 32 for c in value):
            raise ValueError("Invalid Android property response")
        result[name] = value
    return result


def assess(properties: dict[str, str]) -> dict:
    """Readiness is ALWAYS blocked until a separately verified signed device build."""
    values = {k: str(properties.get(k, "")).strip() for k in PROPERTIES}
    codes = {values[k].lower() for k in ("device", "vendor_device") if values[k]}
    compatible_code = bool(codes) and codes.issubset(ACCEPTED_CODENAMES)
    model = values["model"].lower()
    note9 = "redmi note 9" in model and "note 9 pro" not in model and "note 9s" not in model
    arm64 = values["architecture"] == "arm64-v8a"
    platform = (values["board"] or values["hardware"]).lower()
    platform_known = platform in MEDIA_TEK_PLATFORMS
    if values["bootloader_locked"] == "1":
        lock_state = "locked"
    elif values["bootloader_locked"] == "0":
        lock_state = "unlocked_reported"
    else:
        lock_state = "unknown"
    findings = []
    if not compatible_code:
        findings.append("Exact merlin/merlinx device codename has not been verified")
    if not note9:
        findings.append("Reported model is not an unambiguous Redmi Note 9")
    if not arm64:
        findings.append("ARM64 Android ABI has not been confirmed")
    if not platform_known:
        findings.append("MediaTek platform needs independent confirmation")
    if lock_state == "locked":
        findings.append("Bootloader is reported locked; do not attempt to flash")
    elif lock_state == "unknown":
        findings.append("Bootloader lock state was not reported")
    findings.append("No device-specific AURA OS build and recovery validation exists")
    findings.append("Full pinned AOSP/Soong/Cuttlefish gate is not yet certified")
    return {
        "device_family": "Xiaomi Redmi Note 9 (merlin / merlinx)",
        "inventory_only": True,
        "hardware_candidate": compatible_code and note9 and arm64 and platform_known,
        "bootloader_state": lock_state,
        "aura_flash_ready": False,
        "physical_flash_authorized": False,
        "properties": values,
        "blockers": findings,
    }


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="Read-only AURA Redmi Note 9 inventory")
    parser.add_argument("--serial", required=True, help="Explicit ADB serial; never printed")
    arguments = parser.parse_args(argv)
    try:
        report = assess(read_properties(arguments.serial))
    except (ValueError, OSError, subprocess.SubprocessError) as exc:
        # Do not echo command-line arguments or physical device identifiers.
        print("FAIL: read-only ADB inventory unavailable (" +
              type(exc).__name__ + ")", file=sys.stderr)
        return 1
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
