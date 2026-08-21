#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd -P)"
readonly SCRIPT_DIR REPO_ROOT
readonly PACK_ROOT="/home/iank/.local/share/PrismLauncher/instances/1.21.1"
readonly PACK_GAME="$PACK_ROOT/minecraft"
readonly MANIFEST="$PACK_ROOT/manifest/mods.tsv"
readonly CLIENT_DIR="$REPO_ROOT/run/priority20a-compat-client"
readonly SERVER_DIR="$REPO_ROOT/run/priority20a-compat-server"
readonly SERVER_UNIT="vector-regnum-priority20a-compat-server.service"
readonly SERVER_PORT="25575"
readonly SHADER="Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip"
readonly -a CLIENT_MODS=(
    "veil-neoforge-1.21.1-4.4.1.jar"
    "create-1.21.1-6.0.10.jar"
    "sodium-neoforge-0.8.13-beta.2+mc1.21.1.jar"
    "iris-neoforge-1.8.14-beta.1+mc1.21.1.jar"
)
readonly -a SERVER_MODS=("create-1.21.1-6.0.10.jar")

die() {
    printf 'priority 20a compatibility matrix failed: %s\n' "$*" >&2
    exit 1
}

usage() {
    printf 'Usage: scripts/priority20a-compat-matrix.sh stage|run|check\n'
}

verify_artifact() {
    local name="$1" source expected actual
    source="$PACK_GAME/mods/$name"
    [[ -f "$source" && ! -L "$source" ]] || die "missing target-pack artifact: $name"
    expected="$(awk -F '\t' -v name="$name" '$1 == name { print $3 }' "$MANIFEST")"
    [[ "$expected" =~ ^[0-9a-f]{64}$ ]] || die "manifest has no exact SHA-256 for $name"
    actual="$(sha256sum "$source" | cut -d ' ' -f1)"
    [[ "$actual" == "$expected" ]] || die "hash mismatch for $name"
}

link_exact() {
    local source="$1" target="$2"
    if [[ -e "$target" && ! -L "$target" ]]; then
        die "refusing to replace non-symlink compatibility artifact: $target"
    fi
    ln -sfn -- "$source" "$target"
}

stage_matrix() {
    [[ "$(hostname)" == "nixos" && "$(whoami)" == "iank" ]] ||
        die 'run the target-pack matrix only as iank on nixos'
    [[ -f "$MANIFEST" ]] || die "missing target pack manifest: $MANIFEST"
    mkdir -p -- "$CLIENT_DIR/mods" "$CLIENT_DIR/config" "$CLIENT_DIR/shaderpacks" \
        "$SERVER_DIR/mods"
    for name in "${CLIENT_MODS[@]}"; do
        verify_artifact "$name"
        link_exact "$PACK_GAME/mods/$name" "$CLIENT_DIR/mods/$name"
    done
    for name in "${SERVER_MODS[@]}"; do
        verify_artifact "$name"
        link_exact "$PACK_GAME/mods/$name" "$SERVER_DIR/mods/$name"
    done
    expected_shader="$(awk -F '\t' -v name="$SHADER" '$1 == name { print $3 }' \
        "$PACK_ROOT/manifest/shaderpacks.tsv")"
    [[ "$expected_shader" =~ ^[0-9a-f]{64}$ ]] || die 'shader manifest hash is missing'
    actual_shader="$(sha256sum "$PACK_GAME/shaderpacks/$SHADER" | cut -d ' ' -f1)"
    [[ "$actual_shader" == "$expected_shader" ]] || die 'shader pack hash mismatch'
    link_exact "$PACK_GAME/shaderpacks/$SHADER" "$CLIENT_DIR/shaderpacks/$SHADER"
    install -m 0644 -- "$PACK_GAME/config/iris.properties" "$CLIENT_DIR/config/iris.properties"
    install -m 0644 -- "$PACK_GAME/options.txt" "$CLIENT_DIR/options.txt"
    install -m 0644 -- "$REPO_ROOT/dev/hermes/eula.txt" "$SERVER_DIR/eula.txt"
    install -m 0644 -- "$REPO_ROOT/dev/hermes/server.properties" "$SERVER_DIR/server.properties"
    "$SCRIPT_DIR/check-dev-server-config.sh" "$SERVER_DIR" "$SERVER_PORT"
    printf 'PRIORITY20A_MATRIX_STAGED client=%s server=%s live_pack=unchanged\n' \
        "$CLIENT_DIR" "$SERVER_DIR"
}

unit_load_state() {
    systemctl --user show "$SERVER_UNIT" --property=LoadState --value 2>/dev/null ||
        printf 'not-found\n'
}

stop_server() {
    if [[ "$(unit_load_state)" != "not-found" ]]; then
        exec_start="$(systemctl --user show "$SERVER_UNIT" --property=ExecStart --value)"
        if [[ "$exec_start" == *"$REPO_ROOT/gradlew"* && "$exec_start" == *"runServer"* \
                && "$exec_start" == *"vector_regnum_server_game_dir=run/priority20a-compat-server"* ]]; then
            systemctl --user stop "$SERVER_UNIT"
        else
            die "refusing to stop unexpected unit $SERVER_UNIT"
        fi
    fi
}

