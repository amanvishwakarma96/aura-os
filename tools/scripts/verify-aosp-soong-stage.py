#!/usr/bin/env python3
"""Fail-closed static AURA Soong source-layout check before expensive AOSP builds.

This is NOT a Blueprint compiler, a Soong build, or an Android boot test.
Checks the exact known AURA source module graph and the staged file layout.
"""
import argparse
from pathlib import Path
import re
import sys

EXPECTED_MODULES = {
    "platform/aura/framework/Android.bp": {
        "aura-framework-core": "java_library",
    },
    "platform/aura/android-adapter/Android.bp": {
        "aura-binder-android-adapter-sources": "filegroup",
    },
    "platform/aura/android-adapter/probe/Android.bp": {
        "AuraBinderProbeHost": "android_app",
        "AuraBinderProbeAttacker": "android_test_helper_app",
        "AuraBinderProbeTests": "android_test",
    },
}
REQUIRED_FILES = (
    "platform/aura/android-adapter/probe/host/AndroidManifest.xml",
    "platform/aura/android-adapter/probe/attacker/AndroidManifest.xml",
    "platform/aura/android-adapter/probe/tests/AndroidManifest.xml",
)
SOURCE_PATTERNS = {
    "platform/aura/framework/src": "*.kt",
    "platform/aura/android-adapter/src/com/aura/os/binder": "*.kt",
    "platform/aura/android-adapter/probe/host/src": "*.kt",
    "platform/aura/android-adapter/probe/attacker/src": "*.kt",
    "platform/aura/android-adapter/probe/tests/src": "*.java",
}
BLOCKED_BUILD_TOKENS = (
    "PRODUCT_PACKAGES",
    "SystemServer",
    "LOCAL_PRIVILEGED_MODULE",
    "privileged:",
)


def inspect(root: Path) -> list[str]:
    issues = []
    if root.is_symlink() or not root.is_dir():
        return ["AURA source/stage root missing or symbolic link"]
    observed_modules = set()
    for relative, expected in EXPECTED_MODULES.items():
        path = root / relative
        if path.is_symlink() or not path.is_file():
            issues.append(f"Missing/unsafe AURA Blueprint: {relative}")
            continue
        source = path.read_text(encoding="utf-8")
        # The exact expected module names must exist in the expected module kinds.
        declarations = re.findall(
            r'\b(java_library|filegroup|android_app|android_test_helper_app|android_test)\s*'
            r'\{\s*name:\s*"([^"]+)"', source
        )
        found = {name: kind for kind, name in declarations}
        if len(found) != len(declarations):
            issues.append(f"Duplicate Soong modules: {relative}")
        if found != expected:
            issues.append(f"Unexpected Soong module graph: {relative}")
        for name in found:
            if name in observed_modules:
                issues.append(f"Duplicate global Soong module: {name}")
            observed_modules.add(name)
        executable = re.sub(r'(?m)^\\s*//[^\\n]*
            issues.append(f"Production/privileged module wiring in test-only Blueprint: {relative}")
    probe = root / "platform/aura/android-adapter/probe/Android.bp"
    if probe.is_file() and not probe.is_symlink():
        src = probe.read_text(encoding="utf-8")
        if 'certificate: "testkey"' not in src or 'certificate: "media"' not in src:
            issues.append("Host and negative caller must use separate AOSP test signer aliases")
        if 'instrumentation_for: "AuraBinderProbeHost"' not in src:
            issues.append("Binder instrumentation target is not explicitly bound")
        if '":aura-binder-android-adapter-sources"' not in src:
            issues.append("Binder host does not compile real AURA adapter sources")
    for relative in REQUIRED_FILES:
        path = root / relative
        if path.is_symlink() or not path.is_file() or path.stat().st_size == 0:
            issues.append(f"Missing/unsafe test APK manifest: {relative}")
    for relative, pattern in SOURCE_PATTERNS.items():
        folder = root / relative
        if folder.is_symlink() or not folder.is_dir():
            issues.append(f"Missing/unsafe source tree: {relative}")
            continue
        matching = list(folder.rglob(pattern))
        if not matching or any(not p.is_file() or p.is_symlink() for p in matching):
            issues.append(f"Missing/unsafe test module source: {relative}/{pattern}")
    return issues


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("root", type=Path, help="AURA repository root or isolated AOSP staging root")
    args = parser.parse_args()
    errors = inspect(args.root.resolve())
    if errors:
        for issue in errors:
            print("FAIL: " + issue, file=sys.stderr)
        return 1
    print("PASS: AURA test-only Soong source graph and stage source files are consistent")
    print("NOTE: only a full AOSP/Soong build can verify actual module compilation")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
, '', source)
        if any(token in executable for token in BLOCKED_BUILD_TOKENS):
            issues.append(f"Production/privileged module wiring in test-only Blueprint: {relative}")
    probe = root / "platform/aura/android-adapter/probe/Android.bp"
    if probe.is_file() and not probe.is_symlink():
        src = probe.read_text(encoding="utf-8")
        if 'certificate: "testkey"' not in src or 'certificate: "media"' not in src:
            issues.append("Host and negative caller must use separate AOSP test signer aliases")
        if 'instrumentation_for: "AuraBinderProbeHost"' not in src:
            issues.append("Binder instrumentation target is not explicitly bound")
        if '":aura-binder-android-adapter-sources"' not in src:
            issues.append("Binder host does not compile real AURA adapter sources")
    for relative in REQUIRED_FILES:
        path = root / relative
        if path.is_symlink() or not path.is_file() or path.stat().st_size == 0:
            issues.append(f"Missing/unsafe test APK manifest: {relative}")
    for relative, pattern in SOURCE_PATTERNS.items():
        folder = root / relative
        if folder.is_symlink() or not folder.is_dir():
            issues.append(f"Missing/unsafe source tree: {relative}")
            continue
        matching = list(folder.rglob(pattern))
        if not matching or any(not p.is_file() or p.is_symlink() for p in matching):
            issues.append(f"Missing/unsafe test module source: {relative}/{pattern}")
    return issues


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("root", type=Path, help="AURA repository root or isolated AOSP staging root")
    args = parser.parse_args()
    errors = inspect(args.root.resolve())
    if errors:
        for issue in errors:
            print("FAIL: " + issue, file=sys.stderr)
        return 1
    print("PASS: AURA test-only Soong source graph and stage source files are consistent")
    print("NOTE: only a full AOSP/Soong build can verify actual module compilation")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
