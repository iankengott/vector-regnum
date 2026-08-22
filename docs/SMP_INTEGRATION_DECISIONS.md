# SMP integration decisions

Decision date: 2026-08-13. This document records the parts of the recovered SMP
design that are canonical for Vector-Regnum and the boundaries that keep the
larger SMP maintainable. The recovered source remains at
`/home/iank/Downloads/docs/SMP-Design-Document.md`; its sibling
`smp-260813_1532.md` and `Whiteboard.pdf` are provenance, not executable
specifications.

## Platform and repository decision

- **NeoForge 1.21.1 is the active target.** Select and pin the exact supported
  NeoForge build during priority 20 rather than trusting a stale version number.
- The current Fabric 1.21.1 implementation is a verified but **deprecated
  migration baseline**. Do not add new gameplay features to it.
- The active `vector-regnum` repository carries that baseline while priority 20
  ports it to NeoForge. The exact Fabric snapshot is also preserved in the
  separate `vector-regnum-fabric-legacy` repository. Priority 20 must port the
  loader-neutral core before adapting registrations, networking, persistence,
  events, tests, launchers, and the Hermes workflow.
- There is no save-compatibility requirement between the unreleased Fabric
  alpha and NeoForge. Deterministic circle/program codecs should still remain
  versioned because they are part of the long-term design.
- Companion SMP systems live in separate public repositories and integrate
  through small, versioned APIs. See `REPOSITORY_MAP.md` for verified links.

## Rendering and presentation dependency

