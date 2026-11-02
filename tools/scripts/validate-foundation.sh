#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
command -v kotlinc >/dev/null || { echo "ERROR: kotlinc required"; exit 1; }
command -v kotlin >/dev/null || { echo "ERROR: kotlin required"; exit 1; }
tmpdir="$(mktemp -d)"
trap 'rm -rf "$tmpdir"' EXIT
mapfile -t sources < <(find "$ROOT/platform/aura/framework/src" -type f -name '*.kt' | sort)
test "${#sources[@]}" -gt 0
kotlinc -Werror "${sources[@]}" "$ROOT/tests/framework/AuraFrameworkSmokeTest.kt" "$ROOT/tests/framework/AuraApprovalGrantStoreTest.kt" "$ROOT/tests/framework/AuraCanonicalArgumentsTest.kt" "$ROOT/tests/framework/AuraAuthorizationCoordinatorTest.kt" "$ROOT/tests/framework/AuraTrustedCallerTest.kt" "$ROOT/tests/framework/AuraChatGptPlanPolicyTest.kt" "$ROOT/tests/framework/AuraChatGptOAuthPkceTest.kt" "$ROOT/tests/framework/AuraChatGptLoopbackListenerTest.kt" "$ROOT/tests/framework/AuraChatGptSessionManagerTest.kt" "$ROOT/tests/framework/AuraChatGptTokenValidationTest.kt" "$ROOT/tests/framework/AuraChatGptTrustedIssuerKeysTest.kt" -d "$tmpdir/aura-framework.jar"
kotlin -classpath "$tmpdir/aura-framework.jar" com.aura.os.tests.AuraFrameworkSmokeTestKt
kotlin -classpath "$tmpdir/aura-framework.jar" com.aura.os.approval.AuraApprovalGrantStoreTestKt
kotlin -classpath "$tmpdir/aura-framework.jar" com.aura.os.approval.AuraCanonicalArgumentsTestKt
kotlin -classpath "$tmpdir/aura-framework.jar" com.aura.os.approval.AuraAuthorizationCoordinatorTestKt
kotlin -classpath "$tmpdir/aura-framework.jar" com.aura.os.approval.AuraTrustedCallerTestKt
kotlin -classpath "$tmpdir/aura-framework.jar" com.aura.os.provider.AuraChatGptPlanPolicyTestKt
kotlin -classpath "$tmpdir/aura-framework.jar" com.aura.os.provider.AuraChatGptOAuthPkceTestKt
kotlin -classpath "$tmpdir/aura-framework.jar" com.aura.os.provider.AuraChatGptLoopbackListenerTestKt
kotlin -classpath "$tmpdir/aura-framework.jar" com.aura.os.provider.AuraChatGptSessionManagerTestKt
kotlin -classpath "$tmpdir/aura-framework.jar" com.aura.os.provider.AuraChatGptTokenValidationTestKt
kotlin -classpath "$tmpdir/aura-framework.jar" com.aura.os.provider.AuraChatGptTrustedIssuerKeysTestKt
kotlinc -Werror "${sources[@]}" "$ROOT/platform/aura/android-adapter/src/com/aura/os/binder/AuraBinderIdentityResolver.kt" "$ROOT/tests/framework/AuraBinderIdentityResolverHostTest.kt" -d "$tmpdir/aura-binder-host.jar"
kotlin -classpath "$tmpdir/aura-binder-host.jar" com.aura.os.binder.AuraBinderIdentityResolverHostTestKt
echo "PASS: standalone framework Kotlin validation (not an AOSP/Soong build)"
