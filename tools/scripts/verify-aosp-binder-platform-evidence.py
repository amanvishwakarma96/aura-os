#!/usr/bin/env python3
"""Fail-closed consistency checks for independently collected AOSP/Cuttlefish evidence.

This validator does NOT establish cryptographic provenance of a host or device.
It is only a gate over files produced by a real build/test runner.
"""
import argparse
import hashlib
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

SHA256 = re.compile(r"[0-9a-f]{64}\Z")
SHA1 = re.compile(r"[0-9a-fA-F]{40}\Z")
BUILD_KEYS = {"source_commit", "lunch", "manifest_sha", "full_build", "soong_modules"}
PROPERTIES = {"product", "hardware", "build_type"}
APKS = {"host": "AuraBinderProbeHost.apk", "attacker": "AuraBinderProbeAttacker.apk",
        "tests": "AuraBinderProbeTests.apk"}

def _text(directory, name, limit=131072):
    file = directory / name
    if file.is_symlink() or not file.is_file() or not 0 < file.stat().st_size <= limit:
        raise ValueError("Missing/invalid evidence: " + name)
    return file.read_text(encoding="utf-8", errors="strict")

def _fields(data, allowed):
    result = {}
    for line in data.splitlines():
        if not line or "=" not in line:
            raise ValueError("Malformed evidence field")
        key, value = line.split("=", 1)
        if key not in allowed or key in result or not value:
            raise ValueError("Unknown, duplicate or empty evidence field")
        result[key] = value
    if result.keys() != allowed:
        raise ValueError("Incomplete evidence fields")
    return result

def verify(directory, expected_manifest_sha, expected_source_commit):
    errors = []
    if not SHA256.fullmatch(expected_manifest_sha) or not SHA1.fullmatch(expected_source_commit):
        return ["Expected manifest SHA-256 and source SHA must be supplied exactly"]
    try:
        manifest = directory / "locked-manifest.xml"
        if manifest.is_symlink() or not manifest.is_file() or manifest.stat().st_size > 8_000_000:
            raise ValueError("Missing or oversized locked manifest")
        data = manifest.read_bytes()
        actual = hashlib.sha256(data).hexdigest()
        if actual != expected_manifest_sha:
            errors.append("Locked AOSP manifest differs from preapproved SHA-256")
        if _text(directory, "manifest.sha256").strip() != actual:
            errors.append("Recorded AOSP manifest checksum mismatch")
        root = ET.fromstring(data)
        if root.tag != "manifest":
            errors.append("Not a repo manifest")
        projects = root.findall(".//project")
        if not projects or any(not SHA1.fullmatch(p.get("revision", "")) for p in projects):
            errors.append("AOSP manifest has missing or non-immutable project revisions")
        if root.findall(".//include") or root.findall(".//submanifest"):
            errors.append("AOSP manifest has unresolved includes/submanifests")
        commit = _text(directory, "manifest-repository-commit.txt").strip()
        if not SHA1.fullmatch(commit):
            errors.append("Invalid manifest repository commit")
        _text(directory, "metadata.txt")
        observations = _text(directory, "cuttlefish-observations.txt")
        if not re.search(r"(?m)^Boot complete:\s*\n1\s*$", observations):
            errors.append("Device boot complete is not 1")
        if not re.search(r"(?m)^SELinux mode:\s*\nEnforcing\s*$", observations):
            errors.append("Device SELinux is not Enforcing")
        # Bind the selected VM to the image produced by this AOSP checkout.
        # A Cuttlefish-like product/hardware name alone is insufficient.
        image_lines = _text(directory, "built-image-fingerprint.txt", 1024).splitlines()
        if len(image_lines) != 1 or not (8 <= len(image_lines[0]) <= 512):
            errors.append("Missing or malformed locally built image fingerprint")
        else:
            guest = re.search(r"(?m)^Build fingerprint:\r?\n([^\r\n]+)\r?$", observations)
            if guest is None or guest.group(1) != image_lines[0]:
                errors.append("Running Cuttlefish fingerprint differs from built AOSP image")
        build = _fields(_text(directory, "aosp-binder-build-target.txt"), BUILD_KEYS)
        if build["source_commit"] != expected_source_commit:
            errors.append("Evidence source commit differs from approved PR commit")
        if build["manifest_sha"] != expected_manifest_sha:
            errors.append("Build metadata manifest checksum mismatch")
        if build["full_build"] != "PASS" or build["soong_modules"] != "PASS":
            errors.append("Full AOSP build or Soong module compilation did not pass")
        if not re.fullmatch(r"aosp_cf_[A-Za-z0-9_]+-aosp_[A-Za-z0-9_]+-userdebug", build["lunch"]):
            errors.append("Non-Cuttlefish or non-userdebug lunch target")
        properties = _fields(_text(directory, "cuttlefish-platform-properties.txt"), PROPERTIES)
        if (properties["product"] != build["lunch"].split("-aosp_", 1)[0]
                or "cutf" not in properties["hardware"].lower()):
            errors.append("Selected Android device does not report Cuttlefish identity")
        if properties["build_type"] != "userdebug":
            errors.append("Selected device is not userdebug")
        report = _text(directory, "aosp-binder-instrumentation.txt", 1_000_000)
        if not re.search(r"(?m)^OK \(2 tests\)\s*$", report):
            errors.append("Two successful real Binder instrumentation tests not recorded")
        if not re.search(r"(?m)^INSTRUMENTATION_CODE:\s*-1\s*$", report):
            errors.append("Instrumentation runner did not return success")
        if re.search(r"FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_RESULT: shortMsg|^Error:", report, re.M):
            errors.append("Binder instrument report contains a failure")
        signer = _fields(_text(directory, "signer-sha256.txt"), set(APKS))
        if any(not SHA256.fullmatch(v) for v in signer.values()):
            errors.append("Missing APK signer SHA-256")
        if signer["host"] == signer["attacker"]:
            errors.append("Attacker and trusted host have same signing certificate")
        checksums = _fields(_text(directory, "apk-sha256.txt"), set(APKS))
        for role, filename in APKS.items():
            binary = directory / "apks" / filename
            if binary.is_symlink() or not binary.is_file():
                errors.append("Missing real built APK: " + filename)
                continue
            if binary.stat().st_size > 150_000_000:
                errors.append("APK exceeds evidence size limit: " + filename)
                continue
            with binary.open("rb") as stream:
                digest = hashlib.file_digest(stream, "sha256").hexdigest()
            if checksums[role] != digest:
                errors.append("Built APK digest mismatch: " + filename)
    except (OSError, ValueError, ET.ParseError, UnicodeError, KeyError) as exc:
        errors.append("Evidence rejected: " + str(exc))
    return errors

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=pathlib.Path)
    parser.add_argument("--expected-manifest-sha", required=True)
    parser.add_argument("--expected-source-commit", required=True)
    args = parser.parse_args()
    issues = verify(args.directory, args.expected_manifest_sha, args.expected_source_commit)
    for issue in issues:
        print("FAIL: " + issue, file=sys.stderr)
    if issues:
        return 1
    print("PASS: pinned AOSP source, Cuttlefish observations, Soong artifacts and Binder IPC evidence")
    print("NOTE: provenance still depends on the trusted runner; do not treat as hardware attestation")
    return 0

if __name__ == "__main__":
    sys.exit(main())
