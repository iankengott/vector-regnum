# Priority 20 execution plan: Fabric to NeoForge 1.21.1

Status: proposed, not started. This plan implements canonical roadmap priority
20. It does not change what priority 20 requires; it sequences it.

## Measured baseline

Direct counts from the tree, not estimates:

| Area | Files | Lines | Coupling |
|---|---|---|---|
| `vectorregnum/core` | 85 | 5,391 | zero `net.minecraft`, zero `net.fabricmc` |
| `vectorregnum/fabric` | 110 | 12,905 | 63 files use `net.minecraft`, 29 use Fabric API |
| `src/test` | 47 | 3,342 | 1 file imports Minecraft |

There are no mixins. `fabric.mod.json` declares three entrypoints and no mixin
config. The loader-neutral split the roadmap claims is real: `core` compiles
against plain Java and carries the VM, compiler, cost model, and presentation
IR.

The dominant mechanical cost is mappings. `build.gradle` pins
`net.fabricmc:yarn:1.21.1+build.3`; NeoForge uses Mojang official names, so 94
distinct `net.minecraft` imports need renaming across 63 files.

The dominant risk is not mappings. It is registry freeze, entrypoint splitting,
and event-bus/thread choice, where a wrong answer compiles and then corrupts
state or silently does nothing.

## Verification gates, and why they carry the plan

Subagent D established that the existing suite cannot detect a bad port. All 39
pure-JUnit tests pass with zero blocks, items, block entities, networking, or
commands registered. `ManaProgressionGameTestHarnessTest` passes with no
GameTest registration at all. Registry-size contracts such as
`LibrarySpellIntegrationContractTest:23` assert `size() == 15` against library
metadata, not against anything the loader registered.

After a bad port the only automatic red flags are compile errors in
`PlayerManaBridgeTest` and the 16 `@GameTest` classes.

Every phase below therefore states a gate that fails loudly on a broken port.
Green tests are not a gate.

## Phases

Each phase lists an estimate, the subagent tier from the AGENTS.md ladder, and
its gate. Phases 1 through 3 are strictly ordered. Phases 5 through 9 can
overlap once phase 4 lands, provided no two agents share files.

### Phase 0 — Freeze verification and unknowns (2-3 h, parent)

Priority 20 requires verifying the published legacy snapshot before porting.
`vector-regnum-fabric-legacy` exists, is pushed, and its head is `c7371ca docs:
mark Fabric repository deprecated`. Confirm it builds and passes the full
Fabric ladder, then tag it, so the frozen alpha is reproducible rather than
merely present.

Resolve three unknowns that change later phases:

1. Whether NeoForge 1.21.1 `AttachmentType` supports `copyOnDeath` and codec
   serialization. Subagent A says yes and mechanical; subagent C says no
   equivalent exists. They cannot both be right and phase 5 depends on it.
2. The exact NeoForge and NeoGradle/ModDevGradle versions for 1.21.1.
3. Whether `ServerChunkEvents.CHUNK_GENERATE` has any NeoForge counterpart, or
   whether phase 8 must become a placed feature.

Gate: legacy repo tagged and its build reproduced; all three unknowns answered
in writing with a source, not from memory.

### Phase 1 — Build system and toolchain (3-5 h, deepseekflash then parent)

Replace `fabric-loom` with the NeoForge Gradle plugin, switch to official
mappings, replace `fabric.mod.json` with `neoforge.mods.toml`, and re-point the
`client`/`server` run configs including the loopback 25575 dev port.

Gate: `./gradlew build` succeeds with only `core` and the pure tests on the
source path. The `fabric` package is temporarily excluded. A build that
succeeds because nothing compiles does not pass.

### Phase 2 — Core and pure tests (1-2 h, parent)

`core` has zero loader coupling, so it should compile unchanged. Restore the 39
pure-JUnit tests.

