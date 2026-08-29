# Vector-Regnum roadmap

> **Platform transition:** priorities 1–19 describe the verified but deprecated
> Fabric 1.21.1 alpha. NeoForge 1.21.1 is now the active target. Priority 20
> freezes the Fabric line in its own legacy repository and establishes the
> separate NeoForge repository before any new gameplay work.

The priorities 1–10 milestone is implemented as a playable alpha. A checked item
means its first coherent end-to-end pass exists; it does not mean final balance,
art, UX, or production hardening is complete.

## Working foundations

- [x] NeoForge 1.21.1 build, dedicated server, guarded Hermes workflow, and
  one-click local launch; the Fabric alpha remains frozen separately.
- [x] Compatibility compiler/runtime with exact source faults and Wild Magic.
- [x] Persistent tutorial guide, Sigil Tome, Firebolt, Ice Nova, effects,
  collision, cooldowns, and finite server-authoritative mana.
- [x] 262 JUnit/contract tests, 30 production NeoForge GameTests, plus real
  Hermes server/client boots and direct visual inspection.

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

11. [x] Create-style spell/scroll **Ponders**: an in-world warded-scribe
    workshop animates authoritative compilation order, execution, mana flow,
    effects, exact faults, and every Wild Magic category. Bounded live VM
    snapshots, explicit subscriptions, long-trace compaction, replay/scrubbing,
    Primer/Trace views, and authored success/failure scenes are complete.
12. [x] Replace the vanilla Field Manual with a Vector-Regnum-themed visual
    guidebook: illustrated diagrams, recipes and examples; searchable chapter
    navigation; contextual item/block links; progression-aware entries; and
    readable, accessible scaling. Prototype a native screen against maintained
    Fabric 1.21.1 guidebook libraries before choosing the implementation.
    The selected native backend is data-driven; its current v7 content adds three original
    illustrated plates, scrolling and scrollbars, search/history/bookmarks,
    scaling and compact layouts, tooltips/alt text, contextual links, unlock
    gating, live recipes with bounded tag/alternative cycling, item icons, and
    client book interception. The documented comparison retains Patchouli and
    GuideME as authoring/interaction inspiration without adding a dependency.
13. [x] Graphical in-world circle editor with a discoverable sigil palette,
    parameter editing, diagnostics, and media binding without command fluency.
    Its responsive native UI provides searchable typed sigils, drag placement
    and movement with parameter preservation, safe removal and one-step undo,
    quoted parameter editing, diagnostics, compilation/media binding, and a
    server-only raycast that captures a validated fixed dimension/block/face
    anchor without trusting client coordinates.
14. [x] Natural mana-crystal world generation plus balanced geology, source
    growth/recharge, transport, and storage progression. Three persistent
    storage tiers and matching conduit tiers provide bounded loaded-chunk
    routing, atomic restart-safe transfer, player draw/tuning interactions,
    comparator output, recipes, upgrades, loot, and balance envelopes.
15. [x] Fabric GameTests for commands, attachments, media/block-entity round
    trips, crystals, scheduled effects, restart behavior, and multiplayer.
    Sixteen production tests cover the real command parser, two-player
    isolation, attachment/item/block-entity codecs, verified tablet anchors,
    crystals, actual scheduled expiry, persisted tick-queue reload contracts,
    multiplayer claims/death migration, and programmable-relay behavior.
16. [x] Complete the spell language: static stack-type analysis,
    creation/form opcodes, and generic lowering/execution for every curated
    semantic opcode. All 30 library opcodes map losslessly to the 32-opcode
    semantic vocabulary and one authoritative opcode-driven server backend;
    authored `VM_CREATE_FORM` programs execute bounded permission-checked forms
    with persisted cleanup, exact diagnostics, complete mana quotes, and VM
    presentation events.
17. [x] Formal multiplayer lifecycle and security: active spells now fail
    closed on disconnect, death/copy, dimension changes, and owner-chunk
    unload; private/team chunk claims compose with vanilla/Fabric protection
    and PvP/friendly-fire policy; bounded start/concurrency admission resists
    abuse; and versioned player/claim data migrates corrupt or older state.
