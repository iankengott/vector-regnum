#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
(( $# == 0 )) || {
    printf 'Usage: scripts/verify-port.sh\n' >&2
    exit 2
}

cd -- "$repo_dir"

quickplay_count="$(grep -Ec '^[[:space:]]*programArguments\.addAll\("--quickPlayMultiplayer",' build.gradle || true)"
expected_quickplay_count="$(grep -Ec '^[[:space:]]*programArguments\.addAll\("--quickPlayMultiplayer",[[:space:]]*"127\.0\.0\.1:25575"\)' build.gradle || true)"
[[ "$quickplay_count" -eq 1 && "$expected_quickplay_count" -eq 1 ]] || {
    printf 'NeoForge quick-play must use explicit IPv4 loopback 127.0.0.1:25575.\n' >&2
    exit 1
}

scripts/local-play.sh --check-config

config_fixture="$(mktemp -d)"
trap 'rm -rf -- "$config_fixture"' EXIT
printf 'eula=true\n' >"$config_fixture/eula.txt"
printf 'server-port=25565\nserver-ip=0.0.0.0\n' >"$config_fixture/server.properties"
if scripts/check-dev-server-config.sh "$config_fixture" 25575 >/dev/null 2>&1; then
    printf 'Development server configuration guard accepted an unsafe fixture.\n' >&2
    exit 1
fi

printf 'LISTEN 0 4096 127.0.0.1:25575 0.0.0.0:*\n' |
    scripts/check-dev-listener.sh 25575 >/dev/null
if printf 'LISTEN 0 4096 0.0.0.0:25575 0.0.0.0:*\n' |
        scripts/check-dev-listener.sh 25575 >/dev/null 2>&1; then
    printf 'Development listener guard accepted a wildcard fixture.\n' >&2
    exit 1
fi
if printf 'LISTEN 0 4096 [::1]:25575 [::]:*\n' |
        scripts/check-dev-listener.sh 25575 >/dev/null 2>&1; then
    printf 'Development listener guard accepted an IPv6 fixture.\n' >&2
    exit 1
fi

task_jdk="$(nix eval --raw nixpkgs#jdk21.outPath)"
JAVA_HOME="$task_jdk" PATH="$task_jdk/bin:$PATH" \
    ./gradlew --no-daemon clean test build

find src -type f -name '*.json' -print0 | xargs -0 -r -n1 jq empty
find scripts -type f -name '*.sh' -print0 | xargs -0 -r -n1 bash -n
git diff --check

printf 'NeoForge clean build and static verification passed; runGameTestServer separately.\n'
