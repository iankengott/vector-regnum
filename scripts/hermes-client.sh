#!/usr/bin/env bash
set -euo pipefail

# Priority 20 staging guard. The Fabric entrypoints are excluded from the build
# and no NeoForge @Mod entrypoint exists yet, so runClient/runServer would start
# Minecraft with no Vector-Regnum loaded and look like a working launch.
# Remove this guard when a complete NeoForge slice provides an entrypoint.
printf 'This launcher is disabled during the NeoForge port (roadmap priority 20).\n' >&2
printf 'There is no NeoForge entrypoint yet, so a launch would run vanilla Minecraft\n' >&2
printf 'and appear to succeed. For a playable Fabric alpha use the frozen legacy\n' >&2
printf 'checkout at ../vector-regnum-fabric-legacy (commit c7371ca).\n' >&2
exit 1


SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=lib/hermes-common.sh
source "$SCRIPT_DIR/lib/hermes-common.sh"

usage() {
    cat <<'USAGE'
Usage: scripts/hermes-client.sh ACTION

Control only Vector-Regnum's isolated Loom development server and client on
Hermes. The server is staged from dev/hermes, started on port 25575, and proven
ready before the quick-play client starts.

Actions:
  start       Start the isolated server, wait for 25575, then launch the client.
  restart     Stop both owned development units and relaunch the stack.
  stop        Stop only the two owned Vector-Regnum development units.
  status      Show status for only the two development units.
  logs        Show their most recent 200 journal lines.
  logs-follow Follow their journals until interrupted.
  help        Show this help.
USAGE
}

action="${1:-help}"
(( $# <= 1 )) || { usage >&2; vr_die "too many arguments"; }
case "$action" in
    start|restart|stop|status|logs|logs-follow) ;;
    help|-h|--help) usage; exit 0 ;;
    *) usage >&2; vr_die "unknown action: $action" ;;
esac

vr_check_remote_identity
vr_require_remote_marker

vr_ssh bash -s -- \
    "$VR_REMOTE_DIR" "$VR_SERVER_UNIT" "$VR_CLIENT_UNIT" "$VR_DEV_SERVER_PORT" "$action" <<'REMOTE'
set -euo pipefail

remote_dir="$1"
server_unit="$2"
client_unit="$3"
server_port="$4"
action="$5"

