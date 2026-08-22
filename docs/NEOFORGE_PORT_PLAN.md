# Priority 20 execution plan: Fabric to NeoForge 1.21.1

Status: complete, 2026-08-20. The priority-20 migration checkpoint passed 172 JUnit tests,
18 production GameTests, live registration parity, and the guarded Hermes
ladder. The first real Main-PC shortcut run found an IPv6 `localhost` mismatch;
explicit `127.0.0.1` fixed it. Ian then passed the human-controlled local
visual gate with `scripts/priority20-local-visual-wizard.sh`, confirming
in-world Vector-Regnum content and rendered items, normal shutdown, an
inactive owned server unit, and a free development port.

Revision 2 incorporates an adversarial review. Two blockers, four high findings,
and two medium findings were confirmed against the tree and fixed below. The
changes that mattered most: world claims needed an explicit persistence-owner
decision, the phase 5 save gate contradicted a canonical decision, and phase 6
had NeoForge threading backwards.

## Measured baseline

Direct counts from the tree, not estimates:

| Area | Files | Lines | Coupling |
|---|---|---|---|
| `vectorregnum/core` | 85 | 5,391 | zero `net.minecraft`, zero `net.fabricmc` |
| `vectorregnum/fabric` | 110 | 12,905 | 63 files use `net.minecraft`, 29 use Fabric API |
| `src/test` | 47 | 3,342 | 1 file imports Minecraft, 32 use `vectorregnum.fabric` |

The suite is 47 test classes and 170 tests, confirmed by a real Gradle run
rather than by grepping for `@Test`. Only 15 test files compile without the
`vectorregnum.fabric` package, which constrains phases 1 and 2.

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

Subagent D established that the existing suite cannot detect a bad port. The
loader-free tests pass with zero blocks, items, block entities, networking, or
commands registered. `ManaProgressionGameTestHarnessTest` passes with no
GameTest registration at all. Registry-size contracts such as
`LibrarySpellIntegrationContractTest:23` assert `size() == 15` against library
metadata, not against anything the loader registered.

After a bad port the only automatic red flags are compile errors in
`PlayerManaBridgeTest` and the 16 `@GameTest` methods, which live in 3 classes.

Every phase below therefore states a gate that fails loudly on a broken port.
Green tests are not a gate.

## Phases

Each phase lists an estimate, the subagent tier from the AGENTS.md ladder, and
its gate. Phases 1 through 3 are strictly ordered. Phases 5 through 9 can
overlap once phase 4 lands, provided no two agents share files.

### Phase 0 — Freeze verification and unknowns (2-4 h, parent)

Priority 20 requires verifying the published legacy snapshot before porting.
`vector-regnum-fabric-legacy` exists, is pushed, and its head is `c7371ca docs:
mark Fabric repository deprecated`. Confirm it builds and passes the full
Fabric ladder, then tag it, so the frozen alpha is reproducible rather than
merely present.

Three unknowns blocked later phases. All three are now answered against
official NeoForged sources rather than from memory.

**1. `AttachmentType` supports both `copyOnDeath` and codec serialization.**
The builder is `AttachmentType.builder(() -> 0).serialize(Codec.INT).build()`,
and `copyOnDeath` is a real builder flag available once a serializer is
configured. Subagent A was right and subagent C was wrong to claim no
equivalent exists.

The pinned NeoForge API also supports level attachments. Claims nevertheless
use explicit per-dimension `SavedData`: immutable ledger replacement, dirty-bit
ownership, malformed-file recovery, and restart reconciliation remain visible
in one adapter. Player state uses serialized, copy-on-death attachments. Item
stacks use vanilla data components; this project attaches nothing to them.
Source: https://docs.neoforged.net/docs/1.21.1/datastorage/attachments/

**2. Pinned versions.** NeoForge `21.1.248`, which is both the latest 21.1
release on the NeoForged Maven and the version in the official 1.21.1 MDK.
Build plugin is ModDevGradle, `net.neoforged.moddev` version `2.0.141`, chosen
over NeoGradle because this project targets a single Minecraft version and
ModDevGradle is the simpler buildscript. Both are fully endorsed upstream.
Optional Parchment mappings `2024.11.17` for `1.21.1`.
Sources: https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle,
https://neoforged.net/news/moddevgradle2/

