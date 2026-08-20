#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
(( $# == 0 )) || {
    printf 'Usage: scripts/verify-port.sh\n' >&2
    exit 2
}

cd -- "$repo_dir"

task_jdk="$(nix eval --raw nixpkgs#jdk21.outPath)"
JAVA_HOME="$task_jdk" PATH="$task_jdk/bin:$PATH" \
    ./gradlew --no-daemon clean test build

find src -type f -name '*.json' -print0 | xargs -0 -r -n1 jq empty
find scripts -type f -name '*.sh' -print0 | xargs -0 -r -n1 bash -n
git diff --check

printf 'Full NeoForge verification passed.\n'
