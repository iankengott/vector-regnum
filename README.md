# Vector-Regnum

> **Platform status (2026-08-22):** this is the active Vector-Regnum NeoForge
> 1.21.1 repository. The Fabric implementation is deprecated and frozen in its
> own archived repository. Priorities 20a and 21 passed their automated and
> guarded Hermes gates; Ian attested the corrected priority-21 artifact “all
> good,” and the final guide fix was directly inspected through the Main-PC
> desktop launcher. Priority 22 is the first unfinished item.
> The frozen Fabric repository and all companion projects are listed in
> [docs/REPOSITORY_MAP.md](docs/REPOSITORY_MAP.md).

*The Realm of Direction* is a programming-magic mod. Players arrange sigils on
geometric circles, read clockwise around
the outer ring and then inward. Valid circles become server-authoritative world
effects; invalid programs fault at an exact physical sigil and may collapse into
Wild Magic.

The current build is a substantial playable alpha, not a finished release. It
implements the first coherent working pass of priorities 1–21: typed/ticked
execution, authoring and diagnostics,
safety-bounded control flow, perception, physics, cost accounting, three spell
media, finite crystal progression, Ponders, the visual Field Manual, the
server-backed circle editor, natural crystal progression, GameTests,
multiplayer/security policy, programmable automation, and compiler-driven
client presentation.

## NeoForge baseline

Roadmap priority 20 is complete. Its implementation, automated coverage,
Hermes gates, and human-controlled Main-PC desktop-launch visual gate all
passed. The final local run joined the explicit IPv4 endpoint, rendered
Vector-Regnum content and items in-world, closed normally, and left the
development unit inactive and port free. The repeatable visual workflow is
`scripts/priority20-local-visual-wizard.sh`; the execution record lives in
`docs/NEOFORGE_PORT_PLAN.md`.

| | |
|---|---|
| Build | NeoForge 21.1.248, ModDevGradle 2.0.141, Mojang official mappings |
| Loader surface | NeoForge `@Mod`, deferred registries, attachments, SavedData, payloads, events, custom-feature worldgen, client subscribers |
| Automated suite | 235 JUnit tests and 19 production NeoForge GameTests |
| Live parity | Registries, payload directions, attachments, creative tab, and command root checked against a manifest |
| Launch workflow | Guarded NixOS launcher and guarded Hermes server/client mirror on loopback port 25575 |

The production matrix queries the running game rather than inferring loader
health from compilation. A deliberate broken registry ID was confirmed to fail
the parity GameTest before the source was restored.

## Optional Veil renderer

The priority-20a implementation pins Veil 4.4.1 as an optional client-only
dependency. One reflectively loaded adapter maps the bounded presentation
program and compact authoritative world traces into 59 Quasar emitter
motifs (element motes, torus rings, cylinder beams, bursts, sparks, smoke, and
light), at most 8 deferred lights, at most 16 emitters, and optional bloom.
Every mapped visual geometry family receives a capped Quasar motif. The
built-in renderer remains the only renderer on the default runtime and
dedicated server, and the mandatory Veil-absent/failure fallback.

With Veil active it owns every Vector-Regnum particle-based animation; the sole
vanilla-particle allowlist entry is the enchanting-table truth cue
(`VanillaParticleAllowlist`), enforced at the single guarded emission choke
point in `ClientPresentationRuntime`. Servers no longer spawn vanilla
particles at all: `SpellVisualManager`, `NeoForgeVmService`, and
`SemanticSpellExecutor` emit compact `presentation_trace`/`circle_preview`
payloads through the budgeted `ServerTraces` choke point, and each receiving
client renders them through its own backend. A source-scan test
(`ParticleAllowlistSourceScanTest`) proves every emission flows through the
guarded choke point, a vocabulary test proves every requestable Quasar motif
exists, and payload/factory tests cover the wire bounds.

