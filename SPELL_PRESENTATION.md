# Compiler-driven spell presentation

Vector-Regnum's spells are programs, so their presentation must be programmable
too. The target is not a library of fifteen hand-authored animations. Any valid,
player-authored spell should compile into a coherent, distinctive, mechanically
truthful sensory event.

The creative standard is **many quiet layers supporting one readable magical
gesture**. A spell should never feel like a gameplay operation with one particle
emitter attached. Even an ordinary cast should have anticipation, release,
physical presence, environmental response, sound, and a fading aftermath. Most
individual details may be barely noticed; together they should make magic feel
like it has entered and disturbed the world.

Examples include shifting illumination, true dynamic/cast shadows where the
renderer permits them and convincing shadow responses where it does not, local
screen darkness, fog or haze, wind and pressure, displaced dust, heat shimmer,
condensation, drifting residue, spatial sound, silence before impact, resonant
tails, camera or FOV response, hand recoil, material-dependent impacts, nearby
foliage or debris response, and persistent traces. These are a starting
vocabulary, not a limiting checklist. The presentation system should permit new
context-appropriate layers whenever they strengthen a spell's identity without
obscuring play.

## Compilation model

The authored circle should lower through a shared typed semantic representation
into two bounded outputs:

```text
authored circle
      -> typed semantic representation
           -> authoritative VM program
           -> presentation program

VM execution -> resolved visual events -> client presentation runtime
```

The server remains authoritative for selection, targets, timing, mana, damage,
movement, block changes, and all other gameplay. The presentation program is a
non-authoritative client recipe. Runtime events are emitted only for instructions
that actually execute, so branches, delays, loops, raycasts, and dynamically
selected entities stay synchronized with the spell rather than playing a guessed
static animation.

Do not generate arbitrary GLSL source for authored spells. Compile into a
bounded presentation IR interpreted by a curated library of reusable renderers,
shader passes, particle systems, meshes, sounds, and environmental responses.
Generic shaders receive parameters such as origin, target, normal, radius,
element, magnitude, duration, age, and deterministic seed. A rare signature
spell may use a depth-aware post-process or ray-marched primitive, but that is
one composable instrument rather than the entire visual system.

## Veil integration policy