Gate: 39 tests green on the NeoForge toolchain, and `core` compiles with no
edits. Any edit needed here means the loader-neutral claim was wrong and the
plan needs revisiting.

### Phase 3 — Mapping sweep (8-12 h, deepseekflash fan-out)

Rename 94 imports across 63 files using subagent B's table, batched by area so
no two agents share a file. B separates pure find-replace renames from
signature changes; the second group needs per-call edits.

Two traps B identified:

- After renaming `Registries` to `BuiltInRegistries`, the name `Registries`
  becomes valid again in Mojang mappings as a different class holding
  `ResourceKey` constants. An import rewrite can silently resolve to the wrong
  class and still compile.
- `BlockEntity` override names change shape, not just spelling: `readNbt`
  becomes `loadAdditional` and `writeNbt` becomes `saveAdditional` with
  different parameters. A missed override compiles and never runs.

Gate: full compile, plus a grep proving no `net.fabricmc` import survives
outside files the later phases own, plus manual review of every `@Override` on
a persistence method.

### Phase 4 — Registration (6-10 h, deepseekflash with parent review)

Move every static-init `Registry.register` to `DeferredRegister`. NeoForge
freezes registries before mod load completes, so the current pattern throws.
This covers 8 blocks plus 3 conduits and 3 reservoirs, 11 items, 4 block entity
types, 5 creative tab insertions, 3 keybindings, and the `/vectorregnum`
command tree registered from two sites.

Gate: the game launches, `/vectorregnum` tab-completes its full tree, every
item appears in its intended creative tab, and all four block entities place
and persist. Not a test run. Launch it.

### Phase 5 — Persistence and attachments (5-8 h, Luna max)

12 attachments and 4 block entity NBT shapes. Player data carries
`PlayerDataMigration.CURRENT_SCHEMA = 2` and `ClaimLedger` carries its own
schema 2 with a schema-1 decode default of `OWNER_ONLY`. No block entity has a
version field; all four sanitize defensively.

Luna max, not deepseekflash: getting `copyOnDeath` or a decode fallback subtly
wrong corrupts saves without failing a build.

Gate: a world saved by the Fabric alpha loads on NeoForge with mana, capacity,
affinity, attuned source and dimension, channel lock, authored circle,
progression unlocks, guide version, and claims intact. Death copy preserves
mana and clears the transient lock. Schema-1 claims still default to
`OWNER_ONLY`.

### Phase 6 — Networking (5-8 h, Luna max)

7 payloads: 2 C2S and 5 S2C. Move to `RegisterPayloadHandlersEvent` and
`PayloadRegistrar`, convert `PacketCodec` to `StreamCodec`, and split
registration by side. Fabric's `canSend` has no exact per-player equivalent.

Every current handler hops to the main thread via `context.server().execute()`
or `context.client().execute()`. NeoForge handlers also arrive on Netty
threads, so every hop must survive the port. Dropping one lets editor sessions,
Ponder `LATEST`, screens, and presentation state mutate off-thread.

Gate: each of the 7 payloads exercised in a live two-client session, with the
main-thread hop confirmed present at every handler by reading the code, not by
observing that it happened to work once.

### Phase 7 — Events, lifecycle, entrypoints (6-10 h, Luna max then deepseekflash)

The highest-risk structural change. Fabric guarantees the client entrypoint
never runs on a dedicated server. NeoForge runs the `@Mod` constructor on both
sides, so common and client init must be split explicitly or keybindings and
client receivers register on a dedicated server.

Also: 20 `initialize()` calls run in a fixed order so each sees prior statics;
two of them exist only to force static class load. Tick handlers need the right
bus and phase, since the wrong one fires on the mod-loading thread and there
are zero thread assertions anywhere to catch it. `SERVER_STOPPING` must map to
`ServerStoppingEvent`, not `ServerStoppedEvent`, which writes to closed worlds.
`UseItemCallback` has no both-sides NeoForge equivalent and must split while
preserving the SUCCESS-early pattern. `PlayerBlockBreakEvents.BEFORE`, which
`SpellSecurityPolicy` re-enters directly, must become `BlockEvent.BreakEvent`
with a different firing schedule.

