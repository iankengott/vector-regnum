# Development launch workflows

These scripts build and launch the active NeoForge 1.21.1 repository. The
deprecated Fabric alpha remains reproducible in the separate
`vector-regnum-fabric-legacy` checkout at `c7371ca`.

## Main PC one-click launcher

`scripts/local-play.sh` backs the executable **Play Vector-Regnum** shortcut at
`~/Desktop/Vector-Regnum.desktop`. It resolves Java 21 declaratively, stages the
same checked-in test-world configuration used on Hermes, starts the server in
the owned transient unit `vector-regnum-local-server.service`, waits for
`127.0.0.1:25575`, and launches the quick-play client through `steam-run` for
NixOS graphics-driver access. The local client enables the exact optional Veil
4.4.1 runtime; the dedicated server remains dependency-free.

Closing Minecraft triggers the launcher's cleanup trap, which stops and
collects the server unit. The launcher refuses unexpected JARs in either dev
`mods/` directory, a second active launcher, and any pre-existing port-25575
listener. It also runs `scripts/check-dev-server-config.sh` before starting and
requires exactly `eula=true`, `server-port=25575`, and
`server-ip=127.0.0.1`; `scripts/verify-port.sh` exercises both its positive and
unsafe-fixture paths. Its readiness gate also inspects the live socket and
uses `scripts/check-dev-listener.sh` to reject wildcard, IPv6, or duplicate
listeners; the verifier exercises those negative fixtures too. The desktop entry calls
`/run/current-system/sw/bin/bash`,
not a versioned `/nix/store` system path, so weekly Nix garbage collection
cannot break it.

The Main-PC release or roadmap visual gate is approval-only. Ask Ian before
opening anything on NixOS. After he approves this NixOS-only gate, run:

```bash
scripts/priority21-local-visual-wizard.sh
```

The wizard does not open, focus, type into, or close Minecraft. It preflights
the isolated launcher, gives the human an explicit in-game checklist, and then
verifies permanent natural identity versus mutable channel attunement, Ice
terminology and visuals, the then-current Field Manual v7 (the repository now
ships v10), authored/library casts, resource
reload, accessible low LOD, the IPv4 endpoint, owned-unit cleanup, and free
port after the human closes Minecraft normally. It writes the ignored evidence
record `visual-evidence/main-pc-priority21-visual-attestation.txt` only after
every human and automatic gate passes. The implementation remains in the
combined `priority20-local-visual-wizard.sh`; the priority-21 entry point is a
stable wrapper.

For the 2026-08-22 priority-21 closeout, Ian explicitly reported that the
corrected final artifact was “all good” after the earlier full visual exercise;
the later rendering-only change was then opened through the real desktop
launcher and its guide was directly inspected before and after the fix. No
wizard attestation file was retroactively synthesized. That split evidence and
the normal shutdown/port checks are recorded in
`docs/PRIORITY_21_DECISIONS.tsv`. Future human roadmap gates should use the
wizard so their complete checklist is captured in one record.

## Priority 22 casting checks

Run the focused casting-method, reagent, escrow, guide, Ponder, JSON, and shell
gate with:

```bash
scripts/verify-priority22.sh
```

Priority 22's final runtime gate was run entirely on Hermes at Ian's direction.
The guarded client must log Veil loading 59 Quasar emitters and
`VISUAL_CHECKPOINT_READY milestone=priority_22 ... ritual_offering=quartz
field_manual=9`. Its live chat log must show a ritual quote with separate
`requested` and `applied` reagent values plus `Nether Quartz committed as
ritual offering (no discount)`. Inspect the captured window rather than
treating the file as evidence by itself, then stop both development units and
confirm Hermes loopback port 25575 is free.

## Priority 23 persistent-effect checks

Run the focused ownership-contract, upkeep state machine, guide/Ponder, JSON,
shell, and source-policy gate with:

```bash
scripts/verify-priority23.sh
```

The production GameTest matrix now contains 30 tests, including seven
priority-23 cases for SavedData round-trip, exact loaded upkeep, pre-halt force
continuation, natural idempotent cleanup, deterministic unpaid collapse,
offline ownership, and unloaded-chunk pausing. The guarded Hermes visual client must log both
`VISUAL_CHECKPOINT_READY milestone=priority_23 ... field_manual=10` and
`PERSISTENT_EFFECT_CHECKPOINT_READY ... contracts=...`, and its live chat must
show the persistent contract registration plus remaining escrow. Capture only
after the second marker, open and inspect the image, then stop both development
units and confirm loopback port 25575 is free.