[[ "$remote_dir" =~ ^/home/ian-kengott/projects/[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || exit 1
[[ "$server_unit" == "vector-regnum-dev-server.service" ]] || exit 1
[[ "$client_unit" == "vector-regnum-dev-client.service" ]] || exit 1
[[ "$server_port" == "25575" ]] || exit 1

unit_load_state() {
    local unit="$1"
    systemctl --user show "$unit" --property=LoadState --value 2>/dev/null || printf 'not-found\n'
}

assert_owned_unit_if_loaded() {
    local unit="$1"
    local expected_task="$2"
    local load_state exec_start
    load_state="$(unit_load_state "$unit")"
    [[ "$load_state" == "not-found" ]] && return 0

    exec_start="$(systemctl --user show "$unit" --property=ExecStart --value)"
    [[ "$exec_start" == *"$remote_dir/gradlew"* && "$exec_start" == *"$expected_task"* ]] || {
        printf 'refusing to control unexpected unit definition: %s\n' "$unit" >&2
        exit 1
    }
}

stop_owned_unit() {
    local unit="$1"
    local expected_task="$2"
    assert_owned_unit_if_loaded "$unit" "$expected_task"
    if [[ "$(unit_load_state "$unit")" == "not-found" ]]; then
        printf '%s is not running\n' "$unit"
        return 0
    fi

    systemctl --user stop "$unit"
    for _ in {1..50}; do
        [[ "$(unit_load_state "$unit")" == "not-found" ]] && return 0
        sleep 0.2
    done
    printf 'timed out waiting for %s to unload\n' "$unit" >&2
    exit 1
}

require_jdk_and_wrapper() {
    [[ -x "$remote_dir/gradlew" ]] || {
        printf 'Gradle wrapper is missing or not executable: %s/gradlew\n' "$remote_dir" >&2
        exit 1
    }

    local java_command java_path java_version java_major
    java_command="$(command -v java)" || {
        printf 'java is not available in the Hermes SSH environment\n' >&2
        exit 1
    }
    [[ "$java_command" == /* ]] || {
        printf 'refusing non-absolute Java command: %s\n' "$java_command" >&2
        exit 1
    }
    java_path="$(readlink -f -- "$java_command")" || {
        printf 'could not resolve Java executable: %s\n' "$java_command" >&2
        exit 1
    }
    [[ "$java_path" =~ ^/usr/lib/jvm/[A-Za-z0-9._+-]+/bin/java$ && -x "$java_path" ]] || {
        printf 'refusing Java executable outside /usr/lib/jvm: %s\n' "$java_path" >&2
        exit 1
    }

    java_version="$("$java_path" -version 2>&1 | sed -n '1p')"
    java_major="$(sed -E 's/.*version "([0-9]+).*/\1/' <<< "$java_version")"
    [[ "$java_major" == "21" ]] || {
        printf 'Hermes must use JDK 21; found: %s\n' "$java_version" >&2
        exit 1
    }

    verified_java_home="${java_path%/bin/java}"
    [[ "$verified_java_home" =~ ^/usr/lib/jvm/[A-Za-z0-9._+-]+$ ]] || {
        printf 'resolved JAVA_HOME is unsafe: %s\n' "$verified_java_home" >&2
        exit 1
    }
    verified_unit_path="$verified_java_home/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
    printf 'Transient units will use %s (%s)\n' "$java_path" "$java_version"
}

port_is_listening() {
    ss -H -ltn "sport = :$server_port" | grep -q .
}

stage_server_config() {
    local source_dir="$remote_dir/dev/hermes"
    local run_dir="$remote_dir/run/server"
    [[ -f "$source_dir/eula.txt" && ! -L "$source_dir/eula.txt" &&
       -f "$source_dir/server.properties" && ! -L "$source_dir/server.properties" ]] || {
        printf 'checked-in Hermes server configuration is missing\n' >&2
        exit 1
    }
    grep -qx 'eula=true' "$source_dir/eula.txt" || {
        printf 'Hermes development EULA file is invalid\n' >&2
        exit 1
    }
    grep -qx "server-port=$server_port" "$source_dir/server.properties" || {
        printf 'Hermes development server must use port %s\n' "$server_port" >&2
        exit 1
    }
    if grep -Eq '^server-port=25565$' "$source_dir/server.properties"; then
        printf 'refusing production Minecraft port 25565\n' >&2
        exit 1
    fi
    [[ ! -L "$remote_dir/run" && ! -L "$run_dir" ]] || {
        printf 'refusing symlinked development run directory\n' >&2
        exit 1
    }
    mkdir -p -- "$run_dir"
    install -m 0644 -- "$source_dir/eula.txt" "$run_dir/eula.txt"
    install -m 0644 -- "$source_dir/server.properties" "$run_dir/server.properties"
}

start_server() {
    assert_owned_unit_if_loaded "$server_unit" runServer
    if [[ "$(unit_load_state "$server_unit")" != "not-found" ]]; then
        if systemctl --user is-active --quiet "$server_unit"; then
            printf '%s is already active; reusing it\n' "$server_unit"
            return 0
        fi
        printf '%s exists but is not active; use restart to replace it\n' "$server_unit" >&2
        exit 1
    fi
    if port_is_listening; then
        printf 'refusing to start: port %s is already owned by another process\n' "$server_port" >&2
        exit 1
    fi

    stage_server_config
    systemd-run \
        --user \
        --unit="$server_unit" \
        --collect \
        --service-type=exec \
        --working-directory="$remote_dir" \
        --description='Vector-Regnum isolated Loom development server' \
        --setenv="JAVA_HOME=$verified_java_home" \
        --setenv="PATH=$verified_unit_path" \
        --setenv='VECTOR_REGNUM_VISUAL_CHECK=1' \
        "$remote_dir/gradlew" --no-daemon runServer
}

wait_for_server() {
    for _ in {1..180}; do
        if port_is_listening; then
            printf 'development server is listening on %s\n' "$server_port"
            return 0
        fi
        if ! systemctl --user is-active --quiet "$server_unit"; then
            printf 'development server exited before opening port %s\n' "$server_port" >&2
            journalctl --user --unit="$server_unit" --lines=80 --no-pager >&2 || true
            exit 1
        fi
        sleep 1
    done
    printf 'timed out waiting for development server port %s\n' "$server_port" >&2
    exit 1
}

start_client() {
    assert_owned_unit_if_loaded "$client_unit" runClient
    if [[ "$(unit_load_state "$client_unit")" != "not-found" ]]; then
        if systemctl --user is-active --quiet "$client_unit"; then
            printf '%s is already active; reusing it\n' "$client_unit"
            return 0
        fi
        printf '%s exists but is not active; use restart to replace it\n' "$client_unit" >&2
        exit 1
    fi

    manager_environment="$(systemctl --user show-environment)"
    grep -q '^DISPLAY=:0$' <<< "$manager_environment" || {
        printf 'Hermes user manager does not target DISPLAY=:0\n' >&2
        exit 1
    }
    grep -q '^WAYLAND_DISPLAY=wayland-0$' <<< "$manager_environment" || {
        printf 'Hermes user manager has no live Wayland display\n' >&2
        exit 1
    }
    grep -q '^DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/1000/bus$' <<< "$manager_environment" || {
        printf 'Hermes user manager has no expected GNOME session bus\n' >&2
        exit 1
    }

    systemd-run \
        --user \
        --unit="$client_unit" \
        --collect \
        --service-type=exec \
        --working-directory="$remote_dir" \
        --description='Vector-Regnum Loom development client' \
        --setenv="JAVA_HOME=$verified_java_home" \
        --setenv="PATH=$verified_unit_path" \
        "$remote_dir/gradlew" --no-daemon runClient
    printf 'launched %s; inspect with scripts/hermes-client.sh status or logs\n' "$client_unit"
}

start_stack() {
    require_jdk_and_wrapper
    start_server
    wait_for_server
    start_client
}

stop_stack() {
    stop_owned_unit "$client_unit" runClient
    stop_owned_unit "$server_unit" runServer
}

show_status() {
    local unit task
    for unit_task in "$server_unit:runServer" "$client_unit:runClient"; do
        unit="${unit_task%%:*}"
        task="${unit_task##*:}"
        assert_owned_unit_if_loaded "$unit" "$task"
        if [[ "$(unit_load_state "$unit")" == "not-found" ]]; then
            printf '%s is not running\n' "$unit"
        else
            systemctl --user status "$unit" --no-pager || true
        fi
    done
}

case "$action" in
    start)
        start_stack
        ;;
    restart)
        stop_stack
        start_stack
        ;;
    stop)
        stop_stack
        ;;
    status)
        show_status
        ;;
    logs)
        assert_owned_unit_if_loaded "$server_unit" runServer
        assert_owned_unit_if_loaded "$client_unit" runClient
        journalctl --user --unit="$server_unit" --unit="$client_unit" --lines=200 --no-pager
        ;;
    logs-follow)
        assert_owned_unit_if_loaded "$server_unit" runServer
        assert_owned_unit_if_loaded "$client_unit" runClient
        journalctl --user --unit="$server_unit" --unit="$client_unit" --lines=100 --follow
        ;;
esac
REMOTE
