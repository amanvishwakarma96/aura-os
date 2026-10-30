# Manifest lock validation gate

The source baseline is **not pinned yet**. This milestone provides an offline validator and fixtures; it does not invent a manifest SHA or claim an AOSP build.

After syncing a genuine checkout and running `repo manifest -r -o locked-manifest.xml`, execute:

```bash
python3 tools/scripts/validate-locked-manifest.py locked-manifest.xml
python3 -m unittest discover -s tests/manifest -p 'test_*.py' -v
```

The validator rejects missing or floating project revisions, unresolved includes/submanifests, empty manifests and duplicate project/path combinations. It accepts only complete 40-character Git SHA-1 project revisions. A successful result checks *syntax and revision pinning*, not source authenticity, completeness, reproducibility, security or emulator compatibility.

Next: capture a real `repo manifest -r` and its SHA-256 checksum from a supported Linux host, archive the selected target/toolchain and build evidence, then compile/boot a Cuttlefish reference target. Do not distribute proprietary blobs or flash a device.