**3. `ServerChunkEvents.CHUNK_GENERATE` has no NeoForge counterpart.** There is
no per-chunk generation event. The supported mechanism is a `PlacedFeature`
injected through a biome modifier JSON at
`data/vector_regnum/neoforge/biome_modifier/<name>.json`, targeting a biome id
or tag at a `GenerationStep.Decoration` step, with ores and veins using the
ores step. Phase 8 is therefore a rewrite of the placement path, not a hook
swap, and its seed determinism must be re-proved rather than assumed.
Source: https://docs.neoforged.net/docs/1.21.1/worldgen/biomemodifier/

**Status: complete except one item, 2026-08-20.**

The legacy repository is clean and synchronized with `origin/main` at `c7371ca`.
Its build was reproduced on this machine with Java 21:
`./gradlew --no-daemon clean test build` succeeded, running 47 test classes and
170 tests with zero failures, errors, or skips. All resource JSON parses, all
shell scripts parse, and `git diff --check` is clean.

Ladder steps 2 and 3, Hermes integration and a real client launch, were
deliberately not run. They are gates for gameplay and visual work; rerunning
them against an already-verified deprecated repository would touch guarded
Hermes services without producing new information.

An annotated tag `v0.1.0-fabric-alpha` was created at `c7371ca` recording that
verification. It could not be pushed: the GitHub repository is **archived and
read-only**, and the push returned 403. Archiving is a stronger freeze than a
tag, so the intent of this gate is met, but the remote carries no tag.

Decision, 2026-08-20: the archived read-only state is accepted as the
authoritative freeze. The repository cannot receive commits or tags, which is a
stronger guarantee than a tag anyone with push access could move. The annotated
tag stays in the local checkout as a record of the verification run. The
authoritative reference to the frozen alpha is the commit hash `c7371ca`.

Gate: passed. Build reproduced, all three unknowns answered from cited sources,
freeze established by GitHub archival at `c7371ca`.

### Phase 1 — Build system, toolchain, GameTest infrastructure (5-8 h, deepseekflash then parent)

Replace `fabric-loom` with ModDevGradle `net.neoforged.moddev` `2.0.141`
targeting NeoForge `21.1.248`, switch to official mappings, replace
`fabric.mod.json` with `neoforge.mods.toml`, and re-point the `client` and
`server` run configs including the loopback 25575 dev port.

Stand up the GameTest infrastructure now rather than at phase 10, because
phase 10 cannot verify anything without it: an empty structure template, the
registration namespace, and a `gameTestServer` run configuration.
`runGameTestServer` supplies the process exit code the ladder needs.

The template replaces `FabricGameTest.EMPTY_STRUCTURE`, which has no NeoForge
counterpart. The existing tests place blocks no further out than coordinate 3,
so an 8x8x8 template with a solid floor at y=0 covers them, and the tests'
repeated use of `new BlockPos(2, 1, 2)` confirms they expect to work one block
above a floor.

Write a checked-in source manifest naming exactly which sources compile at each
intermediate gate. Without it the phase 1 through 3 gates are unfalsifiable.

Gate: `./gradlew build` succeeds against the manifest's core-only source set,
which is `core` plus the 15 test files that do not reference
`vectorregnum.fabric`. The other 32 test files are excluded by the manifest, not
by accident. A build that succeeds because nothing compiles does not pass.

**Status: passed, 2026-08-20.** `BUILD SUCCESSFUL in 1m 53s` on NeoForge
`21.1.248` with ModDevGradle `2.0.141`. `portStageReport` confirms the build saw
85 main files and 15 test files, and 158 class files were produced, so it did
not pass by compiling nothing. `-PportStage=bogus` fails with
`port-manifest.txt defines no main.include for stage 'bogus'`, so the manifest
gate has been observed to fail rather than merely assumed to work.

`pack.mcmeta` was investigated and deliberately not added; the official 1.21.1
MDK ships without one and NeoForge derives pack metadata from
`neoforge.mods.toml`. The existing data directories already use 1.21 singular
names (`recipe`, `loot_table`, `advancement`), so no renaming was needed.

