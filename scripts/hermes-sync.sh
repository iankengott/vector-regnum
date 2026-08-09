#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=lib/hermes-common.sh
source "$SCRIPT_DIR/lib/hermes-common.sh"

usage() {
    cat <<'USAGE'
Usage: scripts/hermes-sync.sh [--dry-run]

Synchronize the local Vector-Regnum working tree to its guarded Hermes
development directory. The first real sync will mark an empty target as owned
by this workflow. Every rsync deletion requires that marker to match.

Options:
  -n, --dry-run  Preview changes. Requires an already-marked remote directory.
  -h, --help     Show this help.
USAGE
}

dry_run=false
case "${1:-}" in
    "") ;;
    -n|--dry-run) dry_run=true ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; vr_die "unknown argument: $1" ;;
esac
(( $# <= 1 )) || { usage >&2; vr_die "too many arguments"; }

repo_root="$(vr_repo_root)"
vr_require_local_repo "$repo_root"
vr_check_remote_identity

if [[ "$dry_run" == true ]]; then
    vr_require_remote_marker
else
    vr_bootstrap_remote_marker
fi

# This check is intentionally adjacent to rsync: no --delete operation runs
# unless the fixed/validated target proves it belongs to this workflow.
vr_require_remote_marker

rsync_args=(
    --archive
    --human-readable
    --itemize-changes
    --delete-delay
    --exclude=/.git/
    --exclude=/.gradle/
    --exclude=/build/
    --exclude=/run/
    --exclude=/visual-evidence/
    --exclude=/out/
    --exclude=/.idea/
    --exclude=/.vscode/
    --exclude="/$VR_REMOTE_MARKER_NAME"
    --exclude='*.class'
    --exclude='*.log'
)
[[ "$dry_run" == true ]] && rsync_args+=(--dry-run)

export RSYNC_RSH="ssh -o BatchMode=yes -o ConnectTimeout=$VR_SSH_TIMEOUT -o ServerAliveInterval=15 -o ServerAliveCountMax=2"
vr_note "Syncing $repo_root/ to $VR_HERMES_HOST:$VR_REMOTE_DIR/"
rsync "${rsync_args[@]}" -- "$repo_root/" "$VR_HERMES_HOST:$VR_REMOTE_DIR/"

if [[ "$dry_run" == true ]]; then
    vr_note "Dry run complete; nothing was transferred or deleted."
else
    vr_note "Hermes worktree synchronized."
fi
