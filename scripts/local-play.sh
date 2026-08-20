#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd -P)"
readonly SCRIPT_DIR REPO_ROOT
readonly SERVER_UNIT="vector-regnum-local-server.service"
readonly SERVER_PORT="25575"

server_started=false

pause_on_error() {
    if [[ -t 0 ]]; then
        printf '\n'
        read -r -p 'Press Enter to close this window...' _ || true
    fi
}

die() {
    printf 'Vector-Regnum launcher error: %s\n' "$*" >&2
    pause_on_error
    exit 1
}

unit_load_state() {
    systemctl --user show "$SERVER_UNIT" --property=LoadState --value 2>/dev/null ||
        printf 'not-found\n'
}

# Invoked from the EXIT-trap cleanup path.
# shellcheck disable=SC2329
unit_is_owned() {
    local exec_start
    exec_start="$(systemctl --user show "$SERVER_UNIT" --property=ExecStart --value 2>/dev/null)" ||
        return 1
    [[ "$exec_start" == *"$REPO_ROOT/gradlew"* && "$exec_start" == *"runServer"* ]]
}

# Registered by name as the EXIT trap below.
# shellcheck disable=SC2329
cleanup() {
    local original_status=$?
    set +e
    if [[ "$server_started" == true ]]; then
        printf '\nStopping the private Vector-Regnum server...\n'
        if unit_is_owned; then
            systemctl --user stop "$SERVER_UNIT"
        else
            printf 'Refusing to stop an unexpected unit named %s\n' "$SERVER_UNIT" >&2
        fi
    fi
    return "$original_status"
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exit 129' HUP

[[ -x "$REPO_ROOT/gradlew" ]] || die "Gradle wrapper is missing: $REPO_ROOT/gradlew"
[[ -f "$REPO_ROOT/dev/hermes/eula.txt" ]] || die 'development EULA file is missing'
[[ -f "$REPO_ROOT/dev/hermes/server.properties" ]] || die 'development server properties are missing'
command -v nix >/dev/null || die 'nix is unavailable'
command -v steam-run >/dev/null || die 'steam-run is unavailable'
command -v systemd-run >/dev/null || die 'systemd-run is unavailable'
command -v ss >/dev/null || die 'ss is unavailable'

for mods_dir in "$REPO_ROOT/run/client/mods" "$REPO_ROOT/run/server/mods"; do
    if [[ -d "$mods_dir" ]] && find "$mods_dir" -maxdepth 1 -type f -name '*.jar' -print -quit | grep -q .; then
        printf 'Unexpected mod JARs were found in %s:\n' "$mods_dir" >&2
        find "$mods_dir" -maxdepth 1 -type f -name '*.jar' -printf '  %f\n' >&2
        die 'remove or relocate those JARs before using the isolated launcher'
    fi
done

[[ "$(unit_load_state)" == "not-found" ]] ||
    die "$SERVER_UNIT already exists; another Vector-Regnum launch may be active"
if ss -H -ltn "sport = :$SERVER_PORT" | grep -q .; then
    die "port $SERVER_PORT is already in use"
fi

mkdir -p -- "$REPO_ROOT/run/server"
install -m 0644 -- "$REPO_ROOT/dev/hermes/eula.txt" "$REPO_ROOT/run/server/eula.txt"
install -m 0644 -- "$REPO_ROOT/dev/hermes/server.properties" "$REPO_ROOT/run/server/server.properties"

vector_jdk="$(nix eval --raw nixpkgs#jdk21.outPath)" || die 'could not resolve the declared JDK 21'
[[ -x "$vector_jdk/bin/java" ]] || die "JDK 21 is incomplete: $vector_jdk"
vector_path="$vector_jdk/bin:/run/current-system/sw/bin:/etc/profiles/per-user/iank/bin"

printf 'Starting Vector-Regnum\n'
printf '  World: %s/run/server/vector-regnum-test\n' "$REPO_ROOT"
printf '  Server: 127.0.0.1:%s\n' "$SERVER_PORT"
printf '  Loader: NeoForge 21.1.1 development runtime\n\n'

systemd-run \
    --user \
    --unit="$SERVER_UNIT" \
    --collect \
    --service-type=exec \
    --working-directory="$REPO_ROOT" \
    --description='Vector-Regnum one-click local server' \
    --setenv="JAVA_HOME=$vector_jdk" \
    --setenv="PATH=$vector_path" \
    "$REPO_ROOT/gradlew" --no-daemon runServer
server_started=true

printf 'Waiting for the private world to become ready'
for _ in {1..300}; do
    if ss -H -ltn "sport = :$SERVER_PORT" | grep -q .; then
        printf ' ready.\nLaunching Minecraft...\n\n'
        break
    fi
    if ! systemctl --user is-active --quiet "$SERVER_UNIT"; then
        printf '\n' >&2
        journalctl --user --unit="$SERVER_UNIT" --lines=100 --no-pager >&2 || true
        die 'the development server exited before opening its port'
    fi
    printf '.'
    sleep 1
done

ss -H -ltn "sport = :$SERVER_PORT" | grep -q . ||
    die "the server did not open port $SERVER_PORT within five minutes"

set +e
JAVA_HOME="$vector_jdk" PATH="$vector_path" \
    steam-run "$REPO_ROOT/gradlew" --no-daemon runClient
client_status=$?
set -e

if (( client_status != 0 )); then
    printf '\nMinecraft exited with status %s.\n' "$client_status" >&2
    if [[ -f "$REPO_ROOT/run/client/logs/latest.log" ]]; then
        printf 'Recent client log:\n' >&2
        tail -80 "$REPO_ROOT/run/client/logs/latest.log" >&2
    fi
    pause_on_error
fi

exit "$client_status"
