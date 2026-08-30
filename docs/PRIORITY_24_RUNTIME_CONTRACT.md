# Priority 24 runtime contract

This is the loader-neutral executable contract for advanced shared-memory spell
control. It narrows the recovered decisions in
[`SMP_INTEGRATION_DECISIONS.md`](SMP_INTEGRATION_DECISIONS.md) into the exact
operation, ordering, bound, and failure rules used by vm2 and NeoForge.

## Authority and lifetime

- One immutable `Program` owns one ephemeral `SpellVm` control state: the
  shared stack, typed variables, iterators, watchers, messages, and branch table.
  None of this state is written to NBT, `SavedData`, media, or a resume payload.
- Only the authoritative server tick advances branches or mutates shared state.
  Logical branches are not Java threads and vm2 contains no executor, future,
  parallel stream, or other off-thread world access.
- Server stop and the existing owner lifecycle/deadline paths cancel the whole
  VM and settle its casting escrow through the existing policy. Restart never
  resumes the discarded control state.
- Existing `Push` and `Pop` use the same shared bounded LIFO stack for the main
  path and every child. Each instruction commits atomically in scheduler order
  and receives an authoritative trace step with its branch ID.

## Append-only operations

Priority 24 appends these opcodes after the priority-23 vocabulary. Existing
opcode and presentation ordinals are never renumbered. Names match
`[a-z][a-z0-9_]{0,31}` and all targets and declared bounds are validated before
execution.

### Typed variables

- `STORE_VARIABLE(name)` peeks one immutable runtime value, validates the slot,
  stable type, watcher, and signal capacity, then atomically pops and stores it.
  A new name consumes one of 64 variable slots. An existing variable cannot
  change runtime type.
- `LOAD_VARIABLE(name)` pushes the current value. A missing name faults with
  `VARIABLE_NOT_FOUND`; it never pushes null or a default.
- A failed variable operation leaves the stack and variable map unchanged.

### Structured iterators

- `ITERATOR_BEGIN(name, exitTarget, maximumSteps)` pops an immutable `ListValue`.
  An empty list jumps to `exitTarget`; otherwise it opens one of at most 16 live
  iterators, pushes the first item, and yields the VM for the tick.
- `ITERATOR_NEXT(name, bodyTarget)` removes an exhausted iterator and falls
  through, or pushes exactly one next item, jumps to `bodyTarget`, and yields.
  Consequently one iterator exposes at most one item per authoritative tick.
- The program validator requires a structured begin/body/next region. The
  iterator's declared maximum and the VM-wide 1,024-step lifetime cap are both
  enforced. Unknown, cross-branch, duplicate, or overlong iterators fail closed.

### Collision

- `COLLISION(declaredRange, samples)` consumes any pair of entity references or
  points and pushes one boolean. It delegates the read-only query to
  `WorldAccess.collides`; core code never receives a Minecraft object.
- The NeoForge adapter resolves entities in the owner's dimension, requires
  loaded endpoints inside the world border and declared range, and applies a
  bounded geometric test. Missing entities, invalid types, excessive range,
  and adapter failures produce bounded faults without partially consuming the
  operands.

### Watchers, signals, and output

- `WATCH_VARIABLE(variable, declaredRange)` pops a point and installs or replaces
  the watcher for an existing variable. At most 32 variables may be watched.
  Repeating a registration is bounded and idempotent with respect to capacity.
- A later unequal `STORE_VARIABLE` emits one signal at the stored watcher point
  with the changed typed value. Equal stores emit nothing. Signal capacity is
  checked before the variable and stack transaction commits.
- `SIGNAL(declaredRange)` consumes a typed payload followed by a point and emits
  one authoritative `VmMessage.Signal` on channel `signal`.
- `OUTPUT(declaredRange)` consumes a typed payload followed by a point, converts
  it to a bounded canonical string, and emits one owner-visible
  `VmMessage.Output`. Output is plain text; it cannot execute commands or select
  a different recipient.
- Each VM permits at most 128 signals, 64 outputs, and 256 total output
  characters. Messages carry a monotonic sequence, authoritative tick, branch
  ID, position, and declared range. A same-tick fault returns no staged message
  batch to the adapter.

### Logical branches

- `FORK(name, start, endExclusive)` creates a validated child body ending in
  `BRANCH_END`. The child receives a monotonic ID and cannot execute in the same
  tick as its fork.
- Once any child exists, the scheduler snapshots active branches in ascending
  creation ID and advances each by at most one instruction per server tick.
  This is the only form of parallelism.
- `JOIN` waits until every active named child has ended or been cancelled, then
  advances once. `CANCEL_BRANCH(name)` ends an active named child; repeating it
  after completion is an idempotent no-op. `BRANCH_END` terminates only a child.
  The main path cannot halt or end while children remain active.
- The main path counts toward both branch limits: at most 8 active branches and
  32 branches created over the VM lifetime. Branches also share the existing
  per-tick instruction, total-work, lifetime, and stack-depth caps.

## Static analysis and costing

- `Program` rejects backward raw jumps, malformed or crossing iterator regions,
  nested or overlapping child bodies, missing joins, control-flow edges that
  enter or escape child regions, and non-terminal child bodies.
- `StackTypeAnalyzer` tracks definite variable assignment and stable types,
  validates the priority-24 stack contracts, and requires branch bodies to be
  stack-neutral so a deterministic shared stack rejoins cleanly.
- `ManaCostModel` receives every priority-24 instruction's declared memory,
  range, perception, and repeated-control work. Iterator bodies and child
  bodies contribute conservative worst-case cost before admission.
- Runtime faults retain the exact source sigil. Presentation may compact or omit
  expressive layers, but it must preserve a bounded textual truth cue and can
  never affect scheduling, messages, collision, mana, or world outcomes.

## Acceptance ladder

Priority 24 is complete only after the parent runs the repository's documented
Hermes-only ladder on the final candidate:

1. Guarded sync and Java 21 `scripts/hermes-build.sh` for the complete JUnit
   suite.
2. The real NeoForge `runGameTestServer` matrix, including the priority-24 happy
   path, bounds, adapter, lifecycle, and registration-parity coverage.
3. The Hermes overlay diff check against a fresh public clone.
4. Guarded Hermes client integration, required priority-24 readiness markers,
   screenshot capture, and direct inspection of the bounded owner output and
   truth trace.
5. Stop both development units and prove loopback port `25575` is free.

NixOS builds, game launches, and visual-artifact inspection remain
approval-only.
