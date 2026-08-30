#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd -P)"

die() { printf 'priority 26 verification failed: %s\n' "$*" >&2; exit 1; }
require_symbol() {
    local description="$1" pattern="$2"; shift 2
    rg -q -- "$pattern" "$@" || die "$description is missing"
}

cd -- "$REPO_ROOT"
command -v jq >/dev/null 2>&1 || die 'jq is required'
command -v rg >/dev/null 2>&1 || die 'rg is required'

CORE_SECURITY="$REPO_ROOT/src/main/java/vectorregnum/core/security"
NEOFORGE_MULTIPLAYER="$REPO_ROOT/src/main/java/vectorregnum/neoforge/multiplayer"
GUIDE="$REPO_ROOT/src/main/resources/assets/vector_regnum/guide/field_manual.json"
PONDER="$REPO_ROOT/src/main/resources/assets/vector_regnum/ponder/scenes.json"
GAME_TEST="$REPO_ROOT/src/main/java/vectorregnum/neoforge/gametest/Priority26GameTests.java"

[[ -d "$CORE_SECURITY" ]] || die 'core security package is missing'
[[ -f "$GAME_TEST" ]] || die 'Priority26GameTests.java is missing'
for symbol in MechanicCapability MechanicLimits MechanicRequest MechanicSecurityPolicy \
        MechanicDecision WildMagicEnvelope WildMagicResolver; do
    require_symbol "$symbol" "(record|enum|class)[[:space:]]+$symbol\\b" "$CORE_SECURITY"
done
for symbol in ForcedAttentionService SpellDisruptionService; do
    require_symbol "$symbol" "(final[[:space:]]+)?class[[:space:]]+$symbol\\b" "$NEOFORGE_MULTIPLAYER"
done
require_symbol 'render-only semantic opcode' 'RENDER' \
    "$REPO_ROOT/src/main/java/vectorregnum/core/semantic/SemanticOpcode.java" \
    "$REPO_ROOT/src/main/java/vectorregnum/neoforge/SemanticSpellExecutor.java"
require_symbol 'forced-attention semantic opcode' 'FORCE_ATTENTION' \
    "$REPO_ROOT/src/main/java/vectorregnum/core/semantic/SemanticOpcode.java" \
    "$REPO_ROOT/src/main/java/vectorregnum/neoforge/SemanticSpellExecutor.java"
require_symbol 'render-only path uses trace payloads' 'ServerTraces\.burstAll' \
    "$REPO_ROOT/src/main/java/vectorregnum/neoforge/SemanticSpellExecutor.java"
require_symbol 'bounded disruption cancellation' 'GENUINE_SPELL_FAULT' \
    "$REPO_ROOT/src/main/java/vectorregnum/neoforge/NeoForgeVmService.java"
require_symbol 'accessibility persistence codec' 'PresentationAccessibilityCodec' \
    "$REPO_ROOT/src/main/java/vectorregnum/neoforge/presentation/ClientPresentationRuntime.java"

jq -e '.version == 14 and any(.chapters[].pages[]; .id == "recovered_mechanic_security")' \
    "$GUIDE" >/dev/null || die 'Field Manual v14 security page is missing'
jq -e '.scenes | any(.id == "recovered_mechanic_security")' "$PONDER" >/dev/null \
    || die 'recovered-mechanic Ponder scene is missing'

game_test_count="$(rg -c '@GameTest\b' "$GAME_TEST" || true)"
[[ "$game_test_count" == 3 ]] || die "expected 3 Priority 26 GameTests, found $game_test_count"

./gradlew --no-daemon test \
    --tests 'vectorregnum.core.security.Priority26SecurityTest' \
    --tests 'vectorregnum.core.security.PresentationAccessibilityCodecTest' \
    --tests 'vectorregnum.core.semantic.Priority26SemanticTest' \
    --tests 'vectorregnum.core.semantic.SemanticVmLowererTest'

find src -type f -name '*.json' -print0 | xargs -0 -r -n1 jq empty
find scripts -type f -name '*.sh' -print0 | xargs -0 -r -n1 bash -n
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    git diff --check
fi

printf 'PRIORITY26_VERIFY_OK junit_classes=4 gametests=3 guide=14 capabilities=4\n'
