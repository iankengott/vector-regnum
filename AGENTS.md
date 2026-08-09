# Vector-Regnum AI handoff

This file is the starting point for any AI or developer continuing the mod.
The repository is a playable Fabric 1.21.1 alpha, not a finished release.

## Read first

1. Read `README.md` for the current feature set, commands, and confirmed facts.
2. Read `ROADMAP.md`. Its numbered **Next priorities** queue is the canonical
   work order; the later sections give the acceptance scope for those items.
3. Read `scripts/README.md` before launching, syncing, or testing on Hermes.
4. Run `hostname; whoami` before using a machine-specific path. The main PC is
   `nixos`/`iank`; Hermes is `ian-kengott-GF63-Thin-11SC`/`ian-kengott`.

When asked for "what is next," report the first unfinished entries in the
numbered queue. Do not describe priorities 1–10 as production-complete: they
have coherent end-to-end alpha passes, but still need balance and hardening.

## Repository and machine boundaries

- Main-PC repository:
  `/home/iank/Desktop/my mods/mods-editing/vector-regnum`
- Hermes guarded mirror:
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

## Required verification ladder

Use verification proportionate to the change, but gameplay or visual work is
not complete without the full ladder.

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
- Scrolls are consumed only on the first accepted successful cast; spellbooks
  are reusable; tablets cast from their verified stored anchor.
- Temporary world effects must survive restart safely through scheduled world
  ticks rather than process-local cleanup state.
- Tutorial changes require bumping the versioned guide attachment so existing
  players receive the revised manual.
- The Hermes automated showcase is gated by
  `VECTOR_REGNUM_VISUAL_CHECK=1`; normal local play must not stage it.

## Keeping the handoff current

After a meaningful change:

- update `ROADMAP.md` and the confirmed/unfinished sections of `README.md`;
- update `scripts/README.md` if any test or launch behavior changes;
- update the Vector-Regnum project memory and project hub in the Obsidian
  memory vault on the main PC;
- record new automated test counts and the latest inspected visual evidence;
- keep generated worlds, logs, Gradle output, and `visual-evidence/` out of git.

Do not check off a roadmap item merely because a class or stub exists. It needs
an end-to-end playable path, bounded failure behavior, automated coverage, and
visual confirmation when it affects the game.
