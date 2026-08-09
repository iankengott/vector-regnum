package vectorregnum.core;

import java.util.List;
import java.util.Optional;

/** Deterministic compatibility VM. It has no loader or world dependencies. */
public final class SpellEngine {
    public CastResult cast(CompiledSpell program, CastContext context) {
        if (program == null) {
            return new CastResult.EngineFailure("NULL_PROGRAM", "Compiled spell cannot be null");
        }
        if (context == null) {
            return new CastResult.EngineFailure("NULL_CONTEXT", "Cast context cannot be null");
        }

        ExecutionState state = new ExecutionState();
        for (Instruction instruction : program.instructions()) {
            if (state.executed) {
                return spellFailure(program, context, state,
                        FaultCode.INSTRUCTION_AFTER_EXECUTE,
                        "EXECUTE must be the final sigil", instruction.sourceIndex());
            }
            if (instruction.opcode() == Opcode.FAULT) {
                return spellFailure(program, context, state,
                        instruction.faultCode(), instruction.faultMessage(), instruction.sourceIndex());
            }

            CastResult.SpellFailure failure = executeInstruction(program, context, state, instruction);
            if (failure != null) {
                return failure;
            }
        }

        if (!state.executed) {
            return spellFailure(program, context, state, FaultCode.MISSING_EXECUTE,
                    "Spell has no terminal EXECUTE sigil", program.sourceSize());
        }

        if (state.origin == null || state.shape == null) {
            return new CastResult.EngineFailure(
                    "INVALID_FINAL_STATE", "Executed spell lacks required final state");
        }

        SpellSnapshot snapshot = new SpellSnapshot(
                state.origin,
                Optional.ofNullable(state.direction),
                Optional.ofNullable(state.element),
                state.shape,
                state.radius,
                state.magnitude);
        EffectCommand command = structuredEffect(context, snapshot);
        return new CastResult.Success(program, snapshot, List.of(command));
    }

    private CastResult.SpellFailure executeInstruction(
            CompiledSpell program,
            CastContext context,
            ExecutionState state,
            Instruction instruction) {
        return switch (instruction.opcode()) {
            case SET_ORIGIN -> {
                if (state.origin != null) {
                    yield spellFailure(program, context, state,
                            FaultCode.ORIGIN_ALREADY_SET, "Origin is already set", instruction.sourceIndex());
                }
                state.origin = context.origin();
                yield null;
            }
            case SET_VECTOR -> {
                if (state.origin == null) {
                    yield spellFailure(program, context, state,
                            FaultCode.ORIGIN_REQUIRED,
                            "Origin must be set before direction", instruction.sourceIndex());
                }
                if (state.direction != null) {
                    yield spellFailure(program, context, state,
                            FaultCode.VECTOR_ALREADY_SET, "Direction is already set", instruction.sourceIndex());
                }
                if (context.lookDirection().isEffectivelyZero()) {
                    yield spellFailure(program, context, state,
                            FaultCode.DIRECTION_REQUIRED,
                            "Caster look direction cannot be zero", instruction.sourceIndex());
                }
                state.direction = context.lookDirection().normalized();
                yield null;
            }
            case APPLY_ELEMENT -> {
                if (state.origin == null) {
                    yield spellFailure(program, context, state,
                            FaultCode.ORIGIN_REQUIRED,
                            "Origin must be set before applying an element", instruction.sourceIndex());
                }
                if (state.element != null) {
                    yield spellFailure(program, context, state,
                            FaultCode.ELEMENT_ALREADY_SET, "Element is already applied", instruction.sourceIndex());
                }
                state.element = instruction.element();
                yield null;
            }
            case RESOLVE_SHAPE -> {
                if (state.origin == null) {
                    yield spellFailure(program, context, state,
                            FaultCode.ORIGIN_REQUIRED,
                            "Origin must be set before resolving a shape", instruction.sourceIndex());
                }
                if (state.shape != null) {
                    yield spellFailure(program, context, state,
                            FaultCode.SHAPE_ALREADY_SET, "Shape is already resolved", instruction.sourceIndex());
                }
                if (instruction.shape() == Shape.PROJECTILE && state.direction == null) {
                    yield spellFailure(program, context, state,
                            FaultCode.DIRECTION_REQUIRED,
                            "Projectile shape requires a direction", instruction.sourceIndex());
                }
                state.shape = instruction.shape();
                yield null;
            }
            case EXPAND_AREA -> {
                if (state.shape == null) {
                    yield spellFailure(program, context, state,
                            FaultCode.SHAPE_REQUIRED,
                            "A shape must be resolved before expansion", instruction.sourceIndex());
                }
                double radius = state.radius + instruction.scalar();
                if (!Double.isFinite(radius) || radius <= 0.0) {
                    yield spellFailure(program, context, state,
                            FaultCode.NUMERIC_OVERFLOW,
                            "Expansion produced an invalid radius", instruction.sourceIndex());
                }
                state.radius = radius;
                yield null;
            }
            case AMPLIFY -> {
                if (state.shape == null && state.element == null) {
                    yield spellFailure(program, context, state,
                            FaultCode.NOTHING_TO_AMPLIFY,
                            "No element or shape exists to amplify", instruction.sourceIndex());
                }
                double magnitude = state.magnitude * instruction.scalar();
                if (!Double.isFinite(magnitude) || magnitude <= 0.0) {
                    yield spellFailure(program, context, state,
                            FaultCode.NUMERIC_OVERFLOW,
                            "Amplification produced an invalid magnitude", instruction.sourceIndex());
                }
                state.magnitude = magnitude;
                yield null;
            }
            case EXECUTE_EFFECT -> {
                if (state.origin == null || state.shape == null) {
                    yield spellFailure(program, context, state,
                            FaultCode.MISSING_COMPONENT,
                            "Spell lacks an origin or shape before execution", instruction.sourceIndex());
                }
                state.executed = true;
                yield null;
            }
            case FAULT -> throw new IllegalStateException("FAULT instruction bypassed dispatch guard");
        };
    }

