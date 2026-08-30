package vectorregnum.core.vm2;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Result of exactly one server tick; effects contains only commands emitted this tick. */
public record TickResult(Status status, int instructionPointer, int instructionsExecuted,
        List<WorldEffect> effects, List<VmMessage> messages, Optional<VmFault> fault) {
    public TickResult(Status status, int instructionPointer, int instructionsExecuted,
            List<WorldEffect> effects, Optional<VmFault> fault) {
        this(status, instructionPointer, instructionsExecuted, effects, List.of(), fault);
    }

    public TickResult {
        Objects.requireNonNull(status, "status");
        effects = List.copyOf(Objects.requireNonNull(effects, "effects"));
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        fault = Objects.requireNonNull(fault, "fault");
        if (instructionPointer < 0 || instructionsExecuted < 0) throw new IllegalArgumentException("negative result field");
        if ((status == Status.FAULTED) != fault.isPresent()) throw new IllegalArgumentException("fault/status mismatch");
    }

    public enum Status { RUNNING, WAITING, BUDGET_YIELD, HALTED, FAULTED }
}
