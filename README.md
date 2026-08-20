# Vector-Regnum

> **Platform status (2026-08-13):** this is the active Vector-Regnum repository,
> currently carrying the verified Fabric 1.21.1 alpha as its migration baseline.
> The Fabric implementation is deprecated and frozen for new gameplay work;
> **active development targets NeoForge 1.21.1** through roadmap priority 20.
> The frozen Fabric repository and all companion projects are listed in
> [docs/REPOSITORY_MAP.md](docs/REPOSITORY_MAP.md).

*The Realm of Direction* is a programming-magic mod. Players arrange sigils on
geometric circles, read clockwise around
the outer ring and then inward. Valid circles become server-authoritative world
effects; invalid programs fault at an exact physical sigil and may collapse into
Wild Magic.

The current build is a substantial playable alpha, not a finished release. It
implements the first coherent working pass of priorities 1–19: typed/ticked
execution, authoring and diagnostics,
safety-bounded control flow, perception, physics, cost accounting, three spell
media, finite crystal progression, Ponders, the visual Field Manual, the
server-backed circle editor, natural crystal progression, GameTests,
multiplayer/security policy, programmable automation, and compiler-driven
client presentation.

## NeoForge port status

Roadmap priority 20 is in progress on branch `phase3-mapping`. The plan and
every gate live in `docs/NEOFORGE_PORT_PLAN.md`; this is only the current
position.

| | |
|---|---|
| Build | NeoForge 21.1.248, ModDevGradle 2.0.141, Mojang official mappings |
| Phases done | 0, 1, 2 |
| Phase 3 | 4 of about 9 slices done |
| Loader classes ported | 58 of 110 |
| Remaining mapping-only | 26 classes, 3,747 lines |
| Deferred to phases 4-9 | 29 classes, 5,105 lines that need Fabric API replacement |
| Tests green at `-PportStage=slice4` | 149, zero failures |

**What green does not mean.** Those 149 tests pass with no blocks, items, block
entities, networking, or commands registered, because they are loader-neutral
logic. There is still no NeoForge `@Mod` entrypoint, so nothing is launchable
and the GameTests cannot run. A passing build here proves the code compiles
against Mojang mappings, not that the mod works. Phase 10 adds the
registration-presence test that closes this gap.

## Confirmed working in the Fabric legacy alpha

- Fabric Loader 0.18.3, Fabric API 0.116.7+1.21.1, Yarn
  1.21.1+build.3, Loom 1.14.8, Gradle 9.2.1, and Java 21.
- **170 passing JUnit tests** covering the compatibility engine, typed VM,
  static stack analysis, semantics/presentation, circle authoring, media,
  guide/Ponder models, geology, transport, multiplayer policy, automation
  ownership, progression, and spell-library contracts, plus **16 passing
  production Fabric GameTests** on an isolated headless server. Those numbers
  describe the frozen Fabric alpha at `c7371ca`, not the current NeoForge tree.
  See the port status below for what passes today.
- A Minecraft-independent `vm2` with numbers, booleans, points, vectors,
  entity references, immutable lists, Push/Pop/Dup memory, arithmetic, logic,
  branches, bounded loops, delays, durations, and exact source locations.
- Hard limits for stack depth, per-tick work, total work, spell lifetime,
  selection size/range, loop count, and effect duration. Per-tick exhaustion
  yields instead of freezing the server.
- Selection and block-occluded entity raycasts through a read-only world
  adapter. The VM emits validated impulse, acceleration, damping, ordered path,
  move-toward, and keep-distance commands; only the Fabric adapter mutates the
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
- Source nodes can be tuned Arcane, Fire, Frost, or Void with amethyst shard,
  blaze powder, snowball, or ender pearl. Player channel affinity is selected
  with `/vectorregnum mana attune ...`.
- Crystal/media recipes, seven persistent research discoveries, advancement
  guidance, and a versioned first-join Field Manual v6. The v6 book opens a
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
  with vanilla/Fabric protection callbacks; per-player casting is rate- and
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
- The original Sigil Tome, Firebolt, Frost Nova, collision/damage/status
  effects, mana starvation, and context-sensitive Wild Magic remain available.