[FoundryMC Veil](https://github.com/FoundryMC/Veil) is approved as the optional
client rendering foundation for Vector-Regnum after the NeoForge migration.
Vector-Regnum owns the bounded adapter and semantic module vocabulary; Veil is
infrastructure beneath the existing loader-neutral presentation IR, not an
authored-spell language or a source of gameplay authority. Priority 20a pins
Veil 4.4.1 for Minecraft 1.21.1. Veil-present and Veil-absent clients,
dedicated-server classloading, resource reload, accessibility/LOD fallbacks,
and the exact Create 6.0.10, Sodium, Iris, and Bliss target-pack matrix passed.
Priority 20a completed on 2026-08-21 after the emission inventory, compact
client trace migration, automated single-exception allowlist, repeated Hermes
Veil-present/absent checks, and final human Main-PC visual gate passed. With
Veil active, Veil/Quasar replaces every Vector-Regnum particle-based animation
except Minecraft enchanting-table particles (`ParticleTypes.ENCHANT`). Built-in
particle animations remain only as the Veil-absent or failed fallback.

The built-in renderer always preserves mechanics-derived origin, direction,
area, timing, allegiance, danger, and impact cues. Post-processing, advanced
GPU features, and Veil itself may improve expressive layers but may never be
required for a cast to execute or for a player to receive its mandatory
telegraph. Companion mods request versioned semantic presentation capabilities
through Vector-Regnum rather than submitting arbitrary renderer source.

## Elemental identity and attunement

Every character has exactly **one permanent natural element**. It cannot be
rerolled through ordinary gameplay. Channel attunement is a separate mutable
choice and determines which element a current cast or mana route is attempting
to use.

The canonical ordinary natural elements are Water, Fire, Air, Earth,
Lightning, Time, Space, Light, Dark, Nature, Ice, and Sound. **Frost is renamed
to Ice.** Void remains a rare exceptional natural element outside the ordinary
twelve. **Arcane is neutral raw mana**, usable as a source/channel but never a
natural element.

Element use is not binary. Efficiency decreases with affinity distance from
the character's natural element:

- natural match: 100%;
- close resonance: 75%;
- distant resonance: 50%;
- opposed resonance: 25%.

No ordinary relationship falls below the agreed 25% floor. Priority 21 owns a
data-driven, symmetric compatibility matrix and must test every pair. This
matrix affects source conversion, cast cost, stability, and upkeep without
changing the permanent identity. Arcane provides a neutral fallback whose
exact efficiency is configurable; it cannot outperform a natural match.

## Reagents, media, and resource commitment

Bare casts, rituals, engravings, spellbooks, scrolls, and installed circles are
distinct casting methods. A recipe may commit optional reagents to reduce one
or more of:

- initial mana;
- casting time;
- continuing upkeep;
- instability/miscast risk.

Every discount has a server-configured floor and cap so materials cannot make
an ambitious spell free, instant, permanent, or perfectly safe. The compiler
must show the undiscounted quote, each reagent contribution, and the final
bounded quote before approval.

Resources enter a server-authoritative escrow before execution. A genuine
spell fault or Wild Magic consumes committed reagents. Permission rejection,
rate limiting, an unloaded target, shutdown cancellation, or an internal
engine failure must not consume them. Scrolls are normally committed as part
of activation and can be lost to a genuine miscast; reusable books survive,
but their committed reagents do not. Engraving consumes construction materials
when the engraving is accepted. Rituals consume their approved offering pool.

## Persistent upkeep and natural conclusions

Magic should reach a natural conclusion. Every persistent effect records a
versioned ownership entry containing its owner, program hash, anchors,
dimension, start time, endpoint/deadline or termination predicate, upkeep
schedule, authorized payer/escrow, and atomic cleanup plan.

A compiler-provable endpoint is preferred. A program without one may execute
only with an instability surcharge and an absolute server lifetime cap. If an
effect cannot pay upkeep before reaching its conclusion, or reaches its hard
cap without concluding, it transitions into a bounded deterministic Wild Magic
collapse and then cleans itself up atomically. It must never silently become a
free permanent effect.

Offline and chunk behavior is explicit:

- unloaded effects do not simulate world actions;
- elapsed upkeep debt and deadlines are recorded against server time and
  reconciled when their state loads;
- an offline player's personal mana cannot be debited directly;
- an effect may continue only from prepaid escrow or an explicitly authorized,
  loaded infrastructure source;
- insufficient escrow/source produces the same bounded Wild Magic conclusion;
- restart reloads the ownership ledger before effects resume, and cleanup is
  idempotent so a crash cannot duplicate or orphan an effect.

## Shared memory, iteration, and branching

Parallel spell branches retain a shared `Push`/`Pop` stack. This is logical
parallelism, not unrestricted Java-thread mutation. Each branch advances in a
stable deterministic order on the authoritative server tick; every stack
operation is atomic, globally ordered, traced, and bounded. The language must
specify underflow, branch cancellation, join, deadline, and ownership rules.

Automation producers may remain concurrent, but they submit immutable bounded
messages. Only the claimed server thread may advance branches, mutate shared
memory, charge mana, or touch Minecraft state. Iterators are sequential and
bounded. Branch counts, shared-stack depth, messages, lifetime, and per-tick
work all have hard limits.

## Cooperative rituals

Every contributor must explicitly approve **each individual ritual** after
seeing their exact maximum mana/material/upkeep commitment. Membership in a
team or persistent casting group is not consent.

Approval creates an atomic reservation, not an immediate partial cast. If any
required contributor declines, disconnects, loses permission, or cannot fund
the reservation before execution begins, the ritual cancels and refunds all
reservations. Once execution begins, approved contributions enter escrow and
follow the same spell-fault versus engine-failure consumption rules as solo
casting. Contributor identity, limits, revocation window, and resulting effect
ownership remain auditable after restart.

## Security and accessibility constraints

Recovered mechanics keep their core idea while passing through these common
boundaries:

- server-authoritative claims, team/friendly-fire, PvP, loaded-chunk, range,
  rate, concurrency, lifetime, and world-mutation checks;
- typed curated capabilities instead of arbitrary code, arbitrary renderer
  source, arbitrary packets, or unbounded random world mutation;
- deterministic seeded Wild Magic selected from bounded effects with atomic
  cleanup;
- mandatory truth telegraphs that cannot be hidden by cosmetic programming;
- client controls for particles, flashes, darkness/fog, chromatic effects,
  audio, camera motion, and reduced motion;
- forced-camera mechanics such as Averted Gaze require gameplay permission,
  a bounded ramp/rate and duration, an accessibility-safe alternative cue, and
  no client-side authority over the gameplay result;
- hostile spell disruption must validate the active spell, attacker,
  equipment/stance, timing window, claim/team/PvP policy, and a bounded reverse
  execution trace.

These restrictions constrain implementation, not the central fantasy of
dangerous programmable magic.

## Scope boundary

Vector-Regnum owns the magic language/runtime, circles and casting media, mana
and affinities, reagents/upkeep, cooperative casting, automation, spell
presentation, magic security, and versioned integration APIs.

It does **not** own the full origins roster, precision melee system, general
class/profession/skill-tree system, story/dimensions/weather campaign, server
administration tools, or third-party modpack. Those are separate projects in
`REPOSITORY_MAP.md`. Vector-Regnum may expose hooks for them without absorbing
their implementation.
