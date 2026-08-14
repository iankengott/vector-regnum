# Vector-Regnum AI handoff

This file is the starting point for any AI or developer continuing the mod.
This is the active Vector-Regnum repository, currently carrying the playable,
verified **but deprecated** Fabric 1.21.1 alpha as its migration baseline.
NeoForge 1.21.1 is the active target; the exact Fabric state is preserved in a
separate legacy repository and priority 20 ports this active repository before
new gameplay development.

## Read first

1. Read `README.md` for the current feature set, commands, and confirmed facts.
2. Read `ROADMAP.md`. Its numbered **Next priorities** queue is the canonical
   work order; the later sections give the acceptance scope for those items.
3. Read `scripts/README.md` before launching, syncing, or testing on Hermes.
4. Read `docs/SMP_INTEGRATION_DECISIONS.md` and
   `docs/REPOSITORY_MAP.md` for the recovered SMP decisions and project scope.
5. Run `hostname; whoami` before using a machine-specific path. The main PC is
   `nixos`/`iank`; Hermes is `ian-kengott-GF63-Thin-11SC`/`ian-kengott`.

When asked for "what is next," report the first unfinished entries in the
numbered queue. Never include a checked entry in an unfinished-priority list.
Do not describe checked priorities as production-complete: they have coherent
end-to-end alpha passes, but still need balance and hardening.

## Current handoff checkpoint

As of 2026-08-13, priorities 1–19 are checked for the deprecated Fabric alpha
and the first unfinished canonical item is **20, the NeoForge 1.21.1 migration
and repository split**. Priorities 17–19 add formal multiplayer lifecycle/security,
the programmable Automation Relay and bounded concurrency/data bridge, the
sixteen-test production Fabric GameTest matrix, and the compiler-driven layered
client presentation runtime with LOD/accessibility controls.
Always re-read `ROADMAP.md` before reporting or implementing work because it
supersedes this dated checkpoint whenever the queue changes.

Do not begin priority 20a or 21 or later while this active checkout still builds
on Fabric. Priority 20 first verifies the published legacy snapshot, then ports
this repository's behavior and safety/test coverage to NeoForge. Priority 20a
then establishes the optional Veil-backed modular renderer and compatibility
gate before new gameplay development begins at priority 21.

## Subagent collaboration

Ian wants implementation work delegated into bounded independent subtasks when
concurrency is available. Use only **Sol high** and **Luna max** subagents:
choose Sol high for focused code/test tasks and Luna max for cross-cutting
design, integration, or difficult review. If one of those profiles is not
available, keep that work in the parent instead of substituting another model.
Do not assign multiple agents overlapping files. The parent agent owns final
integration, documentation, and the complete verification ladder.

## Repository and machine boundaries

- Active Main-PC checkout (currently the Fabric-to-NeoForge migration baseline):
  `/home/iank/Desktop/my mods/mods-editing/vector-regnum`
- Frozen Main-PC Fabric legacy checkout:
  `/home/iank/Desktop/my mods/mods-editing/vector-regnum-fabric-legacy`
- Current Fabric Hermes guarded mirror:
  `ian-kengott@100.88.229.63:/home/ian-kengott/projects/vector-regnum`
- Main-PC launcher: `/home/iank/Desktop/Vector-Regnum.desktop`
- Development port: loopback `25575` only.
- Hermes owns only `vector-regnum-dev-server.service` and
  `vector-regnum-dev-client.service`. The local launcher owns only
  `vector-regnum-local-server.service`.
- Never touch port 25565, production/modpack servers, tmux Minecraft sessions,
  the normal launcher, or unrelated saves while testing this project.
- Preserve unrelated user changes. Do not sync to Hermes until the guarded
  scripts have verified its identity and ownership marker.
- The active public remote is `https://github.com/iankengott/vector-regnum`.
  Every companion public remote and its exact local path is recorded in
  `docs/REPOSITORY_MAP.md`; do not merge their scopes back into this mod.

## Required verification ladder

The commands below verify the deprecated Fabric reference. Use verification
proportionate to the change, but gameplay or visual work is not complete
without the full ladder. Priority 20 must replace every Fabric-specific step
with an equivalent NeoForge step and retain loader-neutral coverage.

1. On NixOS, use Java 21 and run the automated suite:

   ```bash
   task_jdk=$(nix eval --raw nixpkgs#jdk21.outPath)
   JAVA_HOME="$task_jdk" PATH="$task_jdk/bin:$PATH" \
     ./gradlew --no-daemon clean test build
   find src -type f -name '*.json' -print0 | xargs -0 -n1 jq empty
   find scripts -type f -name '*.sh' -print0 | xargs -0 -n1 bash -n
   git diff --check
   ```