### Phase 2 — Core and pure tests (1-2 h, parent)

`core` has zero loader coupling, so it should compile unchanged. Restore the 15
test files that need no `vectorregnum.fabric` class.

The other 32 test files, including `ManaStorageTest` which tests
`vectorregnum.fabric.progression.ManaStorage`, come back during the vertical
slices in phase 3, not here.

Gate: the 15 loader-free test files green on the NeoForge toolchain, and `core`
compiles with no edits. Any edit needed here means the loader-neutral claim was
wrong and the plan needs revisiting.

**Status: passed, 2026-08-20.** 15 test classes, 83 tests, zero failures, errors
or skips. `git status` on `src/main/java/vectorregnum/core` reports zero changed
files, so all 85 core files compiled against NeoForge and Mojang official
mappings without a single edit.

This is the plan's central assumption confirmed rather than assumed: the
loader-neutral split is real, and roughly a quarter of the codebase needed no
port at all.

### Phase 3 — Mapping sweep, vertical slices, package rename (10-16 h, deepseekflash fan-out)

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

**Historical checkpoint, superseded:** after slice 4, 4 of about 9 mapping
slices were complete. The port later completed and passed the full ladder. Use
the numbered queue in `ROADMAP.md`, not this checkpoint, to choose current
work.

| Slice | Commit | Scope | Result |
|---|---|---|---|
| 1 | `e07a57c` | Package rename to `vectorregnum.neoforge`, 42 transitively clean classes | 144 tests |
| 2 | `2314d69` | `ManaAffinity`, `ManaCrystalGeology`, `AutomationDataBridge` | 149 tests |
| 3 | `4cbac5e` | 7 payload records to `StreamCodec` | 149 tests |
| 4 | `9bb16c7` | 3 temporary-effect blocks | 149 tests |

At that checkpoint, 26 classes and 3,747 lines remained, grouped as follows:
progression 9 classes / 923 lines (the only group that still recovers tests,
gating 5 of the 9 excluded test classes behind `ManaReservoir`,
`ManaTransportRules` and `ManaDrawRules`), root 7 / 843 including the block
entities, guide 2 / 559, editor 1 / 465, ponder 3 / 422, automation 3 / 388,
presentation 1 / 147.

The next action at that historical checkpoint was slice 5, the progression
cluster. It is recorded here as execution history, not unfinished work.

Two lessons from slices 1-4, both earned the hard way:

- Give the subagent explicit method *signatures*, not just name pairs. Slice 3
  used a name table and needed hand fixes; slice 4 used full signatures and
  returned correct.
- A rename table cannot express an argument-order change. `StreamCodec.of`
  takes `(buffer, value)` where Yarn's `PacketCodec.of` took `(value, buffer)`,
  so a method reference binds backwards and fails type inference. Slice 3 also
  proved the agent will confidently assert a wrong rename: it reported
  `readUuid`/`writeUuid` as unchanged when Mojang uses `readUUID`/`writeUUID`.
  Compile every slice; never trust the summary.

Port in vertical slices that compile completely, rather than sweeping all 63
files and hoping for a full compile. A "full compile" is impossible while 29
files still import Fabric APIs owned by phases 4 through 9, so each slice
carries its own Fabric API replacements or it is not a slice.

Also rename the package. The tree has 140 `vectorregnum.fabric` package
declarations and 250 references. Leaving them shipped means the NeoForge
implementation lives under `vectorregnum.fabric` and the VM service is called
`FabricVmService`. Rename to `vectorregnum.neoforge` across main sources, tests,
entrypoints, and class names.

Gate: every slice compiles on its own with its tests restored, and a search
returns zero active references to `vectorregnum.fabric`, `net.fabricmc`,
`FabricVmService`, or `fabric.mod.json`. Plus manual review of every `@Override`
on a persistence method.

### Phase 4 — Registration (7-11 h, deepseekflash with parent review)

Two different mechanisms, kept separate.

Static registries move to `DeferredRegister`, because NeoForge freezes
registries before mod load completes and the current static-init pattern
throws. The tracked resources prove **17 block IDs and 13 item IDs**, not the
14 and 11 an earlier revision claimed, plus 4 block entity types.