The 2026-08-29 final Hermes gate logged three live contracts with 12.15 mana
remaining in escrow and no malformed-contract quarantine. Direct inspection of
`visual-evidence/hermes-window-20260829T064530Z.png` confirmed the authored
scene, active spell particles, and all three endpoint/upkeep lines. Both
development units then stopped and port 25575 was free.

## Priority 21 elemental checks

Regenerate the complete bounded Quasar vocabulary from the canonical element
list, then prove the generated tree is unchanged on a second run:

```bash
scripts/generate-priority21-presentation-assets.sh
scripts/generate-priority21-presentation-assets.sh
```

Run the focused matrix, migration, legacy-alias, tuning-item, guide, resource,
and source-policy gate with:

```bash
scripts/verify-priority21.sh
```

The Hermes automated scene first logs `VISUAL_CHECKPOINT_READY` after its
gameplay checks, waits for those bounded cues to expire, and then emits the
two-row canonical palette and logs `ELEMENT_PALETTE_READY ... count=14`.
Capture after the second marker. Exercise both the default Veil client and the
dependency-free fallback with `VR_CLIENT_RENDERER=builtin`.

Use Hermes for guide inspection by default. If a final Main-PC guide gate is
genuinely necessary, stop and ask Ian before opening the real desktop launcher
or a NixOS visual artifact. After approval, inspect the manual itself rather
than inferring rendering from tests. At compact scale, confirm dark text has no
duplicate shadow, illustrations show the complete source and preserve aspect
ratio, the first plate fits the opening viewport, and search results remain
readable. Keep screenshots under the ignored `visual-evidence/guide-audit/`
directory, close Minecraft normally, and confirm the local unit is inactive
and port 25575 is free.

## Priority 20a renderer checks

These commands validate the completed Veil particle migration. Active Veil
replaces every Vector-Regnum particle animation except enchanting-table
particles, the automated allowlist guards that rule, and the automated plus
Hermes-present/absent gates passed on 2026-08-21. The human-controlled Main-PC
desktop attestation also passed on the final artifact that day.

Check the live handoff documents for claims that contradict the completed
priority before closing later documentation passes:

```bash
scripts/check-handoff-docs.sh
```

Run the deterministic dependency, classloading, resource, and focused-test
gate with:

```bash
scripts/verify-priority20a.sh
```

The priority-20a compatibility matrix currently depends on the Main-PC Prism
instance at `/home/iank/.local/share/PrismLauncher/instances/1.21.1`. Do not
run it on NixOS without Ian's explicit approval. If a future change needs this
gate, first determine whether the verified artifacts can be staged on Hermes;
otherwise stop and explain why Main-PC execution is necessary before asking.
After approval, `scripts/priority20a-compat-matrix.sh run` stages exact
SHA-256-verified Veil 4.4.1, Create 6.0.10, Sodium 0.8.13-beta.2, Iris
1.8.14-beta.1, and Bliss 2.1.2 artifacts from the target Prism pack into
ignored isolated run directories. It never changes the live pack. The run
stages a Create mechanical press and cogwheel in the automated scene, requires
Iris-safe Veil compatibility mode, and rejects unexpected high-severity OpenGL
diagnostics. The `stage` and `check` actions can be run separately.

## Hermes development workflow

These scripts synchronize Vector-Regnum to a dedicated development worktree on
Hermes, verify it with JDK 21, launch an isolated NeoForge server and client,
and bring visual evidence back to this repository. They do not install the mod
into Hermes's normal Minecraft launcher and do not control any production
server, tmux session, or dashboard service.

The default destination is fixed:

```text
ian-kengott@100.88.229.63:/home/ian-kengott/projects/vector-regnum
```

## Normal loop

From the repository root:

```bash
scripts/hermes-sync.sh
scripts/hermes-build.sh
scripts/hermes-client.sh restart
scripts/hermes-client.sh logs
```

Hermes clients use Veil by default. Set `VR_CLIENT_RENDERER=builtin` on the
client command to prove the dependency-free fallback, for example:

```bash
VR_CLIENT_RENDERER=builtin scripts/hermes-client.sh restart
```

## Production NeoForge GameTests

The ordinary Gradle `test` task runs JUnit and contract tests. The 30 production
NeoForge GameTests must additionally run inside the real isolated GameTest
server on Hermes when their integration surface changes:

