# Regnum repository map

Decision date: 2026-08-13. The SMP is a suite of separate **public** repositories
with small versioned integration APIs, not one monorepo. The links and local
paths below were verified when the repositories were created.

| Repository | Responsibility | Main-PC local location | Status |
|---|---|---|---|
| [`vector-regnum`](https://github.com/iankengott/vector-regnum) | Active magic mod; priority 20 ports its Fabric migration baseline to NeoForge 1.21.1 | `/home/iank/Desktop/my mods/mods-editing/vector-regnum` | Active |
| [`vector-regnum-fabric-legacy`](https://github.com/iankengott/vector-regnum-fabric-legacy) | Frozen verified Fabric 1.21.1 alpha | `/home/iank/Desktop/my mods/mods-editing/vector-regnum-fabric-legacy` | Deprecated/read-only |
| [`regnum-origins`](https://github.com/iankengott/regnum-origins) | Origins, Homunculus, body traits, permanent natural-element assignment | `/home/iank/Desktop/my mods/mods-editing/regnum-origins` | Scaffolded |
| [`regnum-combat`](https://github.com/iankengott/regnum-combat) | Precision melee, combos, stances, parries, interrupts, reverse-unwriting | `/home/iank/Desktop/my mods/mods-editing/regnum-combat` | Scaffolded |
| [`regnum-progression`](https://github.com/iankengott/regnum-progression) | Classes, professions, skill trees, patrons, reputation, boons | `/home/iank/Desktop/my mods/mods-editing/regnum-progression` | Scaffolded |
| [`regnum-world-story`](https://github.com/iankengott/regnum-world-story) | Primordials, story state, regional weather/mana storms, dimensions and endings | `/home/iank/Desktop/my mods/mods-editing/regnum-world-story` | Scaffolded |
| [`regnum-administration`](https://github.com/iankengott/regnum-administration) | Admin orb/pocket tools and SMP control surfaces | `/home/iank/Desktop/my mods/mods-editing/regnum-administration` | Scaffolded |
| [`regnum-smp-modpack`](https://github.com/iankengott/regnum-smp-modpack) | Third-party pack manifest/configuration, shader/Create compatibility and distribution | `/home/iank/Desktop/my mods/mods-editing/regnum-smp-modpack` | Scaffolded |

Artemis host power monitoring belongs in host infrastructure rather than a
Minecraft mod. If the SMP needs to display that telemetry in-game,
`regnum-administration` may consume a read-only authenticated endpoint.

The older public design repository
[`iankengott/magic`](https://github.com/iankengott/magic) exists, but it is
historical source material and is not the active Vector-Regnum code repository.

## Integration rule

Vector-Regnum publishes a minimal versioned API for elemental identity,
progression unlocks, reputation/patron modifiers, spell disruption, mana-region
queries, and story events. Companion mods depend on that API or communicate
through events/data packs. Vector-Regnum must not acquire mandatory dependencies
on every companion project.
