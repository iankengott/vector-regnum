#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd -P)"

die() { printf 'priority 27 verification failed: %s\n' "$*" >&2; exit 1; }
require_symbol() {
    local description="$1" pattern="$2"; shift 2
    rg -q -- "$pattern" "$@" || die "$description is missing"
}

cd -- "$REPO_ROOT"
command -v jq >/dev/null 2>&1 || die 'jq is required'
command -v rg >/dev/null 2>&1 || die 'rg is required'

API_ROOT="$REPO_ROOT/src/main/java/vectorregnum/api/v1"
ADAPTER_ROOT="$REPO_ROOT/src/main/java/vectorregnum/neoforge/api/v1"
MANIFEST="$REPO_ROOT/src/main/resources/data/vector_regnum/registration_parity.json"
GAME_TEST="$REPO_ROOT/src/main/java/vectorregnum/neoforge/gametest/Priority27GameTests.java"

[[ -d "$API_ROOT" ]] || die 'loader-neutral v1 API package is missing'
[[ -d "$ADAPTER_ROOT" ]] || die 'NeoForge v1 API adapter package is missing'
[[ -f "$GAME_TEST" ]] || die 'Priority27GameTests.java is missing'

for symbol in VectorRegnumApiV1 IntegrationRegistry CastContext CastParameters CastModifier \
        PlayerMagicSnapshot ManaRegionSnapshot DisruptionRequest DisruptionResult StoryEvent; do
    require_symbol "$symbol" "(record|enum|interface|class)[[:space:]]+$symbol\\b" "$API_ROOT"
done
require_symbol 'NeoForge API facade' 'class[[:space:]]+VectorRegnumApi\b' "$ADAPTER_ROOT"
require_symbol 'API version 1' 'VERSION[[:space:]]*=[[:space:]]*1' "$API_ROOT"
require_symbol 'provider cap' 'MAX_.*PROVIDERS[[:space:]]*=[[:space:]]*8' "$API_ROOT"
require_symbol 'story-listener cap' 'MAX_STORY_LISTENERS[[:space:]]*=[[:space:]]*8' "$API_ROOT"
require_symbol 'mana query radius bound' 'MAX_QUERY_RADIUS[[:space:]]*=[[:space:]]*64' "$API_ROOT" "$ADAPTER_ROOT"
require_symbol 'mana query entry bound' 'MAX_QUERY_ENTRIES[[:space:]]*=[[:space:]]*256' "$API_ROOT" "$ADAPTER_ROOT"

if rg -n '^(import|[[:space:]]*requires)[[:space:]].*(net\\.minecraft|net\\.neoforged|foundry|veil|regnum_(origins|combat|progression|world_story|administration))' "$API_ROOT"; then
    die 'loader-neutral v1 API imports a loader, game, renderer, or companion package'
fi
if rg -ni '(modId[[:space:]]*=[[:space:]]*"regnum_|implementation.*regnum-|runtimeOnly.*regnum-)' \
        build.gradle src/main/resources/META-INF/neoforge.mods.toml; then
    die 'Vector-Regnum declares a hard companion dependency'
fi

jq -e '
    .integration_api.version == 1 and
    .integration_api.optional == true and
    .integration_api.domains == [
      "origins", "combat", "progression", "world_story", "administration", "modpack"
    ] and
    .integration_api.bounds.max_providers == 8 and
    .integration_api.bounds.max_story_listeners == 8 and
    .integration_api.bounds.max_query_radius == 64 and
    .integration_api.bounds.max_query_entries == 256
' "$MANIFEST" >/dev/null || die 'registration parity integration_api contract is missing or stale'

require_symbol 'live integration API parity check' 'checkIntegrationApi' \
    "$REPO_ROOT/src/main/java/vectorregnum/neoforge/gametest/Priority20RegistrationParityGameTests.java"
require_symbol 'priority 27 visual checkpoint' 'milestone=priority_27' \
    "$REPO_ROOT/src/main/java/vectorregnum/neoforge/DevShowcaseController.java"

game_test_count="$(rg -c '@GameTest\b' "$GAME_TEST" || true)"
[[ "$game_test_count" == 6 ]] || die "expected 6 Priority 27 GameTests, found $game_test_count"

./gradlew --no-daemon test \
    --tests 'vectorregnum.api.v1.ApiContractTest' \
    --tests 'vectorregnum.api.v1.IntegrationRegistryTest' \
    --tests 'vectorregnum.api.v1.CastModifierTest' \
    --tests 'vectorregnum.api.v1.ManaRegionSnapshotTest'

find src -type f -name '*.json' -print0 | xargs -0 -r -n1 jq empty
find scripts -type f -name '*.sh' -print0 | xargs -0 -r -n1 bash -n
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    git diff --check
fi

printf 'PRIORITY27_VERIFY_OK junit_classes=4 gametests=6 api_version=1 domains=6\n'
