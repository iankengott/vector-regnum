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
- [x] Persistent tutorial guide, Sigil Tome, Firebolt, Frost Nova, effects,
  collision, cooldowns, and finite server-authoritative mana.
- [x] 172 JUnit/contract tests, 17 production NeoForge GameTests, plus real
  local/Hermes server boots and direct visual inspection.

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
    The selected native v6 backend is data-driven and adds three original
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
20. [ ] **NeoForge 1.21.1 migration and repository split.** *Implementation,
    automated coverage, and Hermes verification are complete; the final
    Main-PC desktop-launch visual rerun remains after fixing explicit IPv4
    quick-play.* The archived Fabric
    alpha is fixed at `c7371ca`; the active repository now uses NeoForge
    21.1.248 and ModDevGradle with Mojang/Parchment mappings. Registrations,
    attachments and claim SavedData, seven payloads, lifecycle/events,
    custom-feature worldgen, client rendering/guide, commands, guarded NixOS
    and Hermes launchers, 172 JUnit tests, and 17 live NeoForge GameTests pass
    their gates. The manifest-driven parity test queries the running
    registries, payload directions, attachments, creative tab, and command
    dispatcher and was proved by an intentional negative control. Behavioral
    parity, hard safety limits, the loader-neutral presentation IR, and the
    built-in client renderer remain intact, so dedicated-server startup and
    baseline telegraphs do not depend on Veil.
20a. [ ] **Veil-backed modular presentation overhaul and compatibility gate.**
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
    renderer/Create compatibility matrix.
21. [ ] **Elemental identity and affinity expansion.** Replace Frost with Ice;
    add Water, Air, Earth, Lightning, Time, Space, Light, Dark, Nature, Sound,
    and rare Void around the ordinary twelve; retain Arcane only as neutral raw
    mana. Every character receives exactly one permanent natural element while
    channel attunement remains mutable. Use a data-driven symmetric affinity
    matrix with 100/75/50/25% efficiency bands and a 25% opposed floor.
22. [ ] **Casting media, reagents, and resource escrow.** Distinguish bare
    casting, rituals, engravings, spellbooks, scrolls, and installed circles.
    Optional reagents may reduce mana, casting time, upkeep, and instability
    within server-configured floors. Genuine miscasts consume committed
    resources; policy/unloaded/rate/shutdown/internal failures refund or never
    withdraw them.
23. [ ] **Persistent upkeep and natural conclusions.** Give every continuing
    effect a versioned owner, endpoint/deadline or termination predicate,
    upkeep payer/escrow, offline and unloaded-chunk policy, restart recovery,
    and idempotent atomic cleanup. Unpaid or non-concluding magic transitions
    into bounded deterministic Wild Magic rather than becoming free or orphaned.
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
- [x] Versioned Field Manual v6 with exact crystal/media/infrastructure recipes and commands,
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
- [ ] Original Vector-Regnum block/item texture pass plus the priority-20a
  Veil-backed modular renderer, with a built-in truth-telegraph fallback, for
  release-quality presentation.
- [x] Sixteen production Fabric GameTests in the frozen legacy repository cover commands, attachments,
  media/block-entity round trips, crystal interactions, timers, serialized
  restart contracts, two-player isolation, claims/death migration, relay
  persistence, remote ownership, and redstone/data behavior.
- [x] Seventeen production NeoForge GameTests replace the legacy integration
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
