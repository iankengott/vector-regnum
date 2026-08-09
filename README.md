# Vector-Regnum

*The Realm of Direction* is a Fabric 1.21.1 programming-magic mod in active
development. Players arrange sigils on geometric circles, read clockwise around
the outer ring and then inward. Valid circles become server-authoritative world
effects; invalid programs fault at an exact physical sigil and may collapse into
Wild Magic.

The current build is a substantial playable alpha, not a finished release. It
implements the first working pass of the project's priorities 1–10: typed/ticked
execution, authoring and diagnostics, safety-bounded control flow, perception,
physics, cost accounting, three spell media, finite crystal progression, and a
15-spell library.

## Confirmed working

- Fabric Loader 0.18.3, Fabric API 0.116.7+1.21.1, Yarn
  1.21.1+build.3, Loom 1.14.8, Gradle 9.2.1, and Java 21.
- **73 passing JUnit tests** covering the compatibility engine, typed VM,
  circle authoring/persistence, media lifecycles, cost model, crystal rules,
  progression, and the spell-library integration contract.
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
  guidance, and a versioned first-join Field Manual v2.
- Fifteen playable spells across combat, defense, movement, utility,
  detection, and automation. Their semantic programs are quoted through the
  same vm2 cost dimensions; Vector Step and Kinetic Ward use live vm2 physics.
- Temporary Mage Light and Redstone Oracle blocks remove themselves through
  persisted scheduled block ticks, including across a normal server restart.
- The original Sigil Tome, Firebolt, Frost Nova, collision/damage/status
  effects, mana starvation, and context-sensitive Wild Magic remain available.

The full workflow has been exercised on Hermes: remote Java 21 tests/build,
loopback-only server, quick-joined Minecraft client, automated in-game
preflight, and direct screenshot inspection of the typed eight-sigil circle,
mana crystal, spell particles, compiler cost output, and bound media.

## Build and test

Java 21 is required:

```bash
./gradlew --no-daemon test build
```

On the main NixOS PC, use the declared JDK without installing an imperative
profile:

```bash
task_jdk=$(nix eval --raw nixpkgs#jdk21.outPath)
JAVA_HOME="$task_jdk" PATH="$task_jdk/bin:$PATH" ./gradlew --no-daemon test build
```

## Play on the main PC

Double-click **Play Vector-Regnum** on the desktop. The shortcut runs
`scripts/local-play.sh`, which resolves Java 21, stages only this mod in an
isolated flat world on `127.0.0.1:25575`, launches Minecraft through
`steam-run`, and stops the private server when the client closes. It does not
touch the normal Minecraft launcher, saves, modpacks, or port 25565.

## Hermes development workflow

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

## Still not finished

The largest remaining systems are a graphical in-world editor, natural crystal
world generation and transport infrastructure, creation/form opcodes,
Create-style spell **Ponders**, generic lowering of every curated semantic
opcode instead of some purpose-built Fabric effects, dedicated GameTests,
multiplayer claim integrations beyond standard Fabric break callbacks,
redstone/remote/multithread/data-bridge expansion, original art/audio,
balancing/configuration/accessibility, and release packaging.

See [ROADMAP.md](ROADMAP.md) for the detailed status.

## Continuing development

A fresh AI or developer should begin with [AGENTS.md](AGENTS.md). It records
the machine boundaries, canonical priority queue, required NixOS and Hermes
verification ladder, visual-inspection requirement, regression invariants, and
documentation handoff rules. In a new session, asking for "the next unfinished
Vector-Regnum priorities" is sufficient; the numbered queue in
[ROADMAP.md](ROADMAP.md) controls the order.

---

Inspired by hard science and high fantasy.