18. [x] Expand programmable automation with a persistent Automation Relay:
    rising/falling/change/while-high redstone rules, owner-only remote
    activation, bounded read-only data bridges and comparator output, a
    1,024-global/32-owner/16-per-tick queue, immutable messages, isolated VMs,
    and explicit many-producer/single-server-thread ownership rules.
19. [x] Replace placeholder presentation with the compiler-driven, deeply
    layered sensory system specified in `SPELL_PRESENTATION.md`: original
    deterministic procedural geometry, particles, glow/contrast/haze and
    compatible screen treatment, spatial audio, material response, aftermath,
    near/mid/far/telegraph-only LODs, localized UI, and independent accessible
    sensory controls. Essential telegraphs do not depend on shaderpacks;
    signature depth-aware shaders, including the planned Veil-backed
    compositor, and final bespoke art remain optional release-quality
    enhancement rather than a gameplay dependency.
20. [x] **NeoForge 1.21.1 migration and repository split.** The archived Fabric
    alpha is fixed at `c7371ca`; the active repository now uses NeoForge
    21.1.248 and ModDevGradle with Mojang/Parchment mappings. Registrations,
    attachments and claim SavedData, seven payloads, lifecycle/events,
    custom-feature worldgen, client rendering/guide, commands, guarded NixOS
    and Hermes launchers, the 172-test migration checkpoint, and 18 live
    NeoForge GameTests passed their gates. The manifest-driven parity test
    queries the running
    registries, payload directions, attachments, creative tab, and command
    dispatcher and was proved by an intentional negative control. Behavioral
    parity, hard safety limits, the loader-neutral presentation IR, and the
    built-in client renderer remain intact, so dedicated-server startup and
    baseline telegraphs do not depend on Veil.
    The final human-controlled Main-PC desktop-launch gate passed on
    2026-08-20: the client joined `127.0.0.1:25575`, rendered Vector-Regnum
    content and items in-world, closed normally, and left the owned server
    inactive with the development port free.
