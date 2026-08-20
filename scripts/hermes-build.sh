#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=lib/hermes-common.sh
source "$SCRIPT_DIR/lib/hermes-common.sh"

usage() {
    cat <<'USAGE'
Usage: scripts/hermes-build.sh

Verify Hermes is using JDK 21, then run the remote Gradle `test build` task
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

# Priority 20 staging. The stage must be explicit: a bare gradle invocation now
# fails rather than silently building only the loader-neutral subset.
port_stage="${VECTOR_REGNUM_PORT_STAGE:-core}"
printf 'Building port stage %s (see gradle/port-manifest.txt).\n' "$port_stage"
if [[ "$port_stage" != "full" ]]; then
    printf 'WARNING: this is a partial staged build, not the full automated suite.\n' >&2
fi
./gradlew --no-daemon "-PportStage=$port_stage" test build
REMOTE

vr_note "Hermes tests and build passed."
