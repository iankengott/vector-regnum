#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=lib/hermes-common.sh
source "$SCRIPT_DIR/lib/hermes-common.sh"

usage() {
    cat <<'USAGE'
Usage: scripts/hermes-build.sh

Verify Hermes is using JDK 21, then run the remote clean NeoForge test/build task
graph. Run hermes-sync.sh first when local sources have changed.
USAGE
}

case "${1:-}" in
    "") ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; vr_die "unknown argument: $1" ;;
esac
(( $# == 0 )) || exit 0

vr_check_remote_identity
vr_require_remote_marker

vr_note "Running tests and build on Hermes with JDK 21..."
vr_ssh bash -s -- "$VR_REMOTE_DIR" <<'REMOTE'
set -euo pipefail

remote_dir="$1"
[[ "$remote_dir" =~ ^/home/ian-kengott/projects/[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || exit 1
cd -- "$remote_dir"
[[ -x ./gradlew ]] || {
    printf 'Gradle wrapper is missing or not executable: %s/gradlew\n' "$remote_dir" >&2
    exit 1
}

java_version="$(java -version 2>&1 | sed -n '1p')"
java_major="$(sed -E 's/.*version "([0-9]+).*/\1/' <<< "$java_version")"
[[ "$java_major" == "21" ]] || {
    printf 'Hermes must use JDK 21; found: %s\n' "$java_version" >&2
    exit 1
}
printf 'Using %s\n' "$java_version"

./gradlew --no-daemon clean test build
find src -type f -name '*.json' -print0 | xargs -0 -n1 jq empty
find scripts -type f -name '*.sh' -print0 | xargs -0 -n1 bash -n
REMOTE

vr_note "Hermes tests and build passed."
