#!/usr/bin/env python3
"""Check presence and consistency of manually collected reference-build evidence."""
import argparse
import hashlib
import pathlib
import sys
import xml.etree.ElementTree as ET

def verify(directory):
    errors = []
    expected = ("locked-manifest.xml", "manifest-repository-commit.txt", "metadata.txt",
                "cuttlefish-observations.txt", "manifest.sha256")
    for name in expected:
        if not (directory / name).is_file() or not (directory / name).stat().st_size:
            errors.append(f"Missing evidence: {name}")
    if errors:
        return errors
    manifest = directory / "locked-manifest.xml"
    digest = hashlib.sha256(manifest.read_bytes()).hexdigest()
    if (directory / "manifest.sha256").read_text().strip() != digest:
        errors.append("Manifest checksum mismatch")
    try:
        root = ET.parse(manifest).getroot()
        if root.tag != "manifest" or not root.findall(".//project"):
            errors.append("Missing resolved manifest projects")
    except ET.ParseError:
        errors.append("Invalid manifest XML")
    commit = (directory / "manifest-repository-commit.txt").read_text().strip()
    if len(commit) != 40 or any(c not in "0123456789abcdefABCDEF" for c in commit):
        errors.append("Manifest repository commit is not a 40-character SHA")
    observations = (directory / "cuttlefish-observations.txt").read_text()
    if "Enforcing" not in observations or "Boot complete:" not in observations:
        errors.append("Cuttlefish evidence missing SELinux/boot fields")
    # This validates artifacts, not the provenance of the observation or actual boot.
    return errors

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=pathlib.Path)
    args = parser.parse_args()
    try:
        errors = verify(args.directory)
    except OSError as exc:
        errors = [str(exc)]
    for error in errors:
        print(f"FAIL: {error}", file=sys.stderr)
    if errors:
        return 1
    print("PASS: evidence artifacts have required fields and matching manifest checksum")
    return 0

if __name__ == "__main__":
    sys.exit(main())