    private static EffectCommand structuredEffect(CastContext context, SpellSnapshot snapshot) {
        return switch (snapshot.shape()) {
            case PROJECTILE -> new EffectCommand.Projectile(
                    context.casterId(), snapshot.origin(), snapshot.direction().orElseThrow(),
                    snapshot.element(), snapshot.radius(), snapshot.magnitude());
            case AURA -> new EffectCommand.Aura(
                    context.casterId(), snapshot.origin(), snapshot.element(),
                    snapshot.radius(), snapshot.magnitude());
        };
    }

    private static CastResult.SpellFailure spellFailure(
            CompiledSpell program,
            CastContext context,
            ExecutionState state,
            FaultCode code,
            String message,
            int sourceIndex) {
        WildMagicCategory category = state.origin == null
                ? WildMagicCategory.INTERNAL_MANA_DETONATION
                : state.shape == null
                        ? WildMagicCategory.UNSTRUCTURED_ELEMENT_BURST
                        : WildMagicCategory.VIOLENT_MISCAST;
        SpellFault fault = new SpellFault(code, message, sourceIndex, category);
        EffectCommand.WildMagic effect = new EffectCommand.WildMagic(
                context.casterId(),
                category,
                Optional.ofNullable(state.origin),
                Optional.ofNullable(state.element),
                Optional.ofNullable(state.shape),
                sourceIndex,
                message,
                mixSeed(context.randomSeed(), sourceIndex, category));
        return new CastResult.SpellFailure(program, fault, List.of(effect));
    }

    private static long mixSeed(long seed, int sourceIndex, WildMagicCategory category) {
        long value = seed ^ (0x9E3779B97F4A7C15L * (sourceIndex + 1L));
        value ^= 0xBF58476D1CE4E5B9L * (category.ordinal() + 1L);
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static final class ExecutionState {
        private Vec3 origin;
        private Vec3 direction;
        private Element element;
        private Shape shape;
        private double radius;
        private double magnitude = 1.0;
        private boolean executed;
    }
}
