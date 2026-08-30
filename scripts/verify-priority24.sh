#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd -P)"
readonly SCRIPT_DIR REPO_ROOT

die() {
    printf 'priority 24 verification failed: %s\n' "$*" >&2
    exit 1
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || die "required command is unavailable: $1"
}

resolve_java_21() {
    local java_path java_version java_major task_jdk

    if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
        java_path="$JAVA_HOME/bin/java"
    elif command -v nix >/dev/null 2>&1; then
        task_jdk="$(nix eval --raw nixpkgs#jdk21.outPath)"
        java_path="$task_jdk/bin/java"
        export JAVA_HOME="$task_jdk"
        export PATH="$task_jdk/bin:$PATH"
    elif command -v java >/dev/null 2>&1; then
        java_path="$(command -v java)"
    else
        die 'Java 21 is unavailable'
    fi

    java_version="$($java_path -version 2>&1 | sed -n '1p')"
    java_major="$(sed -E 's/.*version "([0-9]+).*/\1/' <<< "$java_version")"
    [[ "$java_major" == "21" ]] || die "Java 21 is required; found $java_version"
}

require_symbol() {
    local description="$1"
    local pattern="$2"
    shift 2
    rg -q -- "$pattern" "$@" || die "$description is missing"
}

cd -- "$REPO_ROOT"
require_command jq
require_command rg
resolve_java_21

readonly JAVA_SOURCES="$REPO_ROOT/src/main/java"
readonly VM2_SOURCES="$JAVA_SOURCES/vectorregnum/core/vm2"
readonly OPCODE_SOURCE="$VM2_SOURCES/Opcode.java"
readonly PRIORITY24_GAME_TEST="$JAVA_SOURCES/vectorregnum/neoforge/gametest/Priority24GameTests.java"
readonly GUIDE_SOURCE="$REPO_ROOT/src/main/resources/assets/vector_regnum/guide/field_manual.json"
readonly PARITY_MANIFEST="$REPO_ROOT/src/main/resources/data/vector_regnum/registration_parity.json"
readonly PARITY_GAME_TEST="$JAVA_SOURCES/vectorregnum/neoforge/gametest/Priority20RegistrationParityGameTests.java"

[[ -d "$VM2_SOURCES" ]] || die 'vm2 source directory is missing'
[[ -f "$OPCODE_SOURCE" ]] || die 'vm2 Opcode.java is missing'
[[ -f "$PRIORITY24_GAME_TEST" ]] || die 'Priority24GameTests.java is missing'
[[ -f "$GUIDE_SOURCE" ]] || die 'field_manual.json is missing'
[[ -f "$PARITY_MANIFEST" ]] || die 'registration_parity.json is missing'
[[ -f "$PARITY_GAME_TEST" ]] || die 'live registration parity GameTest is missing'

readonly -a PRIORITY24_OPCODES=(
    STORE_VARIABLE
    LOAD_VARIABLE
    ITERATOR_BEGIN
    ITERATOR_NEXT
    COLLISION
    WATCH_VARIABLE
    SIGNAL
    OUTPUT
    FORK
    JOIN
    CANCEL_BRANCH
    BRANCH_END
)

for opcode in "${PRIORITY24_OPCODES[@]}"; do
    require_symbol "$opcode opcode" "(^|[^[:alnum:]_])${opcode}([^[:alnum:]_]|$)" "$OPCODE_SOURCE"
done

require_symbol 'VmMessage declaration' \
    '(record|class|interface|enum)[[:space:]]+VmMessage\b' "$JAVA_SOURCES"
require_symbol 'Priority24GameTests class' \
    'class[[:space:]]+Priority24GameTests\b' "$PRIORITY24_GAME_TEST"
require_symbol 'Priority24 GameTest annotation' '@GameTest\b' "$PRIORITY24_GAME_TEST"
require_symbol 'priority_24 Hermes marker' 'priority_24' "$JAVA_SOURCES"
require_symbol 'VISUAL_CHECKPOINT_READY Hermes marker' 'VISUAL_CHECKPOINT_READY' "$JAVA_SOURCES"

jq -e '.version == 12' "$GUIDE_SOURCE" >/dev/null ||
    die 'field manual must be version 12'

