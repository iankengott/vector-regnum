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
grep -Fq '21. [x] **Elemental identity and affinity expansion.**' \
    ROADMAP.md || die 'ROADMAP.md does not mark priority 21 complete'
grep -Fq '22. [x] **Casting media, reagents, and resource escrow.**' \
    ROADMAP.md || die 'ROADMAP.md does not mark priority 22 complete'
grep -Fq '23. [x] **Persistent upkeep and natural conclusions.**' \
    ROADMAP.md || die 'ROADMAP.md does not mark priority 23 complete'
grep -Fq '24. [x] **Advanced shared-memory spell control.**' \
    ROADMAP.md || die 'ROADMAP.md does not mark priority 24 complete'
grep -Fq '25. [x] **Cooperative rituals and multicasting.**' \
    ROADMAP.md || die 'ROADMAP.md does not mark priority 25 complete'
grep -Fq '26. [x] **Recovered-mechanic security and accessibility hardening.**' \
    ROADMAP.md || die 'ROADMAP.md does not mark priority 26 complete'
grep -Fq '27. [x] **Versioned SMP integration API.**' \
    ROADMAP.md || die 'ROADMAP.md does not mark priority 27 complete'
grep -Fq '"version": 14' src/main/resources/assets/vector_regnum/guide/field_manual.json \
    || die 'Field Manual is not at the current priority-27 version'

grep -Fq '**Execution-host policy (Ian, 2026-08-27):** Hermes is the default' \
    AGENTS.md || die 'AGENTS.md lost the Hermes-first, approval-gated NixOS policy'
grep -Fq 'Use OpenAI Codex subagents only' AGENTS.md \
    || die 'AGENTS.md lost the OpenAI Luna-max subagent policy'

[[ -x scripts/hermes-diff-check.sh ]] \
    || die 'Hermes overlay diff checker is missing or not executable'
[[ -x scripts/hermes-reset-test-world.sh ]] \
    || die 'Hermes test-world reset helper is missing or not executable'
[[ -x scripts/hermes-record-video.sh ]] \
    || die 'Hermes focused-window video recorder is missing or not executable'
[[ -x scripts/verify-priority25.sh ]] \
    || die 'priority-25 verifier is missing or not executable'
[[ -x scripts/verify-priority27.sh ]] \
    || die 'priority-27 verifier is missing or not executable'
grep -Fq '"$remote_dir/scripts/hermes-reset-test-world.sh"' scripts/hermes-client.sh \
    || die 'Hermes client startup no longer resets generated test-world state'
grep -Fq 'the test world'"'"'s generated/world state while preserving playerdata,' \
    scripts/hermes-client.sh \
    || die 'Hermes client help no longer states the playerdata preservation rule'
grep -Fq 'advancements, and stats.' scripts/hermes-client.sh \
    || die 'Hermes client help no longer states the player-data preservation rule'
grep -Fq 'a fresh flat world is part of the reproducibility contract' scripts/README.md \
    || die 'scripts README lost the clean-world visual-evidence rule'

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
    'priority 21 is the first unfinished'
    'priority 21 remains unfinished'
    'priority 21 is next'
    'priority 21 elemental identity and affinity model is next'
    'priority 22 is the first unfinished'
    'priority 22 remains unfinished'
    'priority 22 is next'
    'priority 22 casting media, reagents, and resource escrow is next'
    'priority 23 is the first unfinished'
    'priority 23 remains unfinished'
    'priority 23 is next'
    'priority 23 persistent upkeep and natural conclusions is next'
    'priority 24 is the first unfinished'
    'priority 24 remains the first unfinished'
    'priority 24 is next'
    'priority 24 advanced shared-memory spell control is next'
    'priority 25 is the first unfinished'
    'priority 25 remains the first unfinished'
    'priority 25 is next'
    'priority 25 explicitly approved cooperative rituals are next'
    'priority 26 is the first unfinished'
    'priority 26 remains unfinished'
    'priority 27 is the first unfinished'
    'priority 27 remains unfinished'
    'priority 27 is next'
    'captured hermes frame still needs'
    'awaiting only direct inspection'
    'field manual v11 with exact crystal/media/infrastructure recipes and commands'
    'priorities 1–20a'
    'priorities 1–21 are checked'
    'priorities 1–22 are checked'
    '1. On NixOS, use Java 21'
    'the 19 production neoforge gametests'
    'all 19 required tests passed'
    'on the main NixOS PC, use the declared JDK'
    '## Play on the main PC'
    'temporary free ox-alpha'
    'opencode deepseekflash'
    'sol xhigh or opus 5 medium'
)

for stale_claim in "${STALE_CLAIMS[@]}"; do
    if matches="$(rg -n -i -F -- "$stale_claim" "${LIVE_HANDOFF_DOCS[@]}" || true)" && \
            [[ -n "$matches" ]]; then
        printf '%s\n' "$matches" >&2
        die "found stale claim: $stale_claim"
    fi
done

printf 'HANDOFF_DOCS_OK priority20a=complete priority21=complete priority22=complete priority23=complete priority24=complete priority25=complete priority26=complete priority27=complete priority28=next hermes_first=true luna_max=true checked_files=%d\n' \
    "${#LIVE_HANDOFF_DOCS[@]}"
