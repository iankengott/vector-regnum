package vectorregnum.core.presentation;

import java.util.List;
import java.util.Objects;
import vectorregnum.core.vm2.Opcode;
import vectorregnum.core.vm2.RuntimeValue;
import vectorregnum.core.vm2.SourceLocation;
import vectorregnum.core.vm2.VmFault;
import vectorregnum.core.vm2.WorldEffect;

/** Compact authoritative VM events used to resolve presentation hooks. */
public sealed interface ExecutionEvent permits ExecutionEvent.Started, ExecutionEvent.StepExecuted,
        ExecutionEvent.ValuesResolved, ExecutionEvent.DelayStarted,
        ExecutionEvent.WorldEffectEmitted, ExecutionEvent.Halted, ExecutionEvent.Faulted {
    long sequence();
    long tick();

    record Started(long sequence, long tick) implements ExecutionEvent {
        public Started { validate(sequence, tick); }
    }

    record StepExecuted(long sequence, long tick, int branchId, int instructionPointer,
            int nextInstructionPointer, Opcode opcode, SourceLocation source) implements ExecutionEvent {
        public StepExecuted(long sequence, long tick, int instructionPointer,
                int nextInstructionPointer, Opcode opcode, SourceLocation source) {
            this(sequence, tick, 0, instructionPointer, nextInstructionPointer, opcode, source);
        }

        public StepExecuted {
            validate(sequence, tick);
            if (branchId < 0 || instructionPointer < 0 || nextInstructionPointer < 0) {
                throw new IllegalArgumentException("negative branch/pointer");
            }
            Objects.requireNonNull(opcode, "opcode");
            Objects.requireNonNull(source, "source");
        }
    }

    record ValuesResolved(long sequence, long tick, int instructionPointer,
            Opcode opcode, SourceLocation source, List<RuntimeValue> values) implements ExecutionEvent {
        public ValuesResolved {
            validate(sequence, tick);
            if (instructionPointer < 0) throw new IllegalArgumentException("negative pointer");
            Objects.requireNonNull(opcode, "opcode");
            Objects.requireNonNull(source, "source");
            values = List.copyOf(Objects.requireNonNull(values, "values"));
        }
    }

    record DelayStarted(long sequence, long tick, int instructionPointer,
            SourceLocation source, int delayTicks) implements ExecutionEvent {
        public DelayStarted {
            validate(sequence, tick);
            if (instructionPointer < 0 || delayTicks < 1) throw new IllegalArgumentException("invalid delay event");
            Objects.requireNonNull(source, "source");
        }
    }

    record WorldEffectEmitted(long sequence, long tick, int instructionPointer,
            SourceLocation source, WorldEffect effect) implements ExecutionEvent {
        public WorldEffectEmitted {
            validate(sequence, tick);
            if (instructionPointer < 0) throw new IllegalArgumentException("negative pointer");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(effect, "effect");
        }
    }

    record Halted(long sequence, long tick, int instructionPointer,
            SourceLocation source) implements ExecutionEvent {
        public Halted {
            validate(sequence, tick);
            if (instructionPointer < 0) throw new IllegalArgumentException("negative pointer");
            Objects.requireNonNull(source, "source");
        }
    }

    record Faulted(long sequence, long tick, VmFault fault) implements ExecutionEvent {
        public Faulted { validate(sequence, tick); Objects.requireNonNull(fault, "fault"); }
    }

    private static void validate(long sequence, long tick) {
        if (sequence < 0 || tick < 0) throw new IllegalArgumentException("negative event sequence/tick");
    }
}