`scripts/verify-priority20a.sh` proves the dependency boundary and focused
policy tests. The exact target-pack matrix passed with Create 6.0.10, Sodium
0.8.13-beta.2, Iris 1.8.14-beta.1, and Bliss 2.1.2. The inspected matrix image
`visual-evidence/main-pc-priority20a-create-matrix-safe.png` shows the staged
Create mechanical press and cogwheel beside the spell scene.
Iris compatibility mode keeps Quasar but disables Veil deferred lights and
bloom, avoiding invalid instanced draws.

For the completed priority, the full automated ladder passed (212 JUnit tests,
18 GameTests, JSON/script checks), guarded Hermes clients booted with Veil
present (all 19 motifs loaded, checkpoint staged, live trace capture in
`visual-evidence/hermes-window-20260821T141107Z.png`) and absent (built-in
backend, checkpoint staged), and both Hermes dev units were stopped with port
25575 free. Ian then passed the human-controlled Main-PC desktop attestation on
the final artifact through `scripts/priority20-local-visual-wizard.sh`.
Authored and library casts, F3+T reload, minimal LOD, reduced motion,
photosensitive mode, and mandatory truth cues passed under Veil. Minecraft
closed normally, the owned unit unloaded, and port 25575 was free.

## Confirmed working in the NeoForge alpha

- NeoForge 21.1.248, ModDevGradle 2.0.141, official/Parchment mappings,
  Gradle 9.2.1, and Java 21.
- **235 passing JUnit tests** covering the compatibility engine, typed VM,
  static stack analysis, semantics/presentation, circle authoring, media,
  guide/Ponder models, geology, transport, multiplayer policy, automation
  ownership, progression, spell-library contracts, priority-20a particle
  allowlist/trace wire/Quasar vocabulary, and priority-21 elemental matrix,
  migration, tuning, guide, and presentation checks, plus **19 passing
  production NeoForge GameTests** on an isolated headless server, including a
  real Vector Step follow-up-VM regression. The separate
  frozen Fabric alpha at `c7371ca` retains its 170-test/16-GameTest record.
- A Minecraft-independent `vm2` with numbers, booleans, points, vectors,
  entity references, immutable lists, Push/Pop/Dup memory, arithmetic, logic,
  branches, bounded loops, delays, durations, and exact source locations.
- Hard limits for stack depth, per-tick work, total work, spell lifetime,
  selection size/range, loop count, and effect duration. Per-tick exhaustion
  yields instead of freezing the server.
- Selection and block-occluded entity raycasts through a read-only world
  adapter. The VM emits validated impulse, acceleration, damping, ordered path,
  move-toward, and keep-distance commands; only the NeoForge adapter mutates the
  world.
- A named mana quote covering physical work, quadratic range/inverse-square
  falloff, duration, rarity/material difficulty, memory, perception, and
  control-flow repetition. Loop quotes conservatively repeat their body cost.
- Persistent player circles with a bounded undo history, deterministic
  checksummed saving, and animated previews. The command editor supports both
  the original compatibility sigils and direct `VM_` bytecode circles.
- Exact ring/slot compiler feedback. Ring `0` is outermost; slot `0` is north;
  slots increase clockwise; execution then advances inward.
- Bound media: a scroll is consumed by its first accepted successful cast, a
  spellbook is reusable, and a carved tablet becomes an unbreakable permanent
  block whose cast origin is its verified dimension/position anchor.
- Players begin with **0 mana and 0 capacity**. Capacity grows from crystal
  shards; finite source nodes hold eight charges; the last used same-dimension
  node can feed a cast while its chunk remains loaded. Draw strength uses
  inverse-square distance and source/channel elemental compatibility.
- Source nodes can be tuned to every canonical element with fourteen explicit
  vanilla tuning items. Player channel affinity remains mutable through
  `/vectorregnum mana attune ...` and controls source-conversion efficiency.
- Every character has one deterministic permanent natural element persisted in
  schema 3. Natural identity governs spell affinity cost/stability; the separate
  channel does not rewrite identity. The JSON-backed symmetric matrix admits
  only 100/75/50/25% bands, with Arcane neutral, rare Void, and a 25% floor.
