# Vector-Regnum (The Realm of Direction)

> **Status: early prototype.** This repository is a standalone Java simulation of
> the spell compiler + virtual machine, not yet a loadable Minecraft mod. The
> classes under `net/minecraft/` are lightweight **mocks** so the engine can run
> and be tested outside the game. Everything below the "Design Vision" heading is
> the target design; the "Current Prototype" section documents what is actually
> implemented today. Run it with:
>
> ```
> javac -d out $(find . -name '*.java' -not -path './out/*')
> java -cp out vectorregnum.Main
> ```

## Current Prototype (implemented)

The prototype compiles an ordered list of `Sigil`s into VM instructions and
executes them against a `SpellState`; invalid logic "breaks" the spell and the
`WildMagicEngine` produces a context-aware chaotic effect. Implemented sigils:

- **`ORIGIN_SELF`** — ground the spell at the caster (must come first).
- **`ELEMENT_<name>`** — apply an element, e.g. `ELEMENT_FIRE`, `ELEMENT_FROST`.
- **`SHAPE_<name>`** — resolve a shape, e.g. `SHAPE_PROJECTILE`, `SHAPE_AURA`
  (`SHAPE_PROJECTILE` requires a direction vector first).
- **`VECTOR_FORWARD`** — set a direction (currently a `Vec3d.ZERO` placeholder).
- **`EXPAND <double>`** — grow the radius (needs a resolved shape).
- **`AMPLIFY <double>`** — multiply magnitude (needs an element or shape).
- **`EXECUTE`** — finalize; requires both an origin and a shape.

An unknown/typo'd sigil is a hard compile error that breaks the spell at that
sigil's position. `DIVIDE` (multi-casting) is defined but not yet implemented.

---

## Design Vision
*The sections below describe the intended full system. Most of it is **planned /
roadmap**, not yet implemented in the prototype above.*

## Concept
A hardcore "Programming Magic" mod where spells are constructed using geometric magic circles and sigils. The system relies on linear algebra, vector calculus, and logical sequencing to manifest reality-altering effects.

## Core Pillars
- **Sequencing:** Magic circles are read in a clockwise direction, moving inward.
- **Visual Compilation:** Spells use matrix transformations (Rotation, Scaling, Translation) for their animations, making every cast visually unique.
- **Hardcore Economy:** No natural mana regeneration. Mana is a finite resource extracted from Mana Crystals (the "Oil" of this magic world).
- **Failure States:** Unstable spell logic leads to unpredictable and dangerous "compiler errors" in-game.

## Implementation Methods — *planned*
1. **Carving (Stone Tablets):** High power, high cost, permanent installations.
2. **Writing (Scrolls):** Quick, weak, single-use.
3. **Prepping (Spell Books):** Balanced, repeatable, higher initial cost.

## Sigil System (Programming Logic) — *planned*
- **Star:** Start Command.
- **Create/CreateO:** Spawns objects/elements.
- **Form:** Defines the shape of the creation.
- **Logic Gates:** YES, NOT, AND, NAND, OR, NOR, XOR, XNOR for complex branching.
- **Control Flow:** If-Else, LoopFori, Select.
- **Spatial:** Direction, Pathfinding, MoveTowardsPoint, KeepDistance.

---
*Inspired by Hard Science and High Fantasy.*
