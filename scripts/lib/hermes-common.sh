#!/usr/bin/env bash

# Shared, deliberately narrow configuration for Vector-Regnum's Hermes workflow.
# This file is sourced by the user-facing scripts in ../.

if [[ -n "${VECTOR_REGNUM_HERMES_COMMON_LOADED:-}" ]]; then
    return 0
fi
readonly VECTOR_REGNUM_HERMES_COMMON_LOADED=1

readonly VR_DEFAULT_HERMES_HOST="ian-kengott@100.88.229.63"
readonly VR_DEFAULT_HERMES_HOSTNAME="ian-kengott-GF63-Thin-11SC"
readonly VR_DEFAULT_REMOTE_DIR="/home/ian-kengott/projects/vector-regnum"
readonly VR_REMOTE_MARKER_NAME=".vector-regnum-hermes-worktree"
readonly VR_REMOTE_MARKER_CONTENT="vector-regnum-hermes-worktree-v1"
# These are consumed by selected entry points after this shared file is sourced.
# shellcheck disable=SC2034
readonly VR_SERVER_UNIT="vector-regnum-dev-server.service"
# shellcheck disable=SC2034
readonly VR_CLIENT_UNIT="vector-regnum-dev-client.service"
# shellcheck disable=SC2034
readonly VR_DEV_SERVER_PORT="25575"

VR_HERMES_HOST="${VR_HERMES_HOST:-$VR_DEFAULT_HERMES_HOST}"
VR_HERMES_EXPECTED_HOSTNAME="${VR_HERMES_EXPECTED_HOSTNAME:-$VR_DEFAULT_HERMES_HOSTNAME}"
VR_REMOTE_DIR="${VR_REMOTE_DIR:-$VR_DEFAULT_REMOTE_DIR}"
VR_SSH_TIMEOUT="${VR_SSH_TIMEOUT:-10}"

readonly VR_HERMES_HOST VR_HERMES_EXPECTED_HOSTNAME VR_REMOTE_DIR VR_SSH_TIMEOUT

vr_die() {
    printf 'error: %s\n' "$*" >&2
    exit 1
}

vr_note() {
    printf '%s\n' "$*"
}

vr_validate_config() {
    [[ "$VR_HERMES_HOST" =~ ^[a-z_][a-z0-9_-]*@([0-9]{1,3}\.){3}[0-9]{1,3}$ ]] ||
        vr_die "VR_HERMES_HOST must be a user@IPv4 destination"

    local remote_user="${VR_HERMES_HOST%%@*}"
    [[ "$remote_user" == "ian-kengott" ]] ||
        vr_die "Hermes SSH user must be ian-kengott"

    [[ "$VR_HERMES_EXPECTED_HOSTNAME" =~ ^[A-Za-z0-9][A-Za-z0-9.-]*$ ]] ||
        vr_die "VR_HERMES_EXPECTED_HOSTNAME contains unsafe characters"

    [[ "$VR_REMOTE_DIR" =~ ^/home/ian-kengott/projects/[A-Za-z0-9][A-Za-z0-9._-]*$ ]] ||
        vr_die "VR_REMOTE_DIR must be one direct child of /home/ian-kengott/projects"

    [[ "$VR_SSH_TIMEOUT" =~ ^[0-9]+$ ]] ||
        vr_die "VR_SSH_TIMEOUT must be an integer from 1 through 60"
    (( VR_SSH_TIMEOUT >= 1 && VR_SSH_TIMEOUT <= 60 )) ||
        vr_die "VR_SSH_TIMEOUT must be an integer from 1 through 60"
}

vr_repo_root() {
    local common_dir
    common_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
    cd -- "$common_dir/../.." && pwd -P
}

vr_ssh() {
    ssh \
        -o BatchMode=yes \
        -o "ConnectTimeout=$VR_SSH_TIMEOUT" \
        -o ServerAliveInterval=15 \
        -o ServerAliveCountMax=2 \
        -- "$VR_HERMES_HOST" "$@"
}

