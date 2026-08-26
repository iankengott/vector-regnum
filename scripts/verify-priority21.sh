#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd -P)"
readonly SCRIPT_DIR REPO_ROOT

die() {
    printf 'priority 21 verification failed: %s\n' "$*" >&2
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

readonly -a AFFINITY_FILES=(
    "$REPO_ROOT/src/main/resources/data/vector_regnum/elemental_affinities.json"
    "$REPO_ROOT/data/vector_regnum/elemental_affinities.json"
)
affinity_file=''
for candidate in "${AFFINITY_FILES[@]}"; do
    if [[ -f "$candidate" ]]; then
        [[ -z "$affinity_file" ]] || die 'elemental_affinities.json exists in more than one data root'
        affinity_file="$candidate"
    fi
done
[[ -n "$affinity_file" ]] ||
    die 'missing data/vector_regnum/elemental_affinities.json'

readonly -a ORDINARY=(water fire air earth lightning time space light dark nature ice sound)
readonly -a RARE=(void)
readonly -a NEUTRAL=(arcane)
readonly -a ALL=("${ORDINARY[@]}" "${RARE[@]}" "${NEUTRAL[@]}")
readonly MATRIX_PATH='.affinities // .matrix'

json_array_equals() {
    local jq_path="$1"
    local expected_json="$2"
    jq -e --argjson expected "$expected_json" \
        "$jq_path | type == \"array\" and (sort == (\$expected | sort))" \
        "$affinity_file" >/dev/null || die "$jq_path does not match the canonical element set"
}

ordinary_json="$(printf '%s\n' "${ORDINARY[@]}" | jq -Rsc 'split("\n") | map(select(length > 0))')"
rare_json="$(printf '%s\n' "${RARE[@]}" | jq -Rsc 'split("\n") | map(select(length > 0))')"
neutral_json="$(printf '%s\n' "${NEUTRAL[@]}" | jq -Rsc 'split("\n") | map(select(length > 0))')"
jq -e --argjson ordinary "$ordinary_json" --argjson rare "$rare_json" \
    --argjson neutral "$neutral_json" '
    (.elements | type == "array" and length == 14 and
        .[0:12] == $ordinary and .[12:13] == $rare and .[13:14] == $neutral)
' "$affinity_file" >/dev/null || die 'elements does not match the canonical ordinary/rare/neutral sets'

jq -e '
    ((.affinities // .matrix) | type == "object") and
    (.elements | type == "array")
' "$affinity_file" >/dev/null || die 'affinity JSON has an invalid top-level shape'

expected_keys_json="$(printf '%s\n' "${ALL[@]}" | jq -Rsc 'split("\n") | map(select(length > 0))')"
jq -e --argjson expected "$expected_keys_json" \
    "$MATRIX_PATH |
    ((keys | sort) == (\$expected | sort)) and
    ([.[] | type] | all(. == \"object\")) and
    ([.[] | ((keys | sort) == (\$expected | sort))] | all)" \
    "$affinity_file" >/dev/null || die 'matrix does not contain exactly the canonical element rows'

matrix_size="$(jq -r "$MATRIX_PATH | length" "$affinity_file")"
[[ "$matrix_size" == "14" ]] || die "matrix must have 14 rows; found $matrix_size"

for element in "${ALL[@]}"; do
    row_size="$(jq -r --arg element "$element" "$MATRIX_PATH[\$element] | length" "$affinity_file")"
    [[ "$row_size" == "14" ]] || die "matrix row $element must have 14 entries; found $row_size"
done

jq -e "$MATRIX_PATH |
    [.[] | to_entries[] | .value] |
    all(type == \"number\" and (. == 25 or . == 50 or . == 75 or . == 100))
" "$affinity_file" >/dev/null || die 'matrix contains a value outside 25/50/75/100'

jq -e "$MATRIX_PATH |
    [to_entries[] as \$row |
        (\$row.value | to_entries[] | select(.key == \$row.key) | .value)] |
    all(. == 100)
" "$affinity_file" >/dev/null || die 'matrix diagonal is not 100 for every element'

jq -e "$MATRIX_PATH |
    [to_entries[] as \$row |
        (\$row.value | to_entries[] | select(.key != \$row.key) | .value)] |
    all(. >= 25)
" "$affinity_file" >/dev/null || die 'matrix contains an opposed value below the 25 floor'

for left in "${ALL[@]}"; do
    for right in "${ALL[@]}"; do
        jq -e --arg left "$left" --arg right "$right" \
            "$MATRIX_PATH | .[\$left][\$right] == .[\$right][\$left]" \
            "$affinity_file" >/dev/null || die "matrix is not symmetric for $left/$right"
    done
done

for element in "${ALL[@]}"; do
    [[ "$element" == arcane ]] && continue
    jq -e --arg element "$element" \
        "$MATRIX_PATH | .arcane[\$element] == 50 and .[\$element].arcane == 50" \
        "$affinity_file" >/dev/null || die "Arcane affinity is not 50 for $element"
done

jq -e "($MATRIX_PATH) as \$matrix |
    ([\$matrix | to_entries[] | .key] +
     [\$matrix | to_entries[] | .value | keys[]] +
     [.elements[]]) |
    all((ascii_downcase | contains(\"frost\")) | not)
" "$affinity_file" >/dev/null || die 'Frost appears in the canonical affinity data'

readonly JAVA_SOURCES="$REPO_ROOT/src/main/java"
require_symbol 'separate permanent natural-element API' 'NATURAL_ELEMENT' "$JAVA_SOURCES"
require_symbol 'mutable channel-affinity API' 'MANA_AFFINITY' "$JAVA_SOURCES"
require_symbol 'mutable attunement operation' '(setAffinity|attune|setAttunement|channel)' "$JAVA_SOURCES"
require_symbol 'schema 3 migration' 'CURRENT_SCHEMA[[:space:]]*=[[:space:]]*3' "$JAVA_SOURCES"
require_symbol 'deterministic natural-element selector' \
    '(deterministic[^[:cntrl:]]*natural.?element|natural.?element[^[:cntrl:]]*deterministic|NaturalElementSelector|selectNaturalElement|assignNaturalElement)' \
    "$JAVA_SOURCES"

readonly -a FROST_ALIAS_SOURCES=(
    "$REPO_ROOT/src/main/java/vectorregnum/core/Element.java"
    "$REPO_ROOT/src/main/java/vectorregnum/neoforge/progression/ManaAffinity.java"
    "$REPO_ROOT/src/main/java/vectorregnum/neoforge/multiplayer/PlayerDataMigration.java"
    "$REPO_ROOT/src/main/java/vectorregnum/neoforge/LibrarySpellService.java"
)

mapfile -t frost_files < <(rg -l -i --glob '*.java' --glob '*.json' 'frost' "$REPO_ROOT/src/main" || true)
for frost_file in "${frost_files[@]}"; do
    case "$frost_file" in
        "${FROST_ALIAS_SOURCES[0]}"|"${FROST_ALIAS_SOURCES[1]}"|"${FROST_ALIAS_SOURCES[2]}"|"${FROST_ALIAS_SOURCES[3]}") ;;
        *)
        die "Frost is present outside approved compatibility-alias locations: ${frost_file#"$REPO_ROOT/"}"
        ;;
    esac
done
(( ${#frost_files[@]} > 0 )) || die 'no Frost compatibility alias remains in the approved migration/parser locations'

mapfile -t frost_tests < <(rg -l -i --glob '*Test.java' 'frost' "$REPO_ROOT/src/test" || true)
(( ${#frost_tests[@]} > 0 )) || die 'no Frost-to-Ice canonicalization test was found'
for frost_test in "${frost_tests[@]}"; do
    rg -qi 'ice' "$frost_test" || die "Frost test does not prove Ice canonicalization: ${frost_test#"$REPO_ROOT/"}"
done

mapfile -t focused_sources < <(
    rg --files "$REPO_ROOT/src/test/java" 2>/dev/null |
        rg '/([^/]*(Element|Affinity|Natural|Migration|ManaDrawRules)[^/]*)Test\.java$' |
        sort
)
(( ${#focused_sources[@]} > 0 )) || die 'no focused priority-21 JUnit test classes were found'

test_args=(--no-daemon test)
for test_source in "${focused_sources[@]}"; do
    test_class="${test_source#"$REPO_ROOT/src/test/java/"}"
    test_class="${test_class%.java}"
    test_class="${test_class//\//.}"
    test_args+=(--tests "$test_class")
done
./gradlew "${test_args[@]}"

find src -type f -name '*.json' -print0 | xargs -0 -r -n1 jq empty
find scripts -type f -name '*.sh' -print0 | xargs -0 -r -n1 bash -n
git diff --check

printf 'PRIORITY21_VERIFY_OK matrix=%s rows=%s focused_tests=%s schema=3\n' \
    "${affinity_file#"$REPO_ROOT/"}" "$matrix_size" "${#focused_sources[@]}"