Gate: a dedicated server starts with no client class loaded, verified by
inspecting the log for client classloading rather than by absence of a crash. A
player who joins and immediately casts gets migrated data first. Disconnect
cancels a running VM before the next tick.

### Phase 8 — Worldgen (4-6 h, Luna max)

`ServerChunkEvents.CHUNK_GENERATE` injects buried crystal veins during
generation. NeoForge has no raw per-chunk generate event, so this becomes a
placed feature or a chunk-generator hook. That changes the placement pipeline
and seed determinism, and the current code deliberately never requests
neighbor chunks.

Gate: same seed produces the same crystal placement as the Fabric alpha, or a
documented and deliberate difference. Neighbor chunks still never requested.

### Phase 9 — Client, rendering, guide (5-8 h, deepseekflash with parent review)

HUD accessibility layers move to `RenderGuiEvent.Post`, `DrawContext` becomes
`GuiGraphics`, and the written-book screen interception moves to
`ScreenEvent.Init.Post` with different ordering.

One decision to make rather than port blindly: the Field Manual reads its JSON
and recipes from the mod jar classpath at screen-open time with no reload
listener. Moving to `ResourceManager` changes both timing and content, because
datapack overrides would suddenly apply. Keep classpath loading for parity, and
record it.

Gate: Field Manual opens with all three plates, search, history, bookmarks, and
live recipes. Accessibility layers render. The book interception still wins its
race.

### Phase 10 — Test port and the coverage gap (6-10 h, deepseekflash then parent)

Port the 16 `@GameTest` classes from `FabricGameTest` to `@GameTestHolder`.
`TestContext` becomes `GameTestHelper` with renamed methods, annotation
attributes change, and `FabricGameTest.EMPTY_STRUCTURE` has no counterpart.
Port `PlayerManaBridgeTest`, the one directly coupled unit test.

Then close the gap D found. Add at minimum a registration-presence test that
fails when a block, item, block entity type, payload, or command is absent from
the live registry. The current suite cannot tell a working port from an empty
one.

Gate: all 16 GameTests pass on NeoForge, and the new registration test fails
when a registration is deliberately commented out. Verify that failure; a gate
that has never failed is not a gate.

### Phase 11 — Ladder, launcher, mirror (4-6 h, parent)

Rewrite the AGENTS.md verification ladder so every Fabric-specific step has a
NeoForge equivalent, update the NixOS launcher and desktop entry, and re-point
the guarded Hermes workflow. Hermes owns only
`vector-regnum-dev-server.service` and `vector-regnum-dev-client.service`, and
the identity and ownership marker must verify before any sync.

Gate: the full new ladder runs clean from a fresh clone.

## Estimate

| Phase | Estimate |
|---|---|
| 0 Freeze and unknowns | 2-3 h |
| 1 Build system | 3-5 h |
| 2 Core and pure tests | 1-2 h |
| 3 Mapping sweep | 8-12 h |
| 4 Registration | 6-10 h |
| 5 Persistence | 5-8 h |
| 6 Networking | 5-8 h |
| 7 Events and lifecycle | 6-10 h |
| 8 Worldgen | 4-6 h |
| 9 Client and guide | 5-8 h |
| 10 Tests | 6-10 h |
| 11 Ladder and launcher | 4-6 h |
| **Total** | **55-88 h** |

This is higher than the 30-50 h estimated before the inventory. The mapping
work turned out easier than expected because `core` needs no changes at all.
The loader semantics turned out harder, and the test suite cannot verify the
result, so phases 4 through 10 each carry a manual gate that costs real time.

## Ordering constraint

Priority 20a and priority 21 do not begin until this plan completes and the
full NeoForge ladder passes. No new gameplay lands on Fabric.
