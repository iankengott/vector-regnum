package vectorregnum.core.presentation;

import java.util.List;
import java.util.Objects;

/** Immutable, renderer-neutral presentation IR validated against explicit budgets. */
public record PresentationProgram(String id, long deterministicSeed,
        List<PresentationInstruction> instructions, PresentationBudget budget) {
    private static final String ID_PATTERN = "[a-z0-9_.-]+:[a-z0-9_./-]+";

    public PresentationProgram {
        Objects.requireNonNull(id, "id");
        instructions = List.copyOf(Objects.requireNonNull(instructions, "instructions"));
        Objects.requireNonNull(budget, "budget");
        if (!id.matches(ID_PATTERN) || id.length() > 128) {
            throw new IllegalArgumentException("invalid presentation id");
        }
        if (instructions.size() > budget.maximumCues()) {
            throw new IllegalArgumentException("presentation cue count exceeds budget");
        }
        PresentationCost total = PresentationCost.ZERO;
        for (PresentationInstruction instruction : instructions) {
            Objects.requireNonNull(instruction, "instruction");
            if ((long) instruction.startOffsetTicks() + instruction.durationTicks()
                    > budget.maximumDurationTicks()) {
                throw new IllegalArgumentException("presentation duration exceeds budget");
            }
            total = total.plus(instruction.cost());
        }
        if (!budget.allows(total)) throw new IllegalArgumentException("presentation resource cost exceeds budget");
        if (!instructions.isEmpty() && instructions.stream().noneMatch(PresentationInstruction::truthLayer)) {
            throw new IllegalArgumentException("presentation requires a mechanics-derived truth cue");
        }
    }

    public PresentationCost declaredCost() {
        return instructions.stream().map(PresentationInstruction::cost)
                .reduce(PresentationCost.ZERO, PresentationCost::plus);
    }
}
