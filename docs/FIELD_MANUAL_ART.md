# Field Manual artwork

The Field Manual v5 includes three original 256×256 raster plates generated
for Vector-Regnum with Codex's built-in image-generation tool on 2026-08-11.
They are checked in under
`src/main/resources/assets/vector_regnum/textures/gui/guide/` and referenced by
`field_manual.json`.

- `compiler_circle.png` — illuminated-manuscript technical plate of a
  three-ring magical program: north start, clockwise reading, then inward,
  with a purple tome and mana crystal. Parchment, plum, brass, and amethyst
  palette; no lettering.
- `mana_network.png` — illuminated technical-fantasy diagram of a central mana
  crystal feeding eight finite reservoirs through radial conduits, with
  attenuation and elemental accents; no lettering.
- `spell_media.png` — three-way illuminated plate contrasting a scroll burning
  to ash, a reusable purple book, and a permanent carved stone tablet around a
  central sigil; no lettering.

The generated masters were visually inspected and downscaled with ffmpeg to
the checked-in game-ready dimensions. Alt text and explanatory prose live in
the guide JSON rather than being baked into the images, keeping the plates
localizable and accessible.