- Crystal/media recipes, seven persistent research discoveries, advancement
  guidance, and a versioned first-join Field Manual v7. The v7 book opens a
  searchable, scrollable, progression-aware visual manual with three original
  illustrated plates, contextual links, tooltips, live shaped/shapeless recipe
  grids, bounded recipe-alternative cycling, and item icons. Its Ponder cards
  and the `K` key request the latest
  server-authoritative completed trace, including actual vm2 ticks, effects,
  runtime faults, compatibility failures, and Wild Magic categories.
- Natural crystal generation has deterministic buried veins, grade/depth/host
  rules, scheduled maturation/recharge, persistent progress, and competition
  balance. Crystal Vials, Runed Cells, and Resonant Vaults connect through
  matching Raw, Runed, and Resonant conduits with bounded loaded-chunk search,
  tiered range/throughput/loss, restart-safe in-flight mana, upgrades,
  interactions, and comparator output.
- Press `V` in a connected world for the server-backed graphical circle editor;
  its responsive layout exposes a typed/searchable sigil palette, drag
  placement and movement with preserved parameters, right-click/Delete removal,
  quoted parameter editing, diagnostics, undo, compile, explicit
  Scroll/Book/Tablet binding, and a server-validated fixed block-face anchor.
  Static stack analysis rejects invalid vm2 programs before execution. All 15
  curated spells lower through one complete opcode-driven semantic backend;
  authored `VM_CREATE_FORM` programs create bounded permission-checked material
  forms, and the presentation IR receives authoritative VM events.
- Fifteen playable spells across combat, defense, movement, utility,
  detection, and automation. Their semantic programs are quoted through the
  same vm2 cost dimensions; Vector Step and Kinetic Ward use live vm2 physics.
- Temporary Mage Light and Redstone Oracle blocks remove themselves through
  persisted scheduled block ticks, including across a normal server restart.
- Formal multiplayer lifecycle and security: running spells cancel on owner
  disconnect/death/dimension change or an unloaded owner chunk; PvP and team
  friendly-fire policy is enforced; private/team chunk spell claims compose
  with vanilla/NeoForge protection callbacks; per-player casting is rate- and
  concurrency-bounded; and versioned player/claim state migrates safely.
- A persistent programmable Automation Relay can bind the current circle to
  rising, falling, changing, or sustained redstone, accept owner-only remote
  requests, expose bounded read-only data bridges/comparator output, and feed
  immutable requests through a bounded many-producer/single-server-thread
  scheduler. Offline owners and unloaded relay chunks never execute.
- Every semantic or authored VM spell compiles into a bounded, versioned client
  presentation program driven only by actually executed server events. The
  runtime composes deterministic rings, beams, ribbons, particles, atmosphere,
  spatial sound, material response, screen treatment, and aftermath with
  distance/quality LODs. Press `O` for independent particle, darkness/fog,
  flash, chromatic, camera, audio, reduced-motion, and photosensitivity controls.
- The original Sigil Tome, Firebolt, Ice Nova, collision/damage/status
  effects, mana starvation, and context-sensitive Wild Magic remain available.

The full NeoForge workflow has been exercised on Hermes: remote Java 21
tests/build, loopback-only server, quick-joined client, automated in-game
preflight, and direct inspection of the priorities 1–19 showcase in
`visual-evidence/hermes-window-20260820T071914Z.png`. The latest process restart
logged `persistence_claim=restored`, `player_schema=2`, and `unlocks_added=0`;
both guarded units were stopped afterward. The real Main-PC shortcut then
exposed and drove a fix for `localhost` resolving to IPv6 while the server
listens on IPv4. The explicit `127.0.0.1` endpoint passes local build and
Hermes client verification. The post-audit Hermes run again joined through the
explicit IPv4 endpoint and displayed the amethyst/copper showcase with
Vector-Regnum items in the hotbar. Ian then passed the human-controlled
Main-PC visual gate through `scripts/priority20-local-visual-wizard.sh`,
confirming in-world content, rendered items, normal shutdown, and clean
development-unit and port state.

