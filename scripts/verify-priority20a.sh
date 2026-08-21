#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd -P)"
readonly SCRIPT_DIR REPO_ROOT
readonly EXPECTED_VEIL_VERSION="4.4.1"
readonly VEIL_ADAPTER="src/main/java/vectorregnum/neoforge/presentation/VeilPresentationBackend.java"

die() {
    printf 'priority 20a verification failed: %s\n' "$*" >&2
    exit 1
}

resolve_java_21() {
    local java_path java_version java_major vr_jdk
    if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
        java_path="$JAVA_HOME/bin/java"
    elif command -v nix >/dev/null 2>&1; then
        vr_jdk="$(nix eval --raw nixpkgs#jdk21.outPath)"
        java_path="$vr_jdk/bin/java"
        export JAVA_HOME="$vr_jdk"
        export PATH="$vr_jdk/bin:$PATH"
    else
        java_path="$(command -v java)" || die 'Java 21 is unavailable'
    fi
    java_version="$($java_path -version 2>&1 | sed -n '1p')"
    java_major="$(sed -E 's/.*version "([0-9]+).*/\1/' <<< "$java_version")"
    [[ "$java_major" == "21" ]] || die "Java 21 is required; found $java_version"
}

cd -- "$REPO_ROOT"
resolve_java_21

grep -Fxq "veil_version=$EXPECTED_VEIL_VERSION" gradle.properties ||
    die "gradle.properties does not pin Veil $EXPECTED_VEIL_VERSION"
grep -Fq 'type = "optional"' src/main/resources/META-INF/neoforge.mods.toml ||
    die 'Veil is not optional in neoforge.mods.toml'
grep -Fq 'side = "CLIENT"' src/main/resources/META-INF/neoforge.mods.toml ||
    die 'Veil is not client-only in neoforge.mods.toml'

mapfile -t veil_imports < <(rg -l '^import foundry\.veil\.' src/main/java || true)
(( ${#veil_imports[@]} == 1 )) ||
    die "expected one Veil-linking class, found ${#veil_imports[@]}"
[[ "${veil_imports[0]}" == "$VEIL_ADAPTER" ]] ||
    die "Veil imports escaped the isolated adapter: ${veil_imports[*]}"
if rg -n 'foundry\.veil|VeilPresentationBackend' \
        src/main/java/vectorregnum/core \
        src/main/java/vectorregnum/neoforge/VectorRegnumMod.java \
        src/main/java/vectorregnum/neoforge/NeoForgeNetworking.java >/dev/null; then
    die 'Veil symbols reached loader-neutral or common server entrypoints'
fi

task_tmp="$(mktemp -d)"
trap 'rm -rf -- "$task_tmp"' EXIT
./gradlew --no-daemon -q dependencies --configuration runtimeClasspath \
    >"$task_tmp/runtime-absent.txt"
if rg -q 'foundry\.veil:veil-neoforge-1\.21\.1' "$task_tmp/runtime-absent.txt"; then
    die 'Veil leaked onto the default runtime classpath'
fi
./gradlew --no-daemon -q -Pvector_regnum_enable_veil=true \
    dependencies --configuration runtimeClasspath >"$task_tmp/runtime-present.txt"
rg -q "foundry\.veil:veil-neoforge-1\.21\.1:$EXPECTED_VEIL_VERSION" \
    "$task_tmp/runtime-present.txt" || die 'the Veil-enabled runtime did not resolve the pinned artifact'

find src/main/resources/assets/vector_regnum/quasar -type f -name '*.json' -print0 |
    xargs -0 -r -n1 jq empty
./gradlew --no-daemon test \
    --tests 'vectorregnum.core.presentation.PresentationModuleMapperTest' \
    --tests 'vectorregnum.core.presentation.PresentationEnhancementPolicyTest' \
    --tests 'vectorregnum.neoforge.presentation.OptionalPresentationBackendTest'

printf 'PRIORITY20A_VERIFY_OK veil=%s default_runtime=absent enabled_runtime=present adapter=%s\n' \
    "$EXPECTED_VEIL_VERSION" "$VEIL_ADAPTER"