vr_check_remote_identity() {
    local identity remote_hostname remote_user
    local -a identity_lines
    # The substitutions are intentionally evaluated by the remote shell.
    # shellcheck disable=SC2016
    identity="$(vr_ssh 'printf "%s\n%s\n" "$(hostname)" "$(id -un)"')" ||
        vr_die "could not reach Hermes over SSH"

    mapfile -t identity_lines <<< "$identity"
    (( ${#identity_lines[@]} == 2 )) ||
        vr_die "unexpected response while checking remote identity"
    remote_hostname="${identity_lines[0]}"
    remote_user="${identity_lines[1]}"
    [[ "$remote_hostname" == "$VR_HERMES_EXPECTED_HOSTNAME" ]] ||
        vr_die "refusing remote host '$remote_hostname'; expected '$VR_HERMES_EXPECTED_HOSTNAME'"
    [[ "$remote_user" == "ian-kengott" ]] ||
        vr_die "refusing remote user '$remote_user'; expected 'ian-kengott'"
}

vr_bootstrap_remote_marker() {
    vr_ssh bash -s -- \
        "$VR_REMOTE_DIR" "$VR_REMOTE_MARKER_NAME" "$VR_REMOTE_MARKER_CONTENT" <<'REMOTE'
set -euo pipefail

remote_dir="$1"
marker_name="$2"
marker_content="$3"

[[ "$remote_dir" =~ ^/home/ian-kengott/projects/[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || {
    printf 'unsafe remote directory: %s\n' "$remote_dir" >&2
    exit 1
}
[[ "$marker_name" == ".vector-regnum-hermes-worktree" ]] || exit 1
[[ "$marker_content" == "vector-regnum-hermes-worktree-v1" ]] || exit 1

if [[ -e "$remote_dir" && ! -d "$remote_dir" ]]; then
    printf 'remote target exists but is not a directory: %s\n' "$remote_dir" >&2
    exit 1
fi
if [[ -L "$remote_dir" ]]; then
    printf 'refusing symlinked remote target: %s\n' "$remote_dir" >&2
    exit 1
fi

mkdir -p -- "$remote_dir"
marker_path="$remote_dir/$marker_name"

if [[ -e "$marker_path" ]]; then
    [[ -f "$marker_path" && ! -L "$marker_path" ]] || {
        printf 'remote marker is not a regular file: %s\n' "$marker_path" >&2
        exit 1
    }
    [[ "$(<"$marker_path")" == "$marker_content" ]] || {
        printf 'remote marker content does not match\n' >&2
        exit 1
    }
    exit 0
fi

shopt -s nullglob dotglob
entries=("$remote_dir"/*)
if (( ${#entries[@]} != 0 )); then
    printf 'refusing to mark non-empty remote directory: %s\n' "$remote_dir" >&2
    exit 1
fi

printf '%s\n' "$marker_content" > "$marker_path"
REMOTE
}

vr_require_remote_marker() {
    vr_ssh bash -s -- \
        "$VR_REMOTE_DIR" "$VR_REMOTE_MARKER_NAME" "$VR_REMOTE_MARKER_CONTENT" <<'REMOTE'
set -euo pipefail

remote_dir="$1"
marker_name="$2"
marker_content="$3"

[[ "$remote_dir" =~ ^/home/ian-kengott/projects/[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || exit 1
[[ "$marker_name" == ".vector-regnum-hermes-worktree" ]] || exit 1
[[ "$marker_content" == "vector-regnum-hermes-worktree-v1" ]] || exit 1
[[ -d "$remote_dir" ]] || {
    printf 'remote worktree is missing: %s\n' "$remote_dir" >&2
    exit 1
}
[[ ! -L "$remote_dir" ]] || {
    printf 'refusing symlinked remote worktree: %s\n' "$remote_dir" >&2
    exit 1
}
[[ -f "$remote_dir/$marker_name" && ! -L "$remote_dir/$marker_name" ]] || {
    printf 'remote ownership marker is missing: %s/%s\n' "$remote_dir" "$marker_name" >&2
    exit 1
}
[[ "$(<"$remote_dir/$marker_name")" == "$marker_content" ]] || {
    printf 'remote ownership marker content does not match\n' >&2
    exit 1
}
REMOTE
}

vr_require_local_repo() {
    local repo_root="$1"
    [[ -d "$repo_root/.git" ]] || vr_die "not a Git worktree: $repo_root"
    [[ -f "$repo_root/gradlew" ]] || vr_die "Gradle wrapper is missing from $repo_root"
}

vr_validate_config
