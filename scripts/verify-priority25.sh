#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd -P)"
readonly SCRIPT_DIR REPO_ROOT

die() {
    printf 'priority 25 verification failed: %s\n' "$*" >&2
    exit 1
}

require_symbol() {
    local description="$1"
    local pattern="$2"
    shift 2
    rg -q -- "$pattern" "$@" || die "$description is missing"
}

resolve_java_21() {
    local java_path java_version java_major
    if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
        java_path="$JAVA_HOME/bin/java"
    elif command -v java >/dev/null 2>&1; then
        java_path="$(command -v java)"
    else
        die 'Java 21 is unavailable'
    fi
    java_version="$($java_path -version 2>&1 | sed -n '1p')"
    java_major="$(sed -E 's/.*version "([0-9]+).*/\1/' <<< "$java_version")"
    [[ "$java_major" == "21" ]] || die "Java 21 is required; found $java_version"
}

cd -- "$REPO_ROOT"
command -v jq >/dev/null 2>&1 || die 'jq is required'
command -v rg >/dev/null 2>&1 || die 'rg is required'
resolve_java_21

readonly CORE_RITUAL="$REPO_ROOT/src/main/java/vectorregnum/core/ritual"
readonly NEOFORGE_RITUAL="$REPO_ROOT/src/main/java/vectorregnum/neoforge/ritual"
readonly GAME_TEST="$REPO_ROOT/src/main/java/vectorregnum/neoforge/gametest/Priority25GameTests.java"
readonly COMMANDS="$REPO_ROOT/src/main/java/vectorregnum/neoforge/VectorRegnumCommands.java"
readonly GUIDE="$REPO_ROOT/src/main/resources/assets/vector_regnum/guide/field_manual.json"
readonly PONDER="$REPO_ROOT/src/main/resources/assets/vector_regnum/ponder/scenes.json"
readonly PARITY="$REPO_ROOT/src/main/resources/data/vector_regnum/registration_parity.json"

[[ -d "$CORE_RITUAL" ]] || die 'core ritual package is missing'
[[ -d "$NEOFORGE_RITUAL" ]] || die 'NeoForge ritual package is missing'
[[ -f "$GAME_TEST" ]] || die 'Priority25GameTests.java is missing'

for symbol in CooperativeRitual CooperativeRitualLedger RitualCostAllocator; do
    require_symbol "$symbol" "(record|class)[[:space:]]+$symbol\b" "$CORE_RITUAL"
done
for symbol in CooperativeRitualService CooperativeRitualSavedData RitualEscrowStore; do
    require_symbol "$symbol" "(record|class)[[:space:]]+$symbol\b" "$NEOFORGE_RITUAL"
done

for contract in 'enum[[:space:]]+Mode' 'SPLIT' 'REPLICATE' \
        'maxMana' 'maxReagentUnits' 'maxUpkeep' 'reserve' 'settle'; do
    require_symbol "ritual contract $contract" "$contract" "$CORE_RITUAL" "$NEOFORGE_RITUAL"
done
require_symbol 'approved cooperative VM quote slice' 'cooperativeCopyReservation' \
    "$REPO_ROOT/src/main/java/vectorregnum/neoforge/CastingResourceService.java" \
    "$REPO_ROOT/src/main/java/vectorregnum/neoforge/NeoForgeVmService.java"
require_symbol 'mixed-copy settlement aggregation' 'aggregateOutcomes' \
    "$CORE_RITUAL" "$NEOFORGE_RITUAL"
require_symbol 'actual split upkeep settlement' 'commitSplitUpkeep' \
    "$CORE_RITUAL" "$NEOFORGE_RITUAL"
require_symbol 'actual replicate upkeep settlement' 'commitReplicateUpkeep' \
    "$CORE_RITUAL" "$NEOFORGE_RITUAL"
require_symbol 'committed cooperative effect rollback' 'cancelCommitted' \
    "$REPO_ROOT/src/main/java/vectorregnum/neoforge/effect/PersistentEffectService.java" \
    "$NEOFORGE_RITUAL"