# The runtime contract requires a named, inspectable cap for each bounded
# shared-memory/branch resource.  The focused JUnit tests below prove the
# numeric behavior; this gate catches an implementation that only documents a
# bound without wiring it into the VM source.
for bound in branch work lifetime variable iterator watcher signal message collision output stack; do
    case "$bound" in
        work) bound_terms='(work|instruction)' ;;
        message) bound_terms='(message|signal|output)' ;;
        collision) bound_terms='(collision|selection)' ;;
        *) bound_terms="$bound" ;;
    esac
    if ! rg -qi -- \
        "(max|limit|cap|bound)[[:alnum:]_]*${bound_terms}|${bound_terms}[[:alnum:]_]*(max|limit|cap|bound)" \
        "$VM2_SOURCES"; then
        die "vm2 has no named bound for $bound state"
    fi
done

# VM state is server-tick-owned.  Java thread pools, futures, and parallel
# streams are forbidden in the loader-neutral VM package.
if rg -n --glob '*.java' \
    '(^|[^[:alnum:]_])(Thread|Executor|ExecutorService|ScheduledExecutorService|CompletableFuture|ForkJoinPool|ForkJoinTask|parallelStream)([^[:alnum:]_]|$)|\.parallel[[:space:]]*\(' \
    "$VM2_SOURCES"; then
    die 'Java thread/executor/parallel-stream machinery reached vm2'
fi

jq -e '
    type == "object" and
    .schema == 1 and
    (.registries | type == "object") and
    ((.registries.blocks | type) == "array") and
    ((.registries.items | type) == "array") and
    ((.registries.block_entity_types | type) == "array") and
    ((.attachments | type) == "array") and
    ((.payloads | type) == "object") and
    ((.payloads.serverbound | type) == "array") and
    ((.payloads.clientbound | type) == "array") and
    ((.creative_tabs | type) == "object") and
    ((.command_roots | type) == "array")
' "$PARITY_MANIFEST" >/dev/null || die 'registration parity manifest shape is invalid'
require_symbol 'registration parity live assertion' \
    'liveRegistrationParityMatchesManifest' "$PARITY_GAME_TEST"

priority24_game_test_count="$(rg -c '@GameTest\b' "$PRIORITY24_GAME_TEST" || true)"
if (( priority24_game_test_count < 1 )); then
    die 'Priority24GameTests declares no @GameTest methods'
fi

# Find the focused JUnit classes by their settled priority-24 vocabulary so a
# package move does not silently make the verifier run only legacy tests.
mapfile -t priority24_test_sources < <(
    rg -l --glob '*Test.java' \
        'STORE_VARIABLE|LOAD_VARIABLE|ITERATOR_BEGIN|ITERATOR_NEXT|COLLISION|WATCH_VARIABLE|SIGNAL|OUTPUT|FORK|JOIN|CANCEL_BRANCH|BRANCH_END|VmMessage|Priority24' \
        "$REPO_ROOT/src/test/java" | sort || true
)
(( ${#priority24_test_sources[@]} > 0 )) ||
    die 'no focused priority-24 JUnit test source was found'

test_args=(--no-daemon test)
for test_source in "${priority24_test_sources[@]}"; do
    require_symbol "JUnit annotation in ${test_source#"$REPO_ROOT/"}" \
        '@(Test|ParameterizedTest|RepeatedTest|TestFactory)' "$test_source"
    test_class="${test_source#"$REPO_ROOT/src/test/java/"}"
    test_class="${test_class%.java}"
    test_class="${test_class//\//.}"
    test_args+=(--tests "$test_class")
done

# Preserve the existing VM/compiler/palette regression coverage alongside the
# new focused classes.
test_args+=(
    --tests 'vectorregnum.core.vm2.ManaCostModelTest'
    --tests 'vectorregnum.core.vm2.SpellVmTest'
    --tests 'vectorregnum.core.vm2.StackTypeAnalyzerTest'
    --tests 'vectorregnum.core.circle.Vm2CircleCompilerTest'
    --tests 'vectorregnum.core.semantic.SemanticVmLowererTest'
    --tests 'vectorregnum.core.presentation.ElementPresentationTest'
    --tests 'vectorregnum.core.presentation.PresentationCompilerTest'
    --tests 'vectorregnum.neoforge.presentation.QuasarEmitterVocabularyTest'
    --tests 'vectorregnum.neoforge.presentation.VanillaParticleAllowlistTest'
    --tests 'vectorregnum.neoforge.presentation.ParticleAllowlistSourceScanTest'
)
./gradlew "${test_args[@]}"

find src -type f -name '*.json' -print0 | xargs -0 -r -n1 jq empty
find scripts -type f -name '*.sh' -print0 | xargs -0 -r -n1 bash -n
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    git diff --check
fi

printf 'PRIORITY24_VERIFY_OK junit_classes=%s gametests=%s opcodes=%s guide=12\n' \
    "${#priority24_test_sources[@]}" "$priority24_game_test_count" \
    "${#PRIORITY24_OPCODES[@]}"