Priority 20a added two more inspected Hermes runs. The built-in capture
`visual-evidence/hermes-window-20260820T152855Z.png` retained mandatory
geometry and particles with Veil absent. The Veil capture
`visual-evidence/hermes-window-20260820T153048Z.png` showed the same readable
scene with Quasar motes, bloom, and local lighting. Both clients reached
`VISUAL_CHECKPOINT_READY`; both remote units were stopped afterward.

Priority 21's final Hermes runs reached `VISUAL_CHECKPOINT_READY` with player
schema 3 and the four affinity bands, then logged
`ELEMENT_PALETTE_READY ... count=14` after the earlier cues expired. The
inspected Veil frame
`visual-evidence/hermes-window-20260822T065044Z.png` showed the clean bounded
Quasar scene with no missing textures; the built-in frame
`visual-evidence/hermes-window-20260822T065259Z.png` showed the distinct
canonical fallback motifs. Veil loaded all 59 emitter definitions. Both owned
Hermes units were stopped and port 25575 was free afterward.

The final post-guide-fix Hermes frame
`visual-evidence/hermes-window-20260822T175002Z.png` visibly retained the
authored circle and priority-21 checkpoint while Veil loaded all 59 Quasar
emitters. The Main-PC guide audit compared
`visual-evidence/guide-audit/00-inventory.png` with
`visual-evidence/guide-audit/05-final-guide.png`: dark text no longer has a
duplicate shadow, the complete source illustration is scaled rather than
cropped, and compact layouts fit the whole plate inside the opening viewport.
The live search overlay was also inspected. Minecraft then closed normally,
the temporary input binding was restored, the local unit became inactive, and
port 25575 was free.

## Build and test

Java 21 is required. Run the full build and the real NeoForge GameTest server:

```bash
./gradlew --no-daemon clean test build
./gradlew --no-daemon runGameTestServer
```

On the main NixOS PC, use the declared JDK without installing an imperative
profile:

```bash
task_jdk=$(nix eval --raw nixpkgs#jdk21.outPath)
JAVA_HOME="$task_jdk" PATH="$task_jdk/bin:$PATH" \
  ./gradlew --no-daemon clean test build
JAVA_HOME="$task_jdk" PATH="$task_jdk/bin:$PATH" \
  ./gradlew --no-daemon runGameTestServer
```

`scripts/verify-port.sh` runs the clean build plus JSON, shell-syntax, and diff
checks. The GameTest task must separately report all 19 required tests passed.

## Play on the main PC

The executable `/home/iank/Desktop/Vector-Regnum.desktop` runs
`scripts/local-play.sh`, which resolves Java 21,
stages only this mod in an isolated flat world on `127.0.0.1:25575`, launches
Minecraft through `steam-run`, and stops the private server when the client
closes. It does not touch the normal Minecraft launcher, saves, modpacks, or
port 25565. Before starting anything, the launcher validates that the checked-in
EULA, server port, and IPv4 loopback bind are unique and exact.

## Hermes development workflow

The scripts exercise the active NeoForge repository through guarded services.

```bash
scripts/hermes-sync.sh
scripts/hermes-build.sh
scripts/hermes-client.sh restart
scripts/hermes-client.sh logs
scripts/hermes-screenshot.sh window
scripts/hermes-client.sh stop
```

The guarded target is
`ian-kengott@100.88.229.63:/home/ian-kengott/projects/vector-regnum`. Only the
two transient `vector-regnum-dev-*` user services and port 25575 are controlled.
Hermes sets `VECTOR_REGNUM_VISUAL_CHECK=1`, which stages an automated 15-second
milestone scene. Normal local play does not run that fixture.

## In-game commands

Start with:

```text
/vectorregnum guide
/vectorregnum mana
/vectorregnum circle
/vectorregnum library list
/vectorregnum progression
```

