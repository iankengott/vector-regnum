package vectorregnum.core;

import java.util.List;
import java.util.Objects;

/** Explicit outcome channel: authored spell faults never masquerade as engine faults. */
public sealed interface CastResult
        permits CastResult.Success, CastResult.SpellFailure, CastResult.EngineFailure {

    Status status();

    List<EffectCommand> effects();

    double manaCost();

    long complexity();

    enum Status {
        SUCCESS,
        SPELL_FAULT,
        ENGINE_FAULT
    }

    record Success(
            CompiledSpell program,
            SpellSnapshot snapshot,
            List<EffectCommand> effects) implements CastResult {
        public Success {
            Objects.requireNonNull(program, "program");
            Objects.requireNonNull(snapshot, "snapshot");
            effects = List.copyOf(effects);
            if (effects.size() != 1
                    || effects.getFirst() instanceof EffectCommand.WildMagic) {
                throw new IllegalArgumentException("Successful cast must contain one structured effect");
            }
        }

        @Override
        public Status status() {
            return Status.SUCCESS;
        }

        @Override
        public double manaCost() {
            return program.totalManaCost();
        }

        @Override
        public long complexity() {
            return program.totalComplexity();
        }
    }

    record SpellFailure(
            CompiledSpell program,
            SpellFault fault,
            List<EffectCommand> effects) implements CastResult {
        public SpellFailure {
            Objects.requireNonNull(program, "program");
            Objects.requireNonNull(fault, "fault");
            effects = List.copyOf(effects);
            if (effects.size() != 1
                    || !(effects.getFirst() instanceof EffectCommand.WildMagic)) {
                throw new IllegalArgumentException("Spell fault must contain one Wild Magic effect");
            }
            EffectCommand.WildMagic wildMagic = (EffectCommand.WildMagic) effects.getFirst();
            if (wildMagic.category() != fault.wildMagicCategory()
                    || wildMagic.sourceIndex() != fault.sourceIndex()) {
                throw new IllegalArgumentException("Wild Magic command must match its spell fault");
            }
        }

        @Override
        public Status status() {
            return Status.SPELL_FAULT;
        }

        @Override
        public double manaCost() {
            return program.totalManaCost();
        }

        @Override
        public long complexity() {
            return program.totalComplexity();
        }
    }

    record EngineFailure(String code, String message) implements CastResult {
        public EngineFailure {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
            if (code.isBlank() || message.isBlank()) {
                throw new IllegalArgumentException("Engine fault code and message cannot be blank");
            }
        }

        @Override
        public Status status() {
            return Status.ENGINE_FAULT;
        }

        @Override
        public List<EffectCommand> effects() {
            return List.of();
        }

        @Override
        public double manaCost() {
            return 0.0;
        }

        @Override
        public long complexity() {
            return 0L;
        }
    }
}
