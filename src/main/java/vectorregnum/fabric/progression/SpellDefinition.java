package vectorregnum.fabric.progression;

import java.util.List;
import java.util.Set;

public record SpellDefinition(
        String id,
        String title,
        SpellCategory category,
        int tier,
        double estimatedMana,
        Set<ProgressionUnlock> requiredUnlocks,
        List<SpellInstruction> program) {
    public SpellDefinition {
        if (!id.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Invalid spell id: " + id);
        }
        if (title.isBlank()) {
            throw new IllegalArgumentException("Spell title cannot be blank");
        }
        if (tier < 1 || tier > 5) {
            throw new IllegalArgumentException("Spell tier must be between 1 and 5");
        }
        if (!Double.isFinite(estimatedMana) || estimatedMana <= 0.0) {
            throw new IllegalArgumentException("Estimated mana must be finite and positive");
        }
        requiredUnlocks = Set.copyOf(requiredUnlocks);
        program = List.copyOf(program);
        if (program.isEmpty() || program.getLast().opcode() != LibraryOpcode.EXECUTE) {
            throw new IllegalArgumentException("A library spell must end with EXECUTE");
        }
    }

    public boolean isUnlocked(ProgressionState progression) {
        return progression.hasAll(requiredUnlocks);
    }
}
