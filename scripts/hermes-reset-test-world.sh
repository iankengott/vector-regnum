#!/usr/bin/env bash
set -euo pipefail

# This helper is intentionally Hermes-only. hermes-client.sh invokes it after
# proving both owned units are stopped and immediately before runServer starts.
repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
server_dir="$repo_root/run/server"
properties="$server_dir/server.properties"
marker="$repo_root/.vector-regnum-hermes-worktree"
server_unit="vector-regnum-dev-server.service"
client_unit="vector-regnum-dev-client.service"

die() {
    printf 'test-world reset refused: %s\n' "$*" >&2
    exit 1
}

[[ "$(hostname)" == "ian-kengott-GF63-Thin-11SC" ]] || die "not running on Hermes"
[[ "$(id -un)" == "ian-kengott" ]] || die "unexpected user"
[[ "$repo_root" =~ ^/home/ian-kengott/projects/[A-Za-z0-9][A-Za-z0-9._-]*$ ]] ||
    die "unsafe repository path: $repo_root"
[[ -d "$repo_root" && ! -L "$repo_root" ]] || die "repository is missing or symlinked"
[[ -f "$marker" && ! -L "$marker" ]] || die "ownership marker is missing or unsafe"
[[ "$(<"$marker")" == "vector-regnum-hermes-worktree-v1" ]] ||
    die "ownership marker content does not match"
[[ -d "$server_dir" && ! -L "$repo_root/run" && ! -L "$server_dir" ]] ||
    die "server run directory is missing or symlinked"
[[ -f "$properties" && ! -L "$properties" ]] || die "server.properties is missing or unsafe"

for unit in "$server_unit" "$client_unit"; do
    if systemctl --user is-active --quiet "$unit"; then
        die "$unit is active"
    fi
done
if ss -H -ltn 'sport = :25575' | grep -q .; then
    die "loopback development port 25575 is busy"
fi

[[ "$(grep -c '^level-name=' "$properties")" -eq 1 ]] ||
    die "server.properties must contain exactly one level-name"
level_name="$(sed -n 's/^level-name=//p' "$properties")"
[[ "$level_name" == "vector-regnum-test" ]] || die "unexpected level-name: $level_name"
world_dir="$server_dir/$level_name"
[[ "$world_dir" == "$repo_root/run/server/vector-regnum-test" ]] ||
    die "resolved an unexpected world path"
[[ ! -L "$world_dir" ]] || die "world directory is symlinked"

mkdir -p -- "$world_dir"
for preserved in playerdata advancements stats; do
    preserved_path="$world_dir/$preserved"
    [[ ! -L "$preserved_path" ]] || die "$preserved is symlinked"
    [[ ! -e "$preserved_path" || -d "$preserved_path" ]] ||
        die "$preserved exists but is not a directory"
done

removed=0
while IFS= read -r -d '' entry; do
    [[ "$entry" == "$world_dir/"* && "$entry" != "$world_dir" ]] ||
        die "unsafe reset entry: $entry"
    rm -rf -- "$entry"
    (( removed += 1 ))
done < <(find "$world_dir" -mindepth 1 -maxdepth 1 \
    ! -name playerdata ! -name advancements ! -name stats -print0)

mkdir -p -- "$world_dir/playerdata" "$world_dir/advancements" "$world_dir/stats"
printf 'RESET_TEST_WORLD_OK world=%s removed_entries=%d preserved=playerdata,advancements,stats\n' \
    "$world_dir" "$removed"