require_symbol 'restart-recoverable cooperative effect identity' 'cooperativeEffectId' \
    "$REPO_ROOT/src/main/java/vectorregnum/neoforge/effect/PersistentEffectService.java" \
    "$REPO_ROOT/src/main/java/vectorregnum/neoforge/gametest/Priority25GameTests.java" \
    "$NEOFORGE_RITUAL"
require_symbol 'retryable cooperative finalization' 'finalization failed safely and will retry' \
    "$NEOFORGE_RITUAL"
require_symbol 'cooperative copy commit metadata' 'CooperativeCopyResult' \
    "$REPO_ROOT/src/main/java/vectorregnum/neoforge/NeoForgeVmService.java" \
    "$REPO_ROOT/src/main/java/vectorregnum/neoforge/CircleAuthoringService.java" \
    "$NEOFORGE_RITUAL"
require_symbol 'ritual refund headroom preflight' 'reservedRitualMana\(player\)' \
    "$REPO_ROOT/src/main/java/vectorregnum/neoforge/ManaData.java"
require_symbol 'continuing-handle allocation GameTest' 'upkeepPerInterval' "$GAME_TEST"

for command in create invite approve decline cancel start status; do
    require_symbol "ritual $command command" "\"$command\"" "$COMMANDS"
done
require_symbol 'explicit max_mana command term' '"max_mana"' "$COMMANDS"
require_symbol 'explicit max_reagents command term' '"max_reagents"' "$COMMANDS"
require_symbol 'explicit max_upkeep command term' '"max_upkeep"' "$COMMANDS"

jq -e '.version == 13 and any(.chapters[].pages[]; .id == "cooperative_rituals")' \
    "$GUIDE" >/dev/null || die 'Field Manual v13 cooperative ritual page is missing'
jq -e '.scenes | any(.id == "cooperative_ritual")' "$PONDER" >/dev/null ||
    die 'cooperative ritual Ponder scene is missing'
jq -e '.attachments | index("vector_regnum:ritual_escrows") != null' "$PARITY" >/dev/null ||
    die 'ritual escrow attachment is absent from registration parity'

game_test_count="$(rg -c '@GameTest\b' "$GAME_TEST" || true)"
[[ "$game_test_count" == 5 ]] || die "expected 5 Priority 25 GameTests, found $game_test_count"
for behavior in savedDataRoundTripPreservesConsentAllocationsAndAuditState \
        declinedPreStartRitualRefundsEveryReservationExactlyOnce \
        splitCircleCombinesManaAndConsumesOneApprovedOffering \
        replicatedVmCircleRunsOneCopyPerContributor approvalRetryUsesOnePlayerEscrowRecord; do
    require_symbol "GameTest $behavior" "$behavior" "$GAME_TEST"
done

if rg -n --glob '*.java' \
    '(^|[^[:alnum:]_])(Thread|Executor|ExecutorService|ScheduledExecutorService|CompletableFuture|ForkJoinPool|ForkJoinTask|parallelStream)([^[:alnum:]_]|$)|\.parallel[[:space:]]*\(' \
    "$CORE_RITUAL" "$NEOFORGE_RITUAL"; then
    die 'thread, executor, future, or parallel-stream machinery reached ritual state'
fi

./gradlew --no-daemon test \
    --tests 'vectorregnum.core.ritual.CooperativeRitualTest' \
    --tests 'vectorregnum.neoforge.ritual.RitualEscrowStoreTest' \
    --tests 'vectorregnum.neoforge.ponder.PonderLessonLibraryTest' \
    --tests 'vectorregnum.neoforge.guide.GuideElementalIdentityTest' \
    --tests 'vectorregnum.neoforge.guide.GuideScreenControllerTest'

find src -type f -name '*.json' -print0 | xargs -0 -r -n1 jq empty
find scripts -type f -name '*.sh' -print0 | xargs -0 -r -n1 bash -n
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    git diff --check
fi

printf 'PRIORITY25_VERIFY_OK junit_classes=5 gametests=5 guide=13 modes=2\n'