2. Exercise server/client integration on Hermes through the guarded scripts:

   ```bash
   scripts/hermes-sync.sh
   scripts/hermes-build.sh
   scripts/hermes-client.sh restart
   scripts/hermes-client.sh logs
   scripts/hermes-screenshot.sh window
   scripts/hermes-client.sh stop
   ```

   Confirm the log contains `VISUAL_CHECKPOINT_READY` for the automated
   showcase when applicable. A screenshot file alone is not a visual test:
   open it, inspect the requested behavior, and record what was actually seen.
   Always stop the two Hermes development units afterward unless the user asks
   to keep them open.

3. Before handing a playable milestone to Ian, launch the real
   `/home/iank/Desktop/Vector-Regnum.desktop`, visually inspect the local
   client, close Minecraft normally, and confirm the local server unit is
   inactive and loopback port 25575 is free.

Pure documentation changes do not require Minecraft launches. Pure core logic
normally requires step 1 and Hermes build parity; any player-visible,
rendering, command, persistence, or Fabric integration change requires steps
1–3 and direct visual inspection.

## Regression invariants

- No magic circle follows a player by default. A requested preview is unique
  per player, anchored to its captured world position, bounded in particles,
  and expires.
- All casting and mana mutation is server-authoritative. The VM is tick-driven
  and must retain hard work, lifetime, stack, loop, range, result, and duration
  limits.
- Entity raycasts respect block occlusion; remote mana draw requires the same
  dimension and a loaded source chunk.
- In the Fabric legacy snapshot, scrolls are consumed only on the first accepted
  successful cast, spellbooks are reusable, and tablets cast from their verified
  stored anchor. The NeoForge reagent/media priority intentionally supersedes
  this: genuine spell faults consume committed resources, while policy,
  unloaded-target, shutdown, and engine failures do not.
- Temporary world effects must survive restart safely through scheduled world
  ticks rather than process-local cleanup state.
- Running spells and compatibility visuals fail closed when their owner dies,
  disconnects, changes dimension, or leaves a loaded owner chunk. Player
  targets respect server PvP, spectator, team friendly-fire, and claim policy.
- Automation ingress carries only immutable bounded frames; only its claimed
  server tick thread may touch worlds, VMs, mana, or relay state. Offline owners
  and unloaded relay chunks never execute.
- Presentation consumes compact authoritative events and is strictly cosmetic.
  LOD or accessibility settings may remove expressive layers but never the
  mechanics-derived truth telegraph or alter a gameplay outcome.
- Veil is optional client presentation infrastructure only. It must never be
  required for dedicated-server startup, authoritative gameplay, or mandatory
  truth telegraphs; the built-in renderer and accessible low-LOD path remain
  functional when Veil or post-processing is unavailable.
- Tutorial changes require bumping the versioned guide attachment so existing
  players receive the revised manual.
- The Hermes automated showcase is gated by
  `VECTOR_REGNUM_VISUAL_CHECK=1`; normal local play must not stage it.
- Canonical NeoForge elemental identity is one permanent natural element;
  attunement remains mutable. Frost becomes Ice, Void is rare, Arcane is neutral
  raw mana, and affinity efficiency uses bounded 100/75/50/25% bands.
- Persistent effects require versioned ownership, upkeep, endpoint/deadline,
  offline/unloaded/restart reconciliation, and idempotent cleanup. Failure to pay
  or conclude produces bounded deterministic Wild Magic.
- Parallel branches may share `Push`/`Pop`, but shared operations are atomic and
  branches advance in deterministic server-tick order. OS threads never mutate
  VM/world/mana state directly.
- Every cooperative ritual requires explicit approval from every contributor
  for that individual ritual and its exact maximum commitment.

## Keeping the handoff current

After a meaningful change:

- update `ROADMAP.md` and the confirmed/unfinished sections of `README.md`;
- update `scripts/README.md` if any test or launch behavior changes;
- update the Vector-Regnum project memory and project hub in the Obsidian
  memory vault on the main PC;
- record new automated test counts and the latest inspected visual evidence;
- keep generated worlds, logs, Gradle output, and `visual-evidence/` out of git.
- update the Regnum Hub with inspected in-game images only when the roadmap's
  release milestone says the NeoForge mod is finished or release-ready.

When a numbered priority is completed, check it in the canonical queue and
remove its remaining-work wording everywhere in the same pass. Confirm that a
fresh search for stale test counts, old guide versions, and claims that the
completed work "still remains" returns no misleading handoff text.

Do not check off a roadmap item merely because a class or stub exists. It needs
an end-to-end playable path, bounded failure behavior, automated coverage, and
visual confirmation when it affects the game.
