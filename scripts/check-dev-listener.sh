#!/usr/bin/env bash
set -euo pipefail

(( $# == 1 )) || {
    printf 'Usage: ss ... | scripts/check-dev-listener.sh EXPECTED_PORT\n' >&2
    exit 2
}

expected_port="$1"
[[ "$expected_port" =~ ^[0-9]+$ ]] && (( expected_port >= 1024 && expected_port <= 65535 )) || {
    printf 'Invalid development listener port: %s\n' "$expected_port" >&2
    exit 1
}

count=0
while read -r _ _ _ local_address _; do
    ((count += 1))
    [[ "$local_address" == "127.0.0.1:$expected_port" ]] || {
        printf 'Development listener escaped IPv4 loopback: %s\n' "$local_address" >&2
        exit 1
    }
done

(( count == 1 )) || {
    printf 'Expected exactly one development listener; found %s\n' "$count" >&2
    exit 1
}

printf 'Development listener is isolated on 127.0.0.1:%s.\n' "$expected_port"