```bash
ssh ian-kengott@100.88.229.63 \
  'cd /home/ian-kengott/projects/vector-regnum && env JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 PATH=/usr/lib/jvm/java-21-openjdk-amd64/bin:/usr/bin:/bin ./gradlew --no-daemon runGameTestServer'
```

The runner exits after the matrix completes and must report all 30 required
tests passed. The tests cover live registration parity, commands, players,
attachments, media/tablet and crystal block entities, scheduled expiry,
serialized tick-queue reload, claim/death migration, relay persistence, remote
ownership, redstone/data automation, safe follow-up VM queueing from a real
Vector Step cast, priority-22 casting/escrow behavior, and priority-23 durable
effect ownership, reconciliation, collapse, and cleanup. The parity test reads
`data/vector_regnum/registration_parity.json` and queries the running registry,
payload, attachment, creative-tab, and command state; update that manifest when
an intentional registration changes. A true OS-process stop/start remains part
of the Hermes ladder below.

## Hermes-side diff check

The guarded Hermes mirror intentionally has no `.git`. Validate the exact
candidate through a temporary public-clone overlay on Hermes:

```bash
scripts/hermes-diff-check.sh
```

The script refuses non-Hermes hosts, excludes generated runtime/build/evidence
directories, applies intent-to-add for new source files, runs
`git diff --check`, rejects remaining untracked files, and removes its
temporary clone. It never syncs the clone back into the guarded mirror.

The first real sync may create the destination only when it is absent or empty.
It writes `.vector-regnum-hermes-worktree`; every later rsync deletion requires
the expected remote identity, a validated path, and an exact marker match.

The launch controller copies `dev/hermes/eula.txt` and `server.properties` into
the excluded remote `run/server/` directory, starts `runServer` as
`vector-regnum-dev-server.service`, waits until its isolated port **25575** is
listening, and only then starts the client. The client is already configured to
quick-play `127.0.0.1:25575` and runs as `vector-regnum-dev-client.service`.
Both transient user units survive the SSH command ending.

The controller never addresses port 25565 or any tmux session. It refuses to
start if another process owns 25575, and refuses to stop either unit unless its
command is this worktree's Gradle wrapper plus the expected `runServer` or
`runClient` task.

Useful client commands:

```bash
scripts/hermes-client.sh start
scripts/hermes-client.sh restart
scripts/hermes-client.sh status
scripts/hermes-client.sh logs
scripts/hermes-client.sh logs-follow
scripts/hermes-client.sh stop
```

## Visual evidence

After `hermes-client.sh start` or `restart` reports that port 25575 is ready,
the NeoForge client opens and quick-plays the dedicated Vector-Regnum server. The
two exact development units must remain running while the scene is inspected.

On the very first Minecraft launch for this worktree, the game may stop at its
accessibility welcome screen before honoring quick-play. Select **Continue**
once through Hermes's desktop; the choice persists in the excluded
`run/client/` state. Explicit IPv4 avoids NixOS resolving `localhost` to `::1`
while the guarded server listens only on IPv4 loopback. A later portal screenshot can leave Minecraft's game menu
open because the portal temporarily takes focus. Return to the game before the
next capture if that happens.

Focus Minecraft through Hermes's remote desktop, then capture its window:

```bash
scripts/hermes-screenshot.sh window
```

For context around the game, capture the full desktop:

```bash
scripts/hermes-screenshot.sh desktop
```

Screenshots are copied to the repository's ignored `visual-evidence/` directory
and the evidence directory is excluded from the source sync. A successful
capture is evidence that GNOME produced an image; someone must still open and
inspect that image before describing the feature as visually verified.

The remote worktree guard is the exact marker
`.vector-regnum-hermes-worktree`, containing
`vector-regnum-hermes-worktree-v1`. The screenshot command also requires that
marker before creating or copying evidence.

## Safe configuration overrides

The scripts accept these environment variables, but validate them before use:

- `VR_HERMES_HOST`: must be an `ian-kengott@IPv4` destination and the connected
  machine must report the expected hostname.
- `VR_HERMES_EXPECTED_HOSTNAME`: letters, digits, dots, and hyphens only.
- `VR_REMOTE_DIR`: exactly one named child under
  `/home/ian-kengott/projects/`; deletion still requires the ownership marker.
- `VR_SSH_TIMEOUT`: integer from 1 through 60 seconds.

Preview a later sync with `scripts/hermes-sync.sh --dry-run`. Dry-run mode never
creates the remote marker, so the destination must already be initialized.
