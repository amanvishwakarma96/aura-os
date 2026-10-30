#!/usr/bin/env python3
"""Verify that a resolved repo manifest pins every project to an immutable SHA."""
import argparse
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

SHA = re.compile(r"[0-9a-fA-F]{40}")

def validate(path: pathlib.Path) -> tuple[int, list[str]]:
    root = ET.parse(path).getroot()
    errors = []
    if root.tag != "manifest":
        return 0, ["Not an Android repo manifest"]
    projects = root.findall(".//project")
    if not projects:
        errors.append("Manifest contains no projects")
    seen = set()
    for project in projects:
        name = project.get("name", "")
        key = (name, project.get("path", name))
        revision = project.get("revision", "")
        if not name:
            errors.append("Unnamed project")
        if key in seen:
            errors.append(f"Duplicate project/path: {key}")
        seen.add(key)
        if not SHA.fullmatch(revision):
            errors.append(f"Unpinned project: {name or '<unnamed>'}")
    # Local manifests and submanifests must be resolved before this check.
    for node in root.findall(".//include") + root.findall(".//submanifest"):
        errors.append(f"Unresolved manifest reference: {node.tag}")
    return len(projects), errors

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("manifest", type=pathlib.Path)
    args = parser.parse_args()
    try:
        count, errors = validate(args.manifest)
    except (OSError, ET.ParseError) as exc:
        print(f"FAIL: unreadable or invalid XML: {exc}", file=sys.stderr)
        return 1
    for error in errors:
        print(f"FAIL: {error}", file=sys.stderr)
    if errors:
        return 1
    print(f"PASS: {count} projects have immutable SHA-1 revisions")
    return 0

if __name__ == "__main__":
    sys.exit(main())
