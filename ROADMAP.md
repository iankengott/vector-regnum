# Vector-Regnum roadmap

The priorities 1–10 milestone is implemented as a playable alpha. A checked item
means its first coherent end-to-end pass exists; it does not mean final balance,
art, UX, or production hardening is complete.

## Working foundations

- [x] Fabric 1.21.1 build, dedicated server, guarded Hermes workflow, and
  one-click local launch.
- [x] Compatibility compiler/runtime with exact source faults and Wild Magic.
- [x] Persistent tutorial guide, Sigil Tome, Firebolt, Frost Nova, effects,
  collision, cooldowns, and finite server-authoritative mana.
- [x] 73 pure/contract tests plus real local/Hermes server boots and Hermes
  visual inspection.

## Priorities 1–10 milestone

1. [x] Typed, tick-driven spell VM: closed runtime values, stack memory,
   resumable delays/durations, and immutable loader-neutral bytecode.
2. [x] Player-authored spell format: geometric ring/slot circles, deterministic
   checksummed saving, persistent per-player draft, and typed parameters.
3. [x] Circle editor/compiler feedback: place/remove/parameterize/undo/show,
   exact physical diagnostics, cost preview, and animated execution order.
4. [x] Control flow/server safety: comparisons, boolean logic, jumps, bounded
   loops, termination budgets, duration caps, result/range limits, and visual
   deduplication.
5. [x] Selection/perception: stable entity snapshots, radius filters, hostile
   filtering, capped results, and block-occluded entity raycasts.
6. [x] Physics/movement: impulse, acceleration, damping, ordered paths,
   move-toward, keep-distance, and Fabric-side validation/application.
7. [x] Mana-cost model: named work/range/duration/rarity/memory/perception/
   control dimensions, inverse-square range cost, and repeated loop-body quote.
8. [x] Scrolls, spellbooks, and stone tablets: recipes, checksummed payloads,
   single-use/reusable/permanent lifecycles, persistent tablet block entity, and
   verified world-anchor casting.
9. [x] Mana-crystal progression: zero start, capacity shards, finite permanent
   source nodes, elemental tuning, inverse-square local/remote draw,
   same-dimension loaded-source validation, research, recipes, and advancements.
10. [x] Expanded spell library: 15 playable programs across combat, defense,
    movement, utility, detection, and automation, all quoted by vm2 cost
    dimensions.

## Next priorities (canonical order)

When choosing new work, take the first unfinished item in this list unless Ian
explicitly reprioritizes it. The detailed sections below describe the broader
acceptance scope.

11. [ ] Create-style spell/scroll **Ponders**: animated compilation order,
    execution, mana breakdown, and representative fault/miscast states.
12. [ ] Graphical in-world circle editor with a discoverable sigil palette,
    parameter editing, diagnostics, and media binding without command fluency.
13. [ ] Natural mana-crystal world generation plus balanced geology, source
    growth/recharge, transport, and storage progression.
14. [ ] Fabric GameTests for commands, attachments, media/block-entity round
    trips, crystals, scheduled effects, restart behavior, and multiplayer.
15. [ ] Complete the spell language: static stack-type analysis,
    creation/form opcodes, and generic lowering/execution for every curated
    semantic opcode.
16. [ ] Formal multiplayer lifecycle and security: chunk unloads, death/copy,
    teams, claims, permissions, abuse cases, and upgrade migration.
17. [ ] Expand programmable automation with redstone logic, remote activation,
    data bridges, and explicit multithread/concurrency ownership rules.
18. [ ] Replace placeholder presentation with original textures, particles,
    sounds, UI, localization, and accessibility work.
19. [ ] Configuration, balancing, profiling, survival/multiplayer playtests,
    and compatibility testing with representative Fabric mods.
20. [ ] Release packaging: installation guide, changelog, screenshots/video,
    versioning, and distributable artifacts.

## Language/runtime follow-up

- [x] Direct clockwise circle-to-vm2 lowering for typed values, memory,
  arithmetic/logic, control/time, perception, and physics sigils.
- [x] Fabric tick scheduler and read-only perception/world-effect boundary.
- [ ] Static stack-type analysis before execution (runtime faults are already
  precise and bounded).
- [ ] Creation/form opcodes with concrete material/rarity constraints.
- [ ] Generic lowering/execution for every semantic library opcode; several
  library effects remain intentionally purpose-built Fabric adapters.
- [ ] Multithreaded circles and explicit concurrency/data ownership rules.

## Player authoring and teaching

- [x] Command-based server-authoritative editor and persistent draft.
- [x] Save, inspect, validate, copy into media, and recover via checksums.
- [x] Animated actual circle topology with compiler order/error highlighting.
- [x] Versioned Field Manual v2 with exact crystal/media recipes and commands.
- [ ] Graphical/in-world placement editor with discoverable sigil palette.
- [ ] **Create-style spell/scroll Ponders** showing compilation, execution,
  mana breakdown, and representative failure states step by step.
- [ ] Advancements/tutorial sequence that guides the complete survival arc.

## World, progression, and automation

- [x] Constructible finite crystal sources, capacity growth, extraction,
  elemental compatibility, attunement, and loaded-source remote draw.
- [x] First survival recipes, seven persistent research unlocks, and two
  advancement guidance entries.
- [ ] Natural crystal world generation, geology/rarity balance, transport,
  storage blocks, and source recharge/growth rules.
- [ ] Redstone logic expansion, remote activation, data bridges, and
  multithreaded automation described in the design documents.
- [ ] Formal chunk-unload, claim, team, and multiplayer permission policy.

## Content and release quality

- [x] Initial useful 15-spell library with concrete bounded world effects.
- [x] Persisted scheduled expiry for temporary light/redstone spell blocks.
- [ ] Original Vector-Regnum block/item textures, particles, sounds, and UI.
- [ ] Fabric GameTests for commands, attachments, media/block-entity round
  trips, crystal interactions, timers, and multiplayer behavior.
- [ ] Configuration, balancing, profiling, localization beyond English,
  accessibility, and broader abuse protection.
- [ ] Survival playtest, death/copy/restart/upgrade tests, mod compatibility,
  release packaging, changelog, screenshots/video, and installation docs.
