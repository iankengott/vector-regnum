#!/usr/bin/env bash
set -euo pipefail

(( $# == 2 )) || {
    printf 'Usage: scripts/check-dev-server-config.sh CONFIG_DIR EXPECTED_PORT\n' >&2
    exit 2
}

config_dir="$1"
expected_port="$2"
eula="$config_dir/eula.txt"
properties="$config_dir/server.properties"

[[ -d "$config_dir" && ! -L "$config_dir" ]] || {
    printf 'Development server configuration directory is missing or symlinked: %s\n' "$config_dir" >&2
    exit 1
}
[[ "$expected_port" =~ ^[0-9]+$ ]] && (( expected_port >= 1024 && expected_port <= 65535 )) || {
    printf 'Invalid development server port: %s\n' "$expected_port" >&2
    exit 1
}
[[ -f "$eula" && ! -L "$eula" ]] || {
    printf 'Development EULA file is missing or symlinked: %s\n' "$eula" >&2
    exit 1
}
[[ -f "$properties" && ! -L "$properties" ]] || {
    printf 'Development server properties are missing or symlinked: %s\n' "$properties" >&2
    exit 1
}
[[ "$(grep -c '^eula=' "$eula")" -eq 1 ]] && grep -qx 'eula=true' "$eula" || {
    printf 'Development EULA must contain exactly eula=true\n' >&2
    exit 1
}
[[ "$(grep -c '^server-port=' "$properties")" -eq 1 ]] &&
    grep -qx "server-port=$expected_port" "$properties" || {
    printf 'Development server must bind exactly port %s\n' "$expected_port" >&2
    exit 1
}
[[ "$(grep -c '^server-ip=' "$properties")" -eq 1 ]] &&
    grep -qx 'server-ip=127.0.0.1' "$properties" || {
    printf 'Development server must bind exactly IPv4 loopback 127.0.0.1\n' >&2
    exit 1
}

printf 'Development server configuration is isolated on 127.0.0.1:%s.\n' "$expected_port"