20a. [x] **Veil-backed modular presentation overhaul and compatibility gate.**
    After priority 20 establishes the NeoForge baseline—and before priority 21
    gameplay work—add [FoundryMC Veil](https://github.com/FoundryMC/Veil) as an
    optional client rendering backend beneath the existing bounded
    `PresentationProgram`. Map semantic parameters such as origin, target,
    normal, radius, element, magnitude, duration, and deterministic seed into a
    curated reusable vocabulary of particles, beams, ribbons, trails, runes,
    animated meshes, surfaces, volumes, deferred lights, framebuffers, and
    optional post-processing. Player-authored spells may select only bounded
    modules and parameters; they cannot upload arbitrary GLSL, Veil JSON,
    assets, packets, or executable code. The built-in renderer remains the
    mandatory fallback and must preserve mechanics-derived truth telegraphs
    when Veil is absent, fails to initialize, post-processing is disabled, a
    shaderpack is incompatible, or accessibility/LOD settings remove expressive
    layers. Pin an exact tested Minecraft 1.21.1 Veil version during
    implementation, then verify Veil present/absent, dedicated-server
    classloading, resource reload, low LOD, reduced-motion/photosensitivity,
    representative library and player-authored spells, and the target SMP
    renderer/Create compatibility matrix. Veil 4.4.1 is pinned as an optional
    client dependency and isolated behind one reflectively loaded adapter. The
    semantic mapper exposes the complete curated bounded module vocabulary.
    Every mapped visual geometry family receives a capped Quasar motif; capped
    deferred lights and optional bloom add more depth when the renderer supports
    them without replacing the built-in truth renderer. The default runtime
    and dedicated GameTest server load without Veil; backend faults fail closed.
    The initial adapter passed its then-current 197-test JUnit suite, 18
    GameTests, resource reload, minimal LOD,
    reduced-motion/photosensitive controls, authored and library casts, guarded
    Veil-present/absent Hermes clients, and the exact Veil/Create/Sodium/Iris/
    Bliss target-pack matrix passed. That matrix stages and visibly renders a
    Create mechanical press and cogwheel. Iris compatibility mode disables
    Veil deferred lights and bloom while retaining Quasar and mandatory truth
    cues; this removed repeated invalid instanced draws. Ian repeated the
    Main-PC desktop gate after those fixes on 2026-08-20. The client exercised
    authored and library spells, F3+T reload, and accessibility fallbacks, then
    closed normally with the owned unit absent and port 25575 free.
    This initial adapter and its gates were not enough to complete the priority.
    The 2026-08-21 migration finished that work: every Vector-Regnum
    particle-emission path was inventoried and migrated so servers emit only
    compact `presentation_trace`/`circle_preview` payloads through the budgeted
    `ServerTraces` choke point, and each client renders them through its active
    backend. With Veil active, Quasar motifs (19 authored emitters: element
    motes, torus rings, cylinder beams, bursts, sparks, smoke, light) own every
    Vector-Regnum particle-based animation; built-in vanilla particles are
    emitted only through one guarded choke point whose runtime allowlist admits
    exactly `ParticleTypes.ENCHANT`, and remain fully available for the
    Veil-absent or failed fallback. Enforcement is automated:
    `ParticleAllowlistSourceScanTest` proves no ungated emission or stray
    `ParticleTypes` reference exists, `VanillaParticleAllowlistTest` proves the
    active-backend allowlist behavior, `PresentationTracePayloadTest` covers
    wire bounds and cue synthesis, and `QuasarEmitterVocabularyTest` proves
    every requestable motif exists and stays capped. The full ladder passed:
    212 JUnit tests, all 18 GameTests on the real server, JSON/script checks,
    `verify-priority20a.sh`, guarded Hermes clients with Veil present (all 19
    motifs loaded, checkpoint staged, live capture inspected) and absent
    (built-in backend, checkpoint staged), and both dev units stopped with port
    25575 free. Ian then passed the human-controlled Main-PC desktop attestation
    through `scripts/priority20-local-visual-wizard.sh` on the final artifact:
    authored and library casts, F3+T reload, minimal LOD, reduced motion,
    photosensitive mode, mandatory truth cues, normal shutdown, unloaded owned
    unit, and free port 25575 all passed.
21. [x] **Elemental identity and affinity expansion.** Replace Frost with Ice;
    add Water, Air, Earth, Lightning, Time, Space, Light, Dark, Nature, Sound,
    and rare Void around the ordinary twelve; retain Arcane only as neutral raw
    mana. Every character receives exactly one permanent natural element while
    channel attunement remains mutable. Use a data-driven symmetric affinity
    matrix with 100/75/50/25% efficiency bands and a 25% opposed floor. The
    schema-3 implementation gives each player one server-authored permanent
    natural element and a separate mutable channel, migrates legacy Frost data
    and block states to Ice, and drives source conversion, spell quotes,
    instability, all 14 tuning items, the then-current Field Manual v7 (now
    v10), and the complete
    59-emitter Quasar palette from the canonical data. Remote source draws are
    atomic even when insufficient. The final ladder passed 235 JUnit tests,
    all 19 production GameTests, the focused verifier, JSON/shell/diff checks,
    guarded Hermes build/client/checkpoint inspection, and the real Main-PC
    desktop launcher. The guide audit removed dark-text shadows, corrected
    full-source image scaling, and proportionally fitted compact illustrations;
    before/after screenshots were directly inspected. Both development stacks
    stopped normally with port 25575 free.
22. [x] **Casting media, reagents, and resource escrow.** Distinguish bare
    casting, rituals, engravings, spellbooks, scrolls, and installed circles.
    Optional reagents may reduce mana, casting time, upkeep, and instability
    within server-configured floors. Genuine miscasts consume committed
    resources; policy/unloaded/rate/shutdown/internal failures refund or never
    withdraw them. The schema-2 checksummed staging contract separates four
    discount-bearing vanilla reagents from quartz ritual offerings; all six
    methods use one server-owned quote and escrow boundary. Quotes show
    requested versus applied clipping. Exact mana, reagent, offering, and
    scroll settlement survives owner lifecycle loss and shutdown, with missing
    execution targets classified as refundable rather than genuine faults.
    The final Hermes-only ladder (per Ian's machine instruction) passed 254
    JUnit tests, all 23 production GameTests, focused/JSON/shell/diff checks,
    guarded build/client launch, a Veil 59-emitter checkpoint, live ritual
    quote inspection, and direct screenshot inspection. Both Hermes units were
    stopped and port 25575 was free.
23. [x] **Persistent upkeep and natural conclusions.** Give every continuing
    effect a versioned owner, endpoint/deadline or termination predicate,
    upkeep payer/escrow, offline and unloaded-chunk policy, restart recovery,
    and idempotent atomic cleanup. Unpaid or non-concluding magic transitions
    into bounded deterministic Wild Magic rather than becoming free or orphaned.
    Successful continuing casts now atomically transfer their quoted prepaid
    upkeep into one schema-1 per-dimension SavedData contract that records the
    owner, program hash, dimension, natural and hard deadlines, upkeep cadence
    and balance, deterministic collapse seed, and bounded opaque cleanup
    handles with independent deadlines. Casts without continuing handles refund the unused upkeep claim.
    Continuing VM forces remain active through pre-halt delays and transfer
    only their unexpired remainder. A failed synchronous registration save
    restores and re-saves the prior ledger before escrow can refund.
    Loaded contracts debit exact elapsed cadence; unloaded chunks pause their
    reconciliation, while offline or invalid owners cannot drive world
    mutations. Natural expiry cleans exactly once. Unpaid and hard-cap paths
    persist deterministic collapsed and emitted states around bounded Wild
    Magic, then clean and remove idempotently across ticks and restarts. Status,
    force, mage-light, redstone-oracle, and temporary-form handles all use the
    same durable ledger. `/vectorregnum effect status`, Field Manual v10, and
    the Ponder primer expose the contract and conclusion rules. The final
    Hermes-only ladder passed 262 JUnit tests, all 30 production GameTests,
    focused/JSON/shell/overlay-diff checks, and a guarded client run with all 59
    Quasar emitters. Its inspected priority-23 frame showed three active
    contracts, their natural endpoints and prepaid balances, visible spell
    particles, and 12.15 mana remaining in escrow. Both Hermes units stopped
    and port 25575 was free.
24. [ ] **Advanced shared-memory spell control.** Add bounded variables,
    iterators, collision, watcher/signal/output operations, and logical parallel
    branches while retaining a shared `Push`/`Pop` stack. Branches advance in a
    deterministic server-tick order; shared operations are atomic and traced;
    branch count, work, lifetime, messages, and stack depth remain hard-capped.
25. [ ] **Cooperative rituals and multicasting.** Split/replicate circles and
    combine mana without implicit consent. Every contributor must approve each
    ritual's exact maximum mana, reagent, and upkeep commitment; reservations
    are atomic, auditable, restart-safe, and refunded if pre-start approval or
    funding fails.
26. [ ] **Recovered-mechanic security and accessibility hardening.** Preserve
    dangerous magic, render-only constructs, spell disruption, forced-attention
    effects, and Wild Magic through curated bounded capabilities, claim/team/PvP
    checks, deterministic randomness, rate/range/lifetime limits, mandatory
    telegraphs, client sensory controls, and atomic cleanup.
27. [ ] **Versioned SMP integration API.** Expose narrow optional hooks for the
    separate Origins, Combat, Progression, World/Story, Administration, and
    modpack repositories without absorbing those systems or making them hard
    dependencies. Repository ownership is defined in
    `docs/REPOSITORY_MAP.md`.
28. [ ] Configuration, elemental/reagent/upkeep balancing, profiling,
    survival and multiplayer playtests, abuse testing, and compatibility with
    representative NeoForge mods and the target SMP pack.
29. [ ] Release packaging: installation guide, changelog, localization,
    screenshots/video, versioning, migration notes, and distributable artifacts.
    Once the NeoForge mod is finished or release-ready and its visuals have
    passed direct inspection, update the
    [Regnum Hub](https://iankengott.github.io/regnum-hub/#index) with accurate
    in-game images and captions; do not publish placeholder, scaffold, or
    unverified Veil imagery as finished work.

## Language/runtime follow-up

- [x] Direct clockwise circle-to-vm2 lowering for typed values, memory,
  arithmetic/logic, control/time, perception, and physics sigils.
- [x] Fabric tick scheduler and read-only perception/world-effect boundary.
- [x] Static stack-type analysis before execution (runtime faults are already
  precise and bounded).
- [x] Executable creation/form opcodes with concrete material/rarity,
  permanence, volume, duration, range, replacement, and permission constraints.
- [x] Generic lowering/execution for every semantic library opcode through one
  opcode-driven Fabric adapter shared by all 15 curated spells.
- [x] Multithreaded automation ingress with immutable data and explicit
  many-producer/single-server-thread queue, VM, world, and mana ownership.

## Player authoring and teaching

- [x] Command-based server-authoritative editor and persistent draft.
- [x] Save, inspect, validate, copy into media, and recover via checksums.
- [x] Animated actual circle topology with compiler order/error highlighting.
- [x] Versioned Field Manual v10 with exact crystal/media/infrastructure recipes and commands,
  data-driven visual elements, search/history/bookmarks/scaling/scrolling,
  progression gating, live recipe/item rendering, and a native Ponder action.
- [x] Illustrated custom Field Manual inspired by Patchouli/Lexica Botania and
  GuideME: a distinctive but familiar visual layout, categories and search,
  history/bookmarks, contextual links from relevant content, annotated circle
  and mana diagrams, live recipe/item displays, progression-aware chapters,
  data-driven/localizable pages, and legible rendering across GUI scales.
- [x] Prototype native and maintained-library implementations on Fabric 1.21.1;
  select one using dependency stability, extensibility, visual identity,
  accessibility, authoring effort, and compatibility as explicit criteria.
- [x] Graphical/in-world placement editor with discoverable sigil palette.
- [x] **Create-style spell/scroll Ponders** showing compilation, execution,
  mana breakdown, and representative failure states step by step.
- [ ] Advancements/tutorial sequence that guides the complete survival arc.

## World, progression, and automation

- [x] Constructible finite crystal sources, capacity growth, extraction,
  elemental compatibility, attunement, and loaded-source remote draw.
- [x] First survival recipes, seven persistent research unlocks, and two
  advancement guidance entries.
- [x] Deterministic natural crystal world generation, geology/rarity balance,
  source recharge/growth, persistent progress, three storage/conduit tiers,
  loaded-chunk routing, restart-safe transfer, recipes, and upgrades.
- [x] Redstone logic expansion, remote activation, bounded data bridges, and
  multithreaded automation ingress with server-thread world ownership.
- [x] Formal chunk-unload, claim, team, PvP, and multiplayer permission policy.

## Content and release quality

- [x] Initial useful 15-spell library with concrete bounded world effects.
- [x] Persisted scheduled expiry for temporary light/redstone spell blocks.
- [x] Stable bounded presentation IR, codec, S2C execution-event bridge, and
  budgeted client runtime shared by library and player-authored spells; see
  `SPELL_PRESENTATION.md`.
- [x] Richly layered compiler-generated spell presentation. Every production
  spell receives a readable primary gesture
  supported by restrained microeffects across form/motion, illumination and
  atmosphere, spatial audio, tactile response, impact, and aftermath. The list
  is intentionally non-exhaustive, and the system should generate additional
  context-appropriate layers from spell semantics.
- [x] Priority-20a Veil-backed modular renderer with full Veil ownership of
  particle animations except enchanting-table particles, plus a built-in
  truth-telegraph fallback. Migration, automated enforcement,
  Hermes-present/absent gates, and the human-controlled Main-PC desktop
  attestation passed 2026-08-21.
- [ ] Original Vector-Regnum block/item texture pass and final presentation art.
- [x] Sixteen production Fabric GameTests in the frozen legacy repository cover commands, attachments,
  media/block-entity round trips, crystal interactions, timers, serialized
  restart contracts, two-player isolation, claims/death migration, relay
  persistence, remote ownership, and redstone/data behavior.
- [x] Twenty-nine production NeoForge GameTests replace and extend the legacy integration
  coverage and add live registration parity while preserving and expanding the
  loader-neutral JUnit suite.
- [ ] Configuration, balancing, profiling, localization beyond English,
  accessibility, and broader abuse protection.
- [ ] Survival playtest, death/copy/restart/upgrade tests, mod compatibility,
  release packaging, changelog, screenshots/video, and installation docs.

## Recovered SMP design

The canonical decisions extracted from the recovered design—including the
NeoForge transition, elemental identity, reagents, upkeep, shared memory,
per-ritual consent, safety constraints, and project boundaries—live in
`docs/SMP_INTEGRATION_DECISIONS.md`. Broader SMP systems are deliberately
separate projects; see `docs/REPOSITORY_MAP.md`.