The full workflow has been exercised on Hermes: remote Java 21 tests/build,
loopback-only server, quick-joined Minecraft client, automated in-game
preflight, and direct inspection of the priorities 1–19 showcase in
`visual-evidence/hermes-window-20260813T153728Z.png`. The real
`/home/iank/Desktop/Vector-Regnum.desktop` was also launched on the main PC;
its client joined the isolated server, entered gameplay, and opened the new
`O`-key presentation/accessibility screen. The inspected captures are
`visual-evidence/local-gameplay-priorities-17-19.png` and
`visual-evidence/local-accessibility-priority-19.png`. Minecraft closed
normally, both private server units became inactive, and port 25575 was free
on both machines afterward.

## Build and test

Java 21 is required. **During the priority 20 port a stage is mandatory.** A
bare `./gradlew build` fails on purpose, so that a partial build cannot be
mistaken for the full suite:

```bash
./gradlew --no-daemon -PportStage=slice4 test build   # newest passing stage
./gradlew --no-daemon -PportStage=full  test build    # the real gate, still failing
```

On the main NixOS PC, use the declared JDK without installing an imperative
profile:

```bash
task_jdk=$(nix eval --raw nixpkgs#jdk21.outPath)
JAVA_HOME="$task_jdk" PATH="$task_jdk/bin:$PATH" \
  ./gradlew --no-daemon -PportStage=slice4 test build
```

Stages are defined in `gradle/port-manifest.txt`, which is authoritative;
`build.gradle` reads it. `-PportStage=full` is the gate that matters and will
keep failing until the port completes. Run `portStageReport` to see how many
files a stage actually selects.

## Play on the main PC

**The launcher is disabled during the priority 20 port.** The build no longer
compiles the Fabric entrypoints and no NeoForge `@Mod` entrypoint exists yet, so
a launch would start vanilla Minecraft with no Vector-Regnum loaded and look
like it worked. `scripts/local-play.sh` therefore exits with an explanation
rather than starting anything.

For a playable build, use the frozen Fabric alpha in the separate
`vector-regnum-fabric-legacy` checkout at commit `c7371ca`.

When a complete NeoForge slice provides an entrypoint, the guard is removed and
this launcher returns. It runs `scripts/local-play.sh`, which resolves Java 21,
stages only this mod in an isolated flat world on `127.0.0.1:25575`, launches
Minecraft through `steam-run`, and stops the private server when the client
closes. It does not touch the normal Minecraft launcher, saves, modpacks, or
port 25565.

## Hermes development workflow

The current scripts exercise the deprecated Fabric reference. Priority 20 must
create equivalent guarded NeoForge workflows before the port is accepted.

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
/vectorregnum mana attune arcane|fire|frost|void
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

The library IDs are `ember_lance`, `chain_frost`, `gravity_slam`,
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
examples are a floor rather than a closed list. After the NeoForge migration,
roadmap priority 20a adds
[FoundryMC Veil](https://github.com/FoundryMC/Veil) as an optional modular
rendering backend for reusable particles, beams, ribbons, meshes, lights,
framebuffers, and post effects. Veil will consume the existing bounded
presentation program rather than replace its spell grammar. Essential effects
remain scalable, accessible, mechanically truthful, and functional through the
built-in renderer when Veil or a compatible shader path is unavailable.

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

The immediate work is the repository-preserving NeoForge port, followed by the
Veil-backed modular presentation overhaul and compatibility gate before new
gameplay work. The elemental identity model, reagent economy, persistent upkeep
and conclusions, shared-memory branching, explicitly approved cooperative
rituals, security and accessibility hardening, and the optional SMP integration
API follow. Configuration, balancing, profiling, full playtests,
NeoForge/modpack compatibility, final art, localization, and release packaging
come afterward; that release milestone also owns publishing inspected in-game
images to the Regnum Hub.

See [ROADMAP.md](ROADMAP.md) for the detailed status.

## Continuing development

A fresh AI or developer should begin with [AGENTS.md](AGENTS.md). It records
the machine boundaries, canonical priority queue, required NixOS and Hermes
verification ladder, visual-inspection requirement, regression invariants, and
documentation handoff rules. In a new session, asking for "the next unfinished
Vector-Regnum priorities" is sufficient; the numbered queue in
[ROADMAP.md](ROADMAP.md) controls the order. At this handoff, priorities 1–19
are checked as the Fabric legacy alpha and the first unfinished canonical item
is priority 20: finish freezing the published Fabric legacy repository, then
port this active repository's complete verified behavior and testing workflow
to NeoForge. `AGENTS.md` also records Ian's subagent ladder — opencode
deepseekflash first, then Luna max, then Sol xhigh or Opus 5 medium — plus the
delegation rules, so no earlier chat context is required.

---

Inspired by hard science and high fantasy.
