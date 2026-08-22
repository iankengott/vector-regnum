#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd -P)"
readonly SCRIPT_DIR REPO_ROOT

die() {
    printf 'handoff documentation check failed: %s\n' "$*" >&2
    exit 1
}

cd -- "$REPO_ROOT"

grep -Fq '20a. [x] **Veil-backed modular presentation overhaul and compatibility gate.**' \
    ROADMAP.md || die 'ROADMAP.md does not mark priority 20a complete'

readonly -a LIVE_HANDOFF_DOCS=(
    AGENTS.md
    README.md
    ROADMAP.md
    SPELL_PRESENTATION.md
    scripts/README.md
    docs/FIELD_MANUAL_BACKEND_DECISION.md
    docs/NEOFORGE_PORT_PLAN.md
    docs/REPOSITORY_MAP.md
    docs/SMP_INTEGRATION_DECISIONS.md
)

readonly -a STALE_CLAIMS=(
    'adapter is still incomplete'
    'partial priority-20a adapter'
    'priority 20a has a partial veil adapter'
    'priority 20a is the first unfinished'
    'priority 20a remains unfinished'
    'remaining work for 20a'
    'repeat those gates after the full particle migration'
    'completion requires an inventory'
    'this is where to resume'
    'next action: slice 5'
    'priority 20 ports its fabric migration baseline'
    'neoforge priority 20 must revalidate'
)

for stale_claim in "${STALE_CLAIMS[@]}"; do
    if matches="$(rg -n -i -F -- "$stale_claim" "${LIVE_HANDOFF_DOCS[@]}" || true)" && \
            [[ -n "$matches" ]]; then
        printf '%s\n' "$matches" >&2
        die "found stale claim: $stale_claim"
    fi
done

printf 'HANDOFF_DOCS_OK priority20a=complete checked_files=%d\n' \
    "${#LIVE_HANDOFF_DOCS[@]}"