run_matrix() {
    stage_matrix
    command -v nix >/dev/null || die 'nix is unavailable'
    command -v steam-run >/dev/null || die 'steam-run is unavailable'
    [[ "$(unit_load_state)" == "not-found" ]] || die "$SERVER_UNIT already exists"
    ! ss -H -ltn "sport = :$SERVER_PORT" | grep -q . || die "port $SERVER_PORT is in use"
    matrix_jdk="$(nix eval --raw nixpkgs#jdk21.outPath)"
    matrix_path="$matrix_jdk/bin:/run/current-system/sw/bin:/etc/profiles/per-user/iank/bin"
    trap stop_server EXIT INT TERM HUP
    systemd-run --user --unit="$SERVER_UNIT" --collect --service-type=exec \
        --working-directory="$REPO_ROOT" \
        --setenv="JAVA_HOME=$matrix_jdk" --setenv="PATH=$matrix_path" \
        --setenv='VECTOR_REGNUM_VISUAL_CHECK=1' \
        "$REPO_ROOT/gradlew" --no-daemon \
        -Pvector_regnum_server_game_dir=run/priority20a-compat-server runServer
    for _ in {1..180}; do
        if ss -H -ltn "sport = :$SERVER_PORT" | "$SCRIPT_DIR/check-dev-listener.sh" \
                "$SERVER_PORT" >/dev/null 2>&1; then
            break
        fi
        systemctl --user is-active --quiet "$SERVER_UNIT" ||
            die 'compatibility server exited before becoming ready'
        sleep 1
    done
    ss -H -ltn "sport = :$SERVER_PORT" | "$SCRIPT_DIR/check-dev-listener.sh" \
        "$SERVER_PORT" >/dev/null || die 'compatibility server did not become ready'

    set +e
    JAVA_HOME="$matrix_jdk" PATH="$matrix_path" timeout --signal=INT 180 \
        steam-run "$REPO_ROOT/gradlew" --no-daemon \
        -Pvector_regnum_client_game_dir=run/priority20a-compat-client runClient
    client_status=$?
    set -e
    [[ "$client_status" == 0 || "$client_status" == 124 || "$client_status" == 130 ]] ||
        die "compatibility client exited with status $client_status"
    stop_server
    check_matrix
}

check_matrix() {
    local client_log="$CLIENT_DIR/logs/latest.log" server_log="$SERVER_DIR/logs/latest.log"
    local high_gl_count unexpected_high_gl
    [[ -f "$client_log" && -f "$server_log" ]] || die 'compatibility logs are missing'
    grep -Fq 'Vector-Regnum presentation backend: veil-4.4.1 (Veil 4.4.1)' "$client_log" ||
        die 'client did not activate the pinned Veil backend'
    grep -Fq 'Veil renderer compatibility mode: Iris detected; deferred lights and bloom disabled' \
        "$client_log" || die 'the Iris-safe Veil compatibility mode did not activate'
    grep -Fq 'Loaded 4 quasar particles' "$client_log" ||
        die 'Veil did not load the four bounded Quasar emitters'
    grep -Fq 'Connecting to 127.0.0.1, 25575' "$client_log" ||
        die 'compatibility client did not use the isolated endpoint'
    grep -Fq 'Create 6.0.10' "$client_log" ||
        die 'Create 6.0.10 was not present in the client log'
    grep -Fq 'Sodium' "$client_log" || die 'Sodium was not present in the client log'
    grep -Fq 'Iris' "$client_log" || die 'Iris was not present in the client log'
    grep -Fq 'Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip' "$client_log" ||
        die 'the approved Bliss shader was not selected'
    grep -Fq 'create_renderer_probe=true' "$server_log" ||
        die 'the isolated showcase did not stage the Create renderer probe'
    if rg -i 'mod loading has failed|failed to load|exception caught from mod|mixin apply failed|quasar registry loading errors' \
            "$client_log" "$server_log" >/dev/null; then
        die 'compatibility logs contain a loader or mixin failure'
    fi
    if rg -i 'Veil presentation backend failed|Veil Quasar emitter did not load' \
            "$client_log" >/dev/null; then
        die 'the optional renderer failed closed during the compatibility run'
    fi
    high_gl_count="$(rg -c 'OpenGL debug message:.*severity=HIGH' "$client_log" || true)"
    unexpected_high_gl="$(rg 'OpenGL debug message:.*severity=HIGH' "$client_log" \
        | rg -v 'GL_INVALID_OPERATION in glBindTextureUnit\(non-gen name\)' || true)"
    [[ -z "$unexpected_high_gl" ]] || die 'compatibility logs contain an unexpected high-severity OpenGL diagnostic'
    (( high_gl_count <= 1 )) || die 'compatibility logs repeated the allowlisted Veil/Iris texture diagnostic'
    printf 'PRIORITY20A_MATRIX_OK veil=4.4.1 create=6.0.10 sodium=0.8.13-beta.2 iris=1.8.14-beta.1 shader=Bliss-2.1.2\n'
    if (( high_gl_count == 1 )); then
        printf 'PRIORITY20A_MATRIX_NOTE one non-fatal Veil/Iris glBindTextureUnit diagnostic was allowlisted\n'
    fi
}

case "${1:-}" in
    stage) stage_matrix ;;
    run) run_matrix ;;
    check) check_matrix ;;
    *) usage; exit 2 ;;
esac