Circle authoring:

```text
/vectorregnum circle new <id>
/vectorregnum circle starter
/vectorregnum circle vm_starter
/vectorregnum circle place <ring> <slot> <SIGIL>
/vectorregnum circle parameter <ring> <slot> <number>
/vectorregnum circle params <ring> <slot> <comma-or-space-separated-values>
/vectorregnum circle remove <ring> <slot>
/vectorregnum circle undo
/vectorregnum circle show
/vectorregnum circle compile
/vectorregnum circle cast
/vectorregnum circle bind scroll|book|tablet
```

For `circle params`, numbers become typed numbers, `true`/`false` become
booleans, and `text:<value>` becomes text (for example an entity UUID).

Progression and spells:

```text
/vectorregnum mana attune water|fire|air|earth|lightning|time|space|light|dark|nature|ice|sound|void|arcane
/vectorregnum research combat_weaving
/vectorregnum library cast <spell-id>
/vectorregnum vm probe
/vectorregnum vm demo
```

Multiplayer security and automation:

```text
/vectorregnum security
/vectorregnum security claim private|team
/vectorregnum security release
/vectorregnum automation give
/vectorregnum automation program <x> <y> <z>
/vectorregnum automation rule rising|falling|change|while_high <x> <y> <z> <threshold> <cooldown>
/vectorregnum automation trigger <x> <y> <z>
/vectorregnum automation inspect <x> <y> <z>
```

The library IDs are `ember_lance`, `chain_ice`, `gravity_slam`,
`aegis_shell`, `kinetic_ward`, `vector_step`, `featherfall`, `mage_light`,
`excavate`, `stoneweave`, `life_sense`, `ore_resonance`, `sentry_pulse`,
`harvest_cycle`, and `redstone_oracle`.

Admin/development commands include `mana refill`, `mana give_source`,
`progression unlock_all`, `devkit`, `showcase`, `give_tome`, and the three
`miscast` variants.

## Typed circle sigils

Useful direct-vm2 sigils include:

- values/memory: `VM_PUSH_SELF`, `VM_PUSH_ORIGIN`, `VM_PUSH_LOOK`,
  `VM_PUSH_NUMBER`, `VM_PUSH_BOOLEAN`, `VM_PUSH_VECTOR`, `VM_PUSH_POINT`,
  `VM_PUSH_ENTITY`, `VM_PUSH_POINT_LIST`, `VM_POP`, and `VM_DUP`;
- arithmetic/logic: `VM_ADD`, `VM_SUBTRACT`, `VM_MULTIPLY`, `VM_DIVIDE`,
  `VM_EQUALS`, `VM_LESS_THAN`, `VM_GREATER_THAN`, `VM_NOT`, `VM_AND`, `VM_OR`;
- control/time: `VM_JUMP`, `VM_JUMP_IF_FALSE`, `VM_LOOP`, `VM_DELAY`, and
  `VM_DURATION`;
- perception: `VM_SELECT_RADIUS`, `VM_SELECT_HOSTILE`, and
  `VM_RAYCAST_ENTITIES`;
- physics: `VM_IMPULSE`, `VM_ACCELERATION`, `VM_DAMPING`, `VM_FOLLOW_PATH`,
  `VM_MOVE_TOWARD`, and `VM_KEEP_DISTANCE`;
- terminal: `EXECUTE`.

Run `circle vm_starter` for a working delayed, costed Vector Step example.
The original seven compatibility sigils remain supported for older circles.

## Spell presentation system

Spell presentation is compiler-driven rather than limited to a hand-authored
animation for each library spell. Shared spell semantics produce authoritative
VM behavior and a bounded client presentation program, with runtime events
keeping branches, loops, delays, targets, and paths in sync.

