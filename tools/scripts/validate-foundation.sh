#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
command -v kotlinc >/dev/null || { echo "ERROR: kotlinc required"; exit 1; }
command -v kotlin >/dev/null || { echo "ERROR: kotlin required"; exit 1; }
tmpdir="$(mktemp -d)"
trap 'rm -rf "$tmpdir"' EXIT
mapfile -t sources < <(find "$ROOT/platform/aura/framework/src" -type f -name '*.kt' | sort)
test "${#sources[@]}" -gt 0
kotlinc -Werror "${sources[@]}" "$ROOT/tests/framework/AuraFrameworkSmokeTest.kt" "$ROOT/tests/framework/AuraApprovalGrantStoreTest.kt" "$ROOT/tests/framework/AuraCanonicalArgumentsTest.kt" "$ROOT/tests/framework/AuraAuthorizationCoordinatorTest.kt" "$ROOT/tests/framework/AuraTrustedCallerTest.kt" -d "$tmpdir/aura-framework.jar"
kotlin -classpath "$tmpdir/aura-framework.jar" com.aura.os.tests.AuraFrameworkSmokeTestKt
kotlin -classpath "$tmpdir/aura-framework.jar" com.aura.os.approval.AuraApprovalGrantStoreTestKt
kotlin -classpath "$tmpdir/aura-framework.jar" com.aura.os.approval.AuraCanonicalArgumentsTestKt
kotlin -classpath "$tmpdir/aura-framework.jar" com.aura.os.approval.AuraAuthorizationCoordinatorTestKt
kotlin -classpath "$tmpdir/aura-framework.jar" com.aura.os.approval.AuraTrustedCallerTestKt
echo "PASS: standalone framework Kotlin validation (not an AOSP/Soong build)"
