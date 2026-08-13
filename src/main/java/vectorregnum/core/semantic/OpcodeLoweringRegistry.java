package vectorregnum.core.semantic;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Generic per-opcode backend used to lower the same semantics into VM or presentation IR. */
public final class OpcodeLoweringRegistry<T> {
    private final Map<SemanticOpcode, Rule<T>> rules;

    private OpcodeLoweringRegistry(Map<SemanticOpcode, Rule<T>> rules) {
        this.rules = Map.copyOf(rules);
    }

    public static <T> Builder<T> builder() { return new Builder<>(); }

    public Set<SemanticOpcode> missingOpcodes() {
        EnumSet<SemanticOpcode> missing = EnumSet.allOf(SemanticOpcode.class);
        missing.removeAll(rules.keySet());
        return Set.copyOf(missing);
    }

    public LoweringResult<T> lower(SemanticProgram program, LoweringContext context) {
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(context, "context");
        List<T> output = new ArrayList<>();
        List<LoweringDiagnostic> diagnostics = new ArrayList<>();
        for (int index = 0; index < program.instructions().size(); index++) {
            SemanticInstruction instruction = program.instructions().get(index);
            Rule<T> rule = rules.get(instruction.opcode());
            if (rule == null) {
                diagnostics.add(new LoweringDiagnostic(LoweringDiagnostic.Code.MISSING_OPCODE_LOWERER,
                        "No lowering rule registered for " + instruction.opcode(), instruction.source(), index));
                continue;
            }
            try {
                List<T> lowered = List.copyOf(Objects.requireNonNull(
                        rule.lower(instruction, context), "lowering result"));
                if (lowered.stream().anyMatch(Objects::isNull)) {
                    throw new NullPointerException("lowering result contains null");
                }
                output.addAll(lowered);
            } catch (IllegalArgumentException exception) {
                diagnostics.add(new LoweringDiagnostic(LoweringDiagnostic.Code.INVALID_OPERAND,
                        safeMessage(exception), instruction.source(), index));
            } catch (RuntimeException exception) {
                diagnostics.add(new LoweringDiagnostic(LoweringDiagnostic.Code.LOWERING_FAILED,
                        safeMessage(exception), instruction.source(), index));
            }
        }
        return new LoweringResult<>(output, diagnostics);
    }

    private static String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    @FunctionalInterface
    public interface Rule<T> {
        /** One semantic opcode may expand into zero or more ordered backend instructions. */
        List<T> lower(SemanticInstruction instruction, LoweringContext context);
    }

    public static final class Builder<T> {
        private final EnumMap<SemanticOpcode, Rule<T>> rules = new EnumMap<>(SemanticOpcode.class);

        public Builder<T> register(SemanticOpcode opcode, Rule<T> rule) {
            Objects.requireNonNull(opcode, "opcode");
            Objects.requireNonNull(rule, "rule");
            if (rules.putIfAbsent(opcode, rule) != null) {
                throw new IllegalArgumentException("duplicate lowering rule for " + opcode);
            }
            return this;
        }

        public Builder<T> registerSingle(SemanticOpcode opcode,
                java.util.function.BiFunction<SemanticInstruction, LoweringContext, T> rule) {
            Objects.requireNonNull(rule, "rule");
            return register(opcode, (instruction, context) ->
                    List.of(Objects.requireNonNull(rule.apply(instruction, context), "lowering result")));
        }

        public OpcodeLoweringRegistry<T> build() {
            return new OpcodeLoweringRegistry<>(rules);
        }
    }
}