Everything else is event-driven and does not touch `DeferredRegister`:
key mappings via `RegisterKeyMappingsEvent`, creative tab additions via
`BuildCreativeModeTabContentsEvent`, and commands via `RegisterCommandsEvent`.
The `/vectorregnum` root is registered from two sites and Brigadier merges the
children, so both must move together.

Produce a checked-in parity manifest listing every expected registry ID,
attachment ID, payload ID, creative-tab membership, and command root. It is the
input to the phase 10 registration test.

Gate: the game launches, `/vectorregnum` tab-completes its full tree, every
item appears in its intended creative tab, and all four block entities place
and persist. The live registry matches the parity manifest exactly, with no
missing and no extra IDs. Not a test run. Launch it.

### Phase 5 — Persistence, attachments, SavedData (8-12 h, Luna max)

11 of the 12 attachments are player data and port to NeoForge `AttachmentType`,
which phase 0 confirmed supports both `serialize(Codec)` and `copyOnDeath`. That
part is mechanical. The twelfth is not.

`WORLD_CLAIMS` is attached to the world, not to a player, via
`world.setAttached(WORLD_CLAIMS, ...)` at `MultiplayerLifecycleService.java:52`,
`:99`, and `:111`. NeoForge 1.21.1 attachments support entities, block entities,
chunks, and item stacks, but not levels; level data belongs in `SavedData`.
Porting `ClaimLedger` as an ordinary attachment is not possible.

Move `ClaimLedger` to versioned `SavedData` with explicit dirty marking and
per-dimension ownership. It carries schema 2 with a schema-1 decode default of
`OWNER_ONLY`, and that migration must survive the move.

Player data carries `PlayerDataMigration.CURRENT_SCHEMA = 2`. No block entity
has a version field; all four sanitize defensively.

Luna max, not deepseekflash: getting `copyOnDeath` or a decode fallback subtly
wrong corrupts saves without failing a build.

There is deliberately no Fabric-world gate here. `SMP_INTEGRATION_DECISIONS.md`
line 21 records the canonical decision that no save-compatibility requirement
exists between the unreleased Fabric alpha and NeoForge. An earlier revision of
this plan required loading a Fabric-alpha world, which contradicted that
decision. If Fabric world importing is ever wanted it needs its own roadmap
entry and a migration reader, not a smuggled-in gate.

Gate, all NeoForge-native: a NeoForge world saves and reloads with mana,
capacity, affinity, attuned source and dimension, channel lock, authored
circle, progression unlocks, guide version, and claims intact. Death copy
preserves mana and clears the transient lock. A hand-built schema-1 claim
fixture still migrates to `OWNER_ONLY`. Corrupt payloads still hit their
defensive fallbacks. All 4 block entities round-trip. Claims survive a full
server restart.

### Phase 6 — Networking (5-8 h, Luna max)

7 payloads: 2 C2S and 5 S2C. Move to `RegisterPayloadHandlersEvent` and
`PayloadRegistrar`, convert `PacketCodec` to `StreamCodec`, and split
registration by side. Fabric's `canSend` has no exact per-player equivalent.

The threading direction here is the opposite of what an earlier revision said.
NeoForge 1.21.1 invokes payload handlers on the main thread by default; running
on the network thread is opt-in through `executesOn(HandlerThread.NETWORK)`.

So the Fabric `context.server().execute()` and `context.client().execute()`
hops are not something to preserve. They become redundant. All 7 payloads
mutate state, so all 7 take the default main-thread handler and the hops are
removed rather than translated. Only decode-only work would justify the network
thread, and then it must return through `context.enqueueWork` with the future's
exceptions handled.

Check the `canSend` claim rather than inheriting it: verify whether
`player.connection.hasChannel(...)` covers it in the pinned NeoForge version
before writing a replacement.

Gate: each of the 7 payloads exercised in a live two-client session, plus a
read of every handler confirming it runs on the main thread and carries no
leftover redundant hop.

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
generation. Phase 0 confirmed NeoForge has no per-chunk generate event at all,
so this becomes a custom `Feature` plus a `PlacedFeature`, injected by a biome
modifier JSON at `data/vector_regnum/neoforge/biome_modifier/<name>.json` at the
ores decoration step.

