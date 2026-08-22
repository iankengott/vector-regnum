# Field Manual backend decision

> Historical platform decision: this comparison selected the backend for the
> now-deprecated Fabric 1.21.1 alpha. NeoForge priority 20 later revalidated and
> retained the native implementation on the active target. Keep the comparison
> below as historical evidence; do not reuse its Fabric artifact conclusions as
> current dependency facts.

Decision date: 2026-08-11. Target: Fabric 1.21.1.

## Decision

Keep the checked-in native, data-driven Field Manual as the production backend.
It is already an end-to-end prototype using the real v5 book data, recipes,
progression state, server Ponder traces, contextual links, original artwork,
scrolling, tooltips, and independent content-text scaling. Do not add a required
guidebook-mod dependency for this milestone.

The decision is encoded in `GuideBackendDecision` and guarded by
`GuideBackendDecisionTest`. Scores are 1–5, higher is better. A candidate
without a Fabric 1.21.1 artifact is ineligible regardless of score.

| backend | Fabric 1.21.1 | dependency stability | extensibility | visual identity | accessibility | authoring efficiency | compatibility | total |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Native | yes | 5 | 5 | 5 | 4 | 3 | 5 | 27 |
| Patchouli | yes | 5 | 3 | 3 | 5 | 5 | 4 | 25 |
| Lavender | yes | 3 | 4 | 4 | 4 | 4 | 4 | 23 |
| Modonomicon | yes | 5 | 4 | 4 | 4 | 4 | 3 | 24 |
| GuideME | no | 5 | 5 | 4 | 5 | 5 | 2 | ineligible |

## Prototype comparison

The native prototype exercises every required Vector-Regnum page primitive in
the actual client. For the library side of the spike, those same primitives
were mapped against maintained projects' documented content and extension
models before scoring:

- Patchouli provides a stable Fabric 1.21.1 build and a mature accessible,
  data-driven book format. Standard pages, recipes, links, localization, and
  navigation map well. Vector-Regnum's server-authored Ponder trace player and
  exact themed layout would require custom component integration and surrender
  some visual control.
- Modonomicon has maintained Fabric 1.21.1 artifacts, data-driven progression,
  recipes, and rich styling. It maps closely, but adds a comparatively large
  runtime surface and a migration boundary for custom trace scenes.
- Lavender supports Fabric across 1.21–1.21.4 and offers strong custom layout
  primitives, but its public release activity is older than the other eligible
  candidates. It does not improve the current prototype enough to justify the
  dependency and migration.
- GuideME remains the UX reference for smooth scrolling, inline recipes,
  cross-links, search, tooltips, scale handling, and interactive 3D scenes.
  Its current published platform list is Forge/NeoForge, not Fabric, so it
  cannot be the backend for this target.

This was a selection prototype, not an excuse to vendor or briefly ship an
unused library. The evaluated capabilities are implemented and tested in the
native backend; dependency metadata stays unchanged. Revisit the decision only
if the target loader/version changes or native authoring cost becomes the
dominant maintenance burden.

## Evidence consulted

- [Patchouli repository and Fabric/NeoForge layout](https://github.com/VazkiiMods/Patchouli)
- [Patchouli authoring documentation](https://vazkiimods.github.io/Patchouli/docs/patchouli-basics/getting-started/)
- [GuideME documentation](https://guideme.appliedenergistics.org/)
- [GuideME published platforms and versions](https://modrinth.com/mod/guideme/versions)
- [Lavender published platforms and versions](https://modrinth.com/mod/lavender/versions)
- [Modonomicon authoring documentation](https://klikli-dev.github.io/modonomicon/docs/getting-started/)
- [Modonomicon project and loaders](https://modrinth.com/mod/modonomicon)