Every spell combines many restrained, coordinated
layers: readable form and telegraphing, particles and procedural geometry,
illumination and dynamic/shadow response, darkness/fog and air movement,
spatial sound, camera/screen response, material interaction, and lingering aftermath. Those
examples are a floor rather than a closed list. The priority-20a work added
[FoundryMC Veil](https://github.com/FoundryMC/Veil) as an optional modular
rendering backend for reusable particles, beams, ribbons, meshes, lights,
framebuffers, and post effects. Veil 4.4.1 consumes the existing bounded
presentation program and compact authoritative traces rather than replace the
spell grammar. Essential effects remain scalable, accessible, mechanically
truthful, and functional through the built-in renderer when Veil or a
compatible shader path is unavailable.

See [SPELL_PRESENTATION.md](SPELL_PRESENTATION.md) for the presentation IR,
semantic-generation rules, sensory choreography standard, runtime boundaries,
budgets, compatibility, and accessibility requirements.

## Recovered SMP design decisions

The recovered SMP notes have been reconciled with the implemented alpha in
[docs/SMP_INTEGRATION_DECISIONS.md](docs/SMP_INTEGRATION_DECISIONS.md). The
canonical direction is:

- NeoForge 1.21.1 replaces Fabric for active development; Fabric remains a
  separate explicitly deprecated backup.
- Each character has exactly one permanent natural element. Frost becomes Ice;
  the ordinary twelve are Water, Fire, Air, Earth, Lightning, Time, Space,
  Light, Dark, Nature, Ice, and Sound; Void is rare, while Arcane is neutral raw
  mana rather than a natural element. Mutable attunement uses data-driven
  affinity distance with 100/75/50/25% efficiency and a 25% opposed floor.
- Rituals, engravings, books, scrolls, and other casting methods may commit
  reagents to reduce mana, casting time, upkeep, and instability. Genuine spell
  faults consume committed resources; policy or engine failures do not.
- Persistent magic owns a restart-safe upkeep/cleanup record and a natural
  endpoint. If it cannot pay before concluding—or never concludes before its
  hard cap—it resolves through bounded deterministic Wild Magic and atomic
  cleanup.
- Parallel branches retain a shared atomic `Push`/`Pop` stack but advance in a
  deterministic authoritative server-tick order.
- Every cooperative ritual requires explicit approval from every contributor
  for that ritual's exact bounded commitment.
- Dangerous and coercive mechanics retain their identity behind permission,
  rate/range/lifetime, deterministic-randomness, telegraph, cleanup, and
  accessibility boundaries.

Origins, precision melee, general progression, story/world systems,
administration, and the modpack are separate repositories connected through a
small versioned API. See [docs/REPOSITORY_MAP.md](docs/REPOSITORY_MAP.md).

## Still not finished

Priority 22's casting-media, reagent, and resource-escrow work is next.
Persistent upkeep and conclusions, shared-memory branching,
explicitly approved cooperative rituals, security and accessibility hardening,
and the optional SMP integration API follow. Configuration, balancing,
profiling, full playtests, NeoForge/modpack compatibility, final art,
localization, and release packaging come afterward; that release milestone also
owns publishing inspected in-game images to the Regnum Hub.

See [ROADMAP.md](ROADMAP.md) for the detailed status.

## Continuing development

A fresh AI or developer should begin with [AGENTS.md](AGENTS.md). It records
the machine boundaries, canonical priority queue, required NixOS and Hermes
verification ladder, visual-inspection requirement, regression invariants, and
documentation handoff rules. In a new session, asking for "the next unfinished
Vector-Regnum priorities" is sufficient; the numbered queue in
[ROADMAP.md](ROADMAP.md) controls the order. At this handoff, priorities 20a
and 21 have coherent end-to-end alpha passes with every required automated,
remote, and human visual gate complete; priority 22 is the first unfinished
item.
`AGENTS.md` also records Ian's subagent ladder — the temporary free ox-alpha
first while it lasts, then opencode deepseekflash, then Luna max, then Sol
xhigh or Opus 5 medium — plus the delegation rules, so no earlier chat context
is required.

---

Inspired by hard science and high fantasy.
