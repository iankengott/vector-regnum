# Vector-Regnum

*The Realm of Direction* is a Fabric 1.21.1 programming-magic mod in active
development. Spells are ordered sigil programs: valid programs become
server-authoritative world effects, while invalid programs fail at a precise
source sigil and can trigger dangerous Wild Magic.

The current release is a playable vertical slice, not the finished magic
system. It proves the full path from sigils to a real Minecraft cast while the
larger typed, tick-driven spell VM is built out.

## Confirmed working

- Fabric Loader 0.18.3, Fabric API 0.116.7+1.21.1, Yarn
  1.21.1+build.3, Loom 1.14.8, Gradle 9.2.1, and Java 21.
- A Minecraft-independent compiler/runtime under `vectorregnum.core` and a
  Fabric adapter under `vectorregnum.fabric`.
- Nine original prototype scenarios preserved as assertion-based compatibility
  fixtures, plus hardening tests for malformed programs and immutability.
  There are currently 25 passing JUnit tests.
- Exact source-index faults, mandatory terminal `EXECUTE`, finite positive
  modifiers, dynamic non-zero caster look vectors, supported element/shape
  validation, and deterministic Wild Magic selection.
- A real **Sigil Tome** item. Right-click casts Firebolt with a server-enforced
  cooldown.
- Persistent, server-authoritative mana. Players start with 500 mana, casts
  debit it, invalid/non-finite values are rejected, and there is intentionally
  no passive regeneration.
- Firebolt projectile collision, damage, fire/frost payloads, Frost Nova's
  radial aura, and three context-sensitive Wild Magic effect families.
- An animated magic circle/pentagram used by the development visual checkpoint.
- Development commands for inspecting mana and exercising preset spells.

The dedicated Hermes workflow has also been exercised end-to-end: remote tests
and build pass under Java 21, a loopback-only server loads the mod, the client
quick-joins it, and the actual magic circle, flame sigil, checkpoint text, and
Sigil Tome have been visually inspected in the live game window.

## Build and test

Java 21 is required:

```bash
./gradlew --no-daemon test build
```

The remapped mod JAR is written to `build/libs/`. On the main NixOS PC, where
the ambient shell may still expose Java 8, use the declared JDK without
installing an imperative profile:

```bash
task_jdk=$(nix eval --raw nixpkgs#jdk21.outPath)
JAVA_HOME="$task_jdk" PATH="$task_jdk/bin:$PATH" ./gradlew --no-daemon test build
```

## Playing the development slice

### Main PC: one click

Double-click **Play Vector-Regnum** on the desktop. The shortcut runs
`scripts/local-play.sh`, which:

- resolves the declared Java 21 without changing the user's Nix profile;
- rejects unexpected JARs in the client or server `mods/` directories;
- stages the isolated flat world on loopback port 25575;
- waits for the private server and starts Minecraft through `steam-run` so
  NixOS graphics drivers are available;
- quick-plays the Vector-Regnum world; and
- stops and collects the private server when Minecraft closes.

The normal Minecraft launcher, saves, and modpacks are not involved. A second
click while the Vector-Regnum world is already active is rejected rather than
starting a competing server.

### Hermes

The checked-in Hermes workflow provides the remote equivalent:

```bash
scripts/hermes-sync.sh
scripts/hermes-build.sh
scripts/hermes-client.sh restart
scripts/hermes-client.sh logs
scripts/hermes-screenshot.sh window
scripts/hermes-client.sh stop
```

It uses only
`ian-kengott@100.88.229.63:/home/ian-kengott/projects/vector-regnum`, owns two
transient user services named `vector-regnum-dev-server.service` and
`vector-regnum-dev-client.service`, and binds the test server to
`127.0.0.1:25575`. It does not touch port 25565, existing Minecraft tmux
sessions, or the normal Hermes launcher. See [scripts/README.md](scripts/README.md)
for the safety checks and recovery commands.

In a development environment, joining the test server automatically gives the
player a Sigil Tome and stages a 60-second visual checkpoint. The automation is
guarded by Fabric's development-environment flag and is absent from normal
release behavior.

Useful in-game commands:

```text
/vectorregnum
/vectorregnum cast firebolt
/vectorregnum cast frost_nova
/vectorregnum cast amplified_firebolt
/vectorregnum mana
```

The following development/admin commands require permission level 2:

```text
/vectorregnum mana refill
/vectorregnum give_tome
/vectorregnum showcase
/vectorregnum miscast internal|unstructured|violent
```

## Current compatibility sigils

- `ORIGIN_SELF` grounds the spell at the caster and must come first.
- `ELEMENT_<name>` selects `FIRE`, `FROST`, `ARCANE`, or `VOID`.
- `VECTOR_FORWARD` resolves the caster's live look vector at cast time.
- `SHAPE_<name>` selects `PROJECTILE` or `AURA`.
- `EXPAND <number>` grows a resolved shape's radius.
- `AMPLIFY <number>` multiplies magnitude after an element or shape exists.
- `EXECUTE` must be the final sigil and materializes an effect command.

Unknown sigils are compile faults. Invalid order, missing state, non-finite or
non-positive modifiers, zero direction vectors, and instructions after
`EXECUTE` all fail explicitly instead of silently producing an effect.

## Architecture

```text
List<Sigil>
    -> SpellCompiler
    -> immutable CompiledSpell
    -> SpellEngine + CastContext
    -> CastResult
    -> EffectCommand
    -> Fabric world-effect adapter
```

The core has no `net.minecraft` imports, which keeps spell semantics fast to
test and lets Minecraft remain a thin server-authoritative execution boundary.
Engine failures are distinguished from player-authored spell failures so an
internal bug cannot masquerade as intended Wild Magic.

## Design direction

The intended system is still much larger: geometric circles read clockwise and
inward, typed stack memory (`Push`/`Pop`), points/vectors/entities as runtime
values, tick/delay/duration control, logic gates and branches, bounded loops,
raycasts and selection, physics operations, redstone/remote activation,
multithreaded circles, data bridges, and stone/scroll/book implementation
methods. Mana remains a finite extracted resource rather than a regenerating
bar, and costs should emerge from physical work, range, rarity, memory, and
control-flow complexity.

The next architectural milestone is the typed ticked stack VM and its first
player-authored circle representation. The seven compatibility sigils remain a
useful vertical-slice frontend, but they are not the final language.

---

Inspired by hard science and high fantasy.
