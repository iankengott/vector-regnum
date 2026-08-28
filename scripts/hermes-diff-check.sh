#!/usr/bin/env bash
set -euo pipefail

readonly EXPECTED_HOST='ian-kengott-GF63-Thin-11SC'
readonly EXPECTED_USER='ian-kengott'
readonly PUBLIC_REMOTE='https://github.com/iankengott/vector-regnum.git'
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd -P)"
readonly SCRIPT_DIR REPO_ROOT

[[ "$(hostname)" == "$EXPECTED_HOST" ]] || {
    printf 'Hermes diff check refused host: %s\n' "$(hostname)" >&2
    exit 1
}
[[ "$(id -un)" == "$EXPECTED_USER" ]] || {
    printf 'Hermes diff check refused user: %s\n' "$(id -un)" >&2
    exit 1
}

audit_root="$(mktemp -d)"
readonly audit_root
audit_repo="$audit_root/repo"
readonly audit_repo
cleanup() {
    [[ -n "$audit_root" && "$audit_root" == /tmp/* ]] || return
    rm -rf -- "$audit_root"
}
trap cleanup EXIT

git clone --depth 1 "$PUBLIC_REMOTE" "$audit_repo"
rsync -a --delete \
    --exclude='.git' \
    --exclude='.gradle' \
    --exclude='build' \
    --exclude='run' \
    --exclude='visual-evidence' \
    "$REPO_ROOT/" "$audit_repo/"

git -C "$audit_repo" add -N .
git -C "$audit_repo" diff --check
untracked="$(git -C "$audit_repo" ls-files --others --exclude-standard)"
[[ -z "$untracked" ]] || {
    printf 'Hermes overlay left untracked files after intent-to-add:\n%s\n' "$untracked" >&2
    exit 1
}

printf 'HERMES_DIFF_CHECK_OK source=%s\n' "$REPO_ROOT"