That moves placement from mod code into the worldgen pipeline, which changes
when it runs and how it is seeded. The current code deliberately never requests
neighbor chunks, and a feature must keep that property.

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

Port the 16 legacy `@GameTest` methods, which live in 3 classes and not 16, from
`FabricGameTest` to `@GameTestHolder`. `TestContext` becomes `GameTestHelper`
with renamed methods, and annotation attributes change. The structure template,
namespace, and `gameTestServer` run configuration were built in phase 1, so
this phase ports tests rather than standing up infrastructure.

Port `PlayerManaBridgeTest`, the one directly coupled unit test.

Then close the gap D found. Add a registration-presence test that reads the
phase 4 parity manifest and fails when any expected registry ID, attachment ID,
payload ID, creative-tab membership, or command root is missing from the live
game. The current suite cannot tell a working port from an empty one.

Gate: all 18 GameTest methods (the 16 ports, live registration parity, and the
semantic follow-up-VM regression) pass
on NeoForge through `runGameTestServer` with a real process exit code, and the
new registration test fails when a registration is deliberately broken. This
was proved by temporarily renaming the live `sigil_tome` ID: parity was the
sole required failure, the mutation was restored, and two consecutive
persisted-world runs then passed all 17 tests. A final audit then found that a
semantic impulse could append a follow-up VM during active iteration; deferred
queueing and a real Vector Step GameTest raised the matrix to 18 tests.

### Phase 11 — Ladder, launcher, mirror, handoff (6-9 h, parent)

Rewrite the AGENTS.md verification ladder so every Fabric-specific step has a
NeoForge equivalent, update the NixOS launcher and desktop entry, and re-point
the guarded Hermes workflow. Hermes owns only
`vector-regnum-dev-server.service` and `vector-regnum-dev-client.service`, and
the identity and ownership marker must verify before any sync.

The "keeping the handoff current" rules at `AGENTS.md:189` require more than
that, and all of it lands here: update `ROADMAP.md` and the confirmed and
unfinished sections of `README.md`, update `scripts/README.md` because test and
launch behavior changes, update the Vector-Regnum project memory and project hub
in the Obsidian vault on the main PC, and record the new automated test counts
and latest inspected visual evidence.

Then sweep for stale claims. The docs currently assert Fabric-specific facts in
many places, and a port that leaves them reads as a lie to the next agent.

Gate: the full new ladder runs clean from a fresh clone, and a search for
"Fabric", "loom", and "yarn" across the docs returns only deliberate historical
references.

## Estimate

| Phase | Estimate |
|---|---|
| 0 Freeze and unknowns | 2-4 h |
| 1 Build system and GameTest infrastructure | 5-8 h |
| 2 Core and loader-free tests | 1-2 h |
| 3 Mapping, slices, package rename | 10-16 h |
| 4 Registration | 7-11 h |
| 5 Persistence and SavedData | 8-12 h |
| 6 Networking | 5-8 h |
| 7 Events and lifecycle | 6-10 h |
| 8 Worldgen | 4-6 h |
| 9 Client and guide | 5-8 h |
| 10 Tests | 6-10 h |
| 11 Ladder, launcher, handoff | 6-9 h |
| **Total** | **65-104 h** |

The estimate has moved twice. It was 30-50 h before the inventory, 55-88 h
after it, and 65-104 h after the adversarial review. Each revision found work
that was real rather than speculative: the package rename, `ClaimLedger` moving
to `SavedData`, the parity manifest, and the GameTest infrastructure.

`core` still needs no changes at all, so the mapping work remains the easy half.
The loader semantics are the hard half, and the existing suite cannot verify
them, so phases 4 through 10 each carry a manual gate that costs real time.

## Ordering constraint

This constraint is satisfied: the plan and full NeoForge ladder passed.
Priority 20a's initial optional Veil and target-pack gates passed on 2026-08-20,
and its complete Veil ownership migration and repeated verification ladder
passed on 2026-08-21. The final Main-PC attestation exercised authored and
library casts, F3+T reload, accessible low LOD, and normal cleanup on the final
artifact. Priority 20a is checked and priority 21 is next. No new gameplay lands
on Fabric.
