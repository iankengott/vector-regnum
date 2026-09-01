# Vector-Regnum integration API v1

Priority 27 publishes a small optional server API for the separate Regnum SMP
repositories. Vector-Regnum remains fully playable when none of them are
installed. Companion systems remain in their own repositories and may not
receive direct attachment, VM, renderer, escrow, or world-mutation access.

## Packages

`vectorregnum.api.v1` contains immutable contracts and callback registries. It
uses UUIDs, strings, primitive values, records, and Java collections only. It
does not import Minecraft, NeoForge, Veil, or a companion mod.

`vectorregnum.neoforge.api.v1` is the server adapter. It accepts Minecraft
players and worlds, verifies the authoritative server thread, reuses existing
claim, PvP, lifecycle, disruption, progression, and mana rules, and converts
results into the loader-neutral v1 records.

Companion mods should declare Vector-Regnum as an optional compile-time
dependency, check that `vector_regnum` is loaded before linking their adapter,
and keep their standalone behavior when it is absent.

## Stable domains

API version 1 advertises exactly these domains in stable order:

1. `origins`
2. `combat`
3. `progression`
4. `world_story`
5. `administration`
6. `modpack`

The public registry accepts namespaced source IDs up to 128 characters. It
holds at most eight natural-element providers, eight cast-modifier providers,
and eight story listeners. Registration order never affects results. Duplicate
source IDs are rejected, handles unregister idempotently, callbacks run on the
calling server thread, and one failing callback cannot stop Vector-Regnum or
the other callbacks.

## Hooks

Origins may register a natural-element provider. On first login with no stored
identity, Vector-Regnum asks providers in source-ID order, accepts the first
canonical natural element, maps the legacy `frost` input to `ice`, rejects
Arcane, and otherwise uses its deterministic fallback. Stored identity remains
permanent. The NeoForge facade exposes an immutable player snapshot containing
natural element, mutable channel, and sorted Vector-Regnum unlock IDs.

Progression may grant only one of Vector-Regnum's known unlock IDs through the
facade. Unknown IDs fail closed. Reputation and patron systems may register a
cast modifier provider. Each provider returns finite multipliers from 0.5
through 2.0 for mana, casting time, upkeep, and instability. Providers compose
in source-ID order, the aggregate remains within 0.5 through 2.0, and the
existing server floors and reagent policy run afterward. The same central path
drives displayed and committed costs.

Combat submits a disruption request with attacker, target, source ID, stance
proof, weapon proof, and timing window. The adapter rechecks identity, loaded
state, dimension, line of sight, range, claims, teams, PvP, active-spell state,
and the shared priority-26 limits before asking the authoritative VM service to
cancel anything. The result uses stable v1 codes and exposes no raw VM handle.

World/Story may query a loaded mana region with a radius from 0 through 64
blocks. The adapter never loads a chunk, examines at most 256 in-range block
entity candidates in deterministic position order, and returns an immutable
summary of positive sources and reservoirs by canonical element. Zero-mana and
unrecognized candidates still consume the scan budget. The snapshot reports
unloaded and truncated results explicitly.

World/Story and Administration may register story listeners. Events contain a
stable UUID, revision, kind, game tick, actor UUID, dimension, bounded integer
position, subject ID, element ID, and outcome. Spell start and terminal events
share an ID and advance the revision. Persistent or restart-reconciled events
may be delivered at least once, so consumers deduplicate `(eventId, revision)`.
Events are observations and cannot cancel or rewrite Vector-Regnum state.

Administration and the modpack consume version, domain, snapshot, and event
metadata only. Configuration, pack manifests, distribution, and administration
actions remain owned by their repositories.

## Completion proof

`scripts/verify-priority27.sh` checks the package boundary, exact metadata,
bounds, absence of companion dependencies, focused JUnit coverage, production
GameTest inventory, JSON and shell syntax, and whitespace. The complete Hermes
ladder additionally runs every JUnit test, the live registration parity check,
all production GameTests, a clean-client checkpoint with no companion mods,
the overlay diff, direct visual inspection, unit shutdown, and the loopback
port check.
