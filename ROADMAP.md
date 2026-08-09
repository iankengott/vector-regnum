# Vector-Regnum roadmap

Vector-Regnum is a tested playable vertical slice, not a finished mod. The
current seven-sigil frontend proves that a program can compile into a
server-authoritative Minecraft effect. The remaining work turns that proof into
the full player-authored programming-magic system.

## Working vertical slice

- [x] Fabric 1.21.1 build, dedicated server, and one-click local launch.
- [x] Minecraft-independent compiler/runtime with exact source faults.
- [x] Compatibility sigils: origin, element, forward vector, shape, expand,
  amplify, and terminal execute.
- [x] Persistent finite mana with no passive regeneration.
- [x] Sigil Tome, Firebolt, Frost Nova, block collision, damage/status effects,
  Wild Magic, particles, commands, and cooldowns.
- [x] First-join tutorial book and starter Tome.
- [x] Automated tests plus local and Hermes visual-test workflows.

## Language and runtime

- [ ] Replace/extend the compatibility frontend with typed runtime values:
  numbers, booleans, points, vectors, entities, elements, shapes, and lists.
- [ ] Implement stack memory and explicit `Push`/`Pop` operations.
- [ ] Implement ticked execution, delay, duration, and resumable spell state.
- [ ] Add logic gates, comparison, branching, bounded loops, and termination
  budgets that cannot freeze a server.
- [ ] Add perception, raycasting, entity/block selection, and filtering.
- [ ] Add physics operations such as impulse, acceleration, damping, paths,
  move-toward-point, and keep-distance.
- [ ] Add creation/form operations with rarity/material constraints.
- [ ] Implement the full cost model for work, range, inverse-square perception,
  rarity, memory, duration, and control-flow complexity.
- [ ] Expand compiler diagnostics and Wild Magic coverage for the new language.

## Player authoring and teaching

- [ ] Create the actual clockwise/inward magic-circle representation.
- [ ] Build a player-facing circle editor and sigil placement interaction.
- [ ] Encode, save, copy, inspect, and validate player-authored programs.
- [ ] Implement stone tablets/carving, single-use scrolls, and reusable spell
  books with distinct power/cost tradeoffs.
- [ ] Add **Create-style spell/scroll Ponders**: pondering a spell or scroll
  opens a staged animated scene that shows its sigils compiling and the spell
  working step by step, including mana cost and representative failure states.
- [ ] Connect the Field Manual to recipes, circle editing, Ponders, and a proper
  progression/tutorial sequence.

## World, progression, and automation

- [ ] Add mana crystals, world generation, extraction, transport, and storage.
- [ ] Balance finite-mana survival progression and recovery after Wild Magic.
- [ ] Add recipes, research/unlocks, advancement guidance, and loot integration.
- [ ] Add redstone logic, remote activation, multithreaded circles, and data
  bridges described in the design documents.
- [ ] Decide how circles interact with chunks, unloaded areas, claims, and
  multiplayer permissions.

## Content and release quality

- [ ] Add a useful library of spells beyond the three development presets.
- [ ] Replace placeholder vanilla particles/sounds and the book item model with
  a coherent Vector-Regnum visual/audio language.
- [ ] Add configuration, balancing, GameTests/integration tests, profiler limits,
  localization, accessibility, and multiplayer abuse protection.
- [ ] Test survival play, death/copy behavior, multiple simultaneous casters,
  server restarts, upgrades, and mod compatibility.
- [ ] Produce release packaging, changelog, screenshots/video, and installation
  documentation for non-development Minecraft instances.