[FoundryMC Veil](https://github.com/FoundryMC/Veil) is the selected optional
client rendering foundation for the NeoForge presentation overhaul. It sits
below Vector-Regnum's semantic module and compositor layer: Veil supplies
rendering infrastructure, while Vector-Regnum remains responsible for turning
arbitrary valid spell programs into coherent, bounded, mechanically truthful
compositions. The compiler and `PresentationProgram` stay loader-neutral.

The client selects either a built-in backend or the pinned Veil 4.4.1 backend
behind the same curated interface. One reflectively loaded client adapter owns
all Veil imports, so the default runtime and dedicated server never resolve
Veil classes. The bounded module vocabulary covers
particles, beams, ribbons, trails, runes, animated meshes, surfaces, volumes,
deferred lights, framebuffers, and optional post-processing, but authored
spells can address only Vector-Regnum's versioned module IDs and bounded typed
parameters. They cannot provide arbitrary Veil definitions, GLSL, resources,
packets, or executable code.

The priority-20a Veil adapter gives every mapped visual geometry family a
bounded Quasar motif. It also provides capped deferred lights and bloom where
compatible. Bespoke beam, ribbon, mesh, surface, and volume geometry remains a
release-art task; the built-in renderer supplies their mandatory truth shape in
the meantime. With Iris loaded, the adapter keeps Quasar but disables Veil
deferred lights and bloom because the full target scene exposed invalid
instanced draws on that combination.

That adapter is still incomplete. With Veil active, Veil/Quasar must own every
Vector-Regnum particle-based animation. Minecraft enchanting-table particles
(`ParticleTypes.ENCHANT`) are the only exception. Built-in particle animations
stay available for Veil-absent or failed fallback, but must not run beside the
active Veil version. Completion requires an inventory of every particle spawn
and resource path plus an automated allowlist check that permits only the
enchanting-table exception.

The built-in backend is not temporary scaffolding: it permanently owns the
minimum truth telegraph and graceful fallback. Veil absence or initialization
failure, resource reload failure, disabled post-processing, incompatible
shaderpacks, low graphics settings, and accessibility modes may remove
expressive layers but never origin, direction, affected area, timing,
allegiance, danger, or impact cues. The partial priority-20a adapter verified
resource reload, accessible low LOD, authored and library spells, backend
failure fallback, and the target Veil/Create/Sodium/Iris/Bliss pack. Repeat
those gates after the full particle migration.

A conceptual presentation program may contain:

```text
PresentationProgram
  telegraph and truth layer
  staged timeline and execution hooks
  particle emitters
  beams, ribbons, trails, runes, surfaces, and volumes
  lighting, darkness, fog, air, and material-response cues
  spatial sound and musical/resonance cues
  camera and screen-space cues
  impact and lingering-aftermath cues
  deterministic variation seed
  visual, audio, and post-processing budgets
```

## Semantic generation

Presentation defaults derive from what the program means:

- element controls palette, motion character, material language, sound family,
  atmospheric response, and decay;
- form controls the primary silhouette and render primitive, such as projectile,
  ray, ribbon, field, barrier, aura, volume, construct, or transformation;
- origin, direction, targets, paths, and selected sets bind effects to resolved
  world geometry;
- radius and range control truthful spatial boundaries;
- magnitude influences emphasis and intensity through bounded curves rather
  than unbounded particle counts;
- delay and duration create anticipation, sustain, and release timing;
- loops create readable repetitions, orbit counts, pulses, or layered echoes;
- perception and filters create searching, highlighting, connection, or
  rejection motifs;
- control flow can pulse or illuminate the authored circle as the VM actually
  advances;
- faults fracture or destabilize the same visual grammar instead of switching
  to an unrelated generic failure effect.

Arithmetic and memory operations need not each create a loud world effect, but
they may drive subtle glyph motion, orbit relationships, color transfer,
compression, branching, or other signs that the compiled program is working.
Stable seeds should make a spell recognizable while still providing organic
microvariation between casts.

## Truth and expression

Every presentation has two conceptual layers:

1. A mandatory, mechanics-derived truth layer communicates origin, direction,
   affected area, timing, allegiance, danger, and impact.
2. A bounded expressive layer allows authored cosmetic choices such as glyph
   family, trail character, rhythm, color accents, easing, and sound texture.

Cosmetic programming must not hide or materially exaggerate gameplay. Players
should be able to invent an aesthetic without making a small harmless spell
look like an unavoidable world-ending attack, concealing a damaging radius, or
removing a necessary warning.

## Choreography standard

Presentation is staged, not emitted as an undifferentiated particle cloud. The
available phases include invocation, gathering, tension, release, travel,
sustain, contact, impact, decay, and aftermath. A spell uses whichever phases
fit its semantics and may overlap them. Strong moments benefit from contrast:
quiet before force, darkness around a bright core, inward motion before an
outward strike, or a brief absence of sound before the impact transient.

Layer count is not an excuse for noise. Visual hierarchy must leave one clear
primary gesture, a few supporting secondary movements, and numerous restrained
microeffects. Details should respond to camera distance, environment, surface,
weather, medium, target type, and elemental interactions where practical.

## Runtime, safety, and accessibility

- Synchronize a compact presentation program or stable program identifier plus
  resolved runtime events; do not synchronize thousands of individual
  particles.
- Use deterministic seeds so each client can reproduce motion consistently.
- Track nearby players and respect dimensions, visibility, and unloads.
- Apply compile-time and runtime budgets for active instances, emitters,
  particles, mesh vertices, lights, sounds, screen coverage, post-process
  passes, ray-march steps, duration, and repetition.
- Budget Veil framebuffers, post passes, shader work, animated meshes, deferred
  lights, and resource lifetimes through the same per-program and global caps.
- Provide distance and quality LODs that preserve timing and telegraphs while
  reducing secondary detail.
- Make post-processing optional. The essential spell silhouette and warning
  must survive with post effects disabled or incompatible shaderpacks active.
- Provide independent controls for particle density, screen darkness/fog,
  flashes, chromatic effects, camera movement, audio intensity, and reduced
  motion/photosensitivity behavior.
- A hostile mechanic may influence aim/camera only when the authoritative
  gameplay permission policy permits it. It must have bounded range, ramp,
  angular speed, duration, and frequency; expose an accessibility-safe
  alternative cue; and never let a client presentation packet decide whether
  the gameplay effect succeeded. Disabling camera motion changes the sensory
  delivery, not server-side targeting or balance.
- Authored `Render`/output behavior selects only curated bounded capabilities.
  It cannot upload arbitrary shaders, sounds, files, packets, or executable
  code, and it cannot suppress mandatory truth telegraphs.
- Never rely on a third-party shaderpack for essential presentation. Dynamic
  shadow, darkness, glow, and atmospheric response need compatible fallbacks;
  optional integrations may enhance their quality.
- Keep all gameplay consequences server-authoritative and ensure disabling
  presentation cannot affect outcomes.

## Architectural consequence

The priority-19 Fabric alpha established the stable presentation IR, codec,
authoritative execution-event boundary, and first bounded client interpreter.
The NeoForge port preserves that compiler/runtime split. Priority 20a has a
partial Veil adapter behind the same interface while retaining the built-in backend.
Future original particles, models, shaders, sounds, UI effects,
coercive-attention mechanics, and environmental layers target that curated
boundary so they compose for library and player-authored spells without
creating an arbitrary-code path.
