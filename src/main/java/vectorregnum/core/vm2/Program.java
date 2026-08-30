package vectorregnum.core.vm2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable validated bytecode plus its deterministic pre-cast mana quote. */
public final class Program {
    public static final int MAX_INSTRUCTIONS = 4_096;
    private final List<Instruction> instructions;
    private final ManaCostModel.Input declaredCost;
    private final ManaCostModel.Breakdown manaCost;

    public Program(List<Instruction> instructions) {
        this.instructions = copyAndValidate(instructions);
        this.declaredCost = aggregate(this.instructions);
        this.manaCost = ManaCostModel.estimate(declaredCost);
    }

    public List<Instruction> instructions() { return instructions; }
    public ManaCostModel.Input declaredCost() { return declaredCost; }
    public ManaCostModel.Breakdown manaCost() { return manaCost; }

    private static List<Instruction> copyAndValidate(List<Instruction> input) {
        List<Instruction> copy = List.copyOf(Objects.requireNonNull(input, "instructions"));
        if (copy.size() > MAX_INSTRUCTIONS) {
            throw new IllegalArgumentException("program exceeds " + MAX_INSTRUCTIONS + " instructions");
        }
        for (int index = 0; index < copy.size(); index++) {
            Instruction instruction = Objects.requireNonNull(copy.get(index), "instruction");
            if (instruction.opcode() == Opcode.JUMP || instruction.opcode() == Opcode.JUMP_IF_FALSE
                    || instruction.opcode() == Opcode.LOOP) {
                if (instruction.argument() >= copy.size()) {
                    throw new IllegalArgumentException("jump target outside program at " + index);
                }
            }
            if (instruction.opcode() == Opcode.LOOP && instruction.argument() >= index) {
                throw new IllegalArgumentException("LOOP must target an earlier body instruction");
            }
            if ((instruction.opcode() == Opcode.JUMP
                    || instruction.opcode() == Opcode.JUMP_IF_FALSE)
                    && instruction.argument() <= index) {
                throw new IllegalArgumentException("backward control flow requires bounded LOOP");
            }
        }
        validateIterators(copy);
        validateBranches(copy);
        return copy;
    }

    private static void validateIterators(List<Instruction> code) {
        Map<String, Integer> begins = new LinkedHashMap<>();
        Map<String, Integer> nexts = new LinkedHashMap<>();
        for (int index = 0; index < code.size(); index++) {
            Instruction instruction = code.get(index);
            if (instruction.opcode() == Opcode.ITERATOR_BEGIN) {
                AdvancedOperand.IteratorSpec spec = iterator(instruction);
                if (begins.putIfAbsent(spec.name(), index) != null) {
                    throw new IllegalArgumentException("duplicate iterator " + spec.name());
                }
            } else if (instruction.opcode() == Opcode.ITERATOR_NEXT) {
                AdvancedOperand.IteratorSpec spec = iterator(instruction);
                if (nexts.putIfAbsent(spec.name(), index) != null) {
                    throw new IllegalArgumentException("duplicate iterator next " + spec.name());
                }
            }
        }
        List<Range> ranges = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : begins.entrySet()) {
            String name = entry.getKey();
            int begin = entry.getValue();
            Integer next = nexts.get(name);
            if (next == null || next <= begin) {
                throw new IllegalArgumentException("iterator " + name + " has no later NEXT");
            }
            AdvancedOperand.IteratorSpec beginSpec = iterator(code.get(begin));
            AdvancedOperand.IteratorSpec nextSpec = iterator(code.get(next));
            if (beginSpec.target() != next + 1 || nextSpec.target() != begin + 1
                    || beginSpec.target() >= code.size()) {
                throw new IllegalArgumentException("iterator " + name
                        + " must target its body and instruction after NEXT");
            }
            ranges.add(new Range(begin, next + 1, name));
        }
        for (String name : nexts.keySet()) {
            if (!begins.containsKey(name)) {
                throw new IllegalArgumentException("iterator NEXT has no BEGIN: " + name);
            }
        }
        ranges.sort(java.util.Comparator.comparingInt(Range::start));
        for (int index = 1; index < ranges.size(); index++) {
            if (ranges.get(index).start() < ranges.get(index - 1).end()) {
                throw new IllegalArgumentException("iterator regions cannot overlap");
            }
        }
    }

    private static void validateBranches(List<Instruction> code) {
        List<ForkAt> forks = new ArrayList<>();
        Set<String> names = new HashSet<>();
        List<Integer> joins = new ArrayList<>();
        for (int index = 0; index < code.size(); index++) {
            Instruction instruction = code.get(index);
            if (instruction.opcode() == Opcode.FORK) {
                AdvancedOperand.ForkSpec spec = fork(instruction);
                if (!names.add(spec.name())) {
                    throw new IllegalArgumentException("duplicate branch " + spec.name());
                }
                if (spec.start() >= code.size() || spec.endExclusive() > code.size()
                        || spec.start() <= index
                        || code.get(spec.endExclusive() - 1).opcode() != Opcode.BRANCH_END) {
                    throw new IllegalArgumentException("branch " + spec.name()
                            + " must end with BRANCH_END inside the program");
                }
                forks.add(new ForkAt(index, spec));
            } else if (instruction.opcode() == Opcode.JOIN) {
                joins.add(index);
            }
        }
        if (forks.isEmpty()) {
            boolean stray = code.stream().anyMatch(instruction -> instruction.opcode() == Opcode.JOIN
                    || instruction.opcode() == Opcode.CANCEL_BRANCH
                    || instruction.opcode() == Opcode.BRANCH_END);
            if (stray) throw new IllegalArgumentException("branch operation has no FORK");
            return;
        }
        if (joins.size() != 1) throw new IllegalArgumentException("forked program needs exactly one JOIN");
        int join = joins.getFirst();
        int minimumStart = forks.stream().mapToInt(value -> value.spec().start()).min().orElseThrow();
        int maximumEnd = forks.stream().mapToInt(value -> value.spec().endExclusive()).max().orElseThrow();
        int maximumFork = forks.stream().mapToInt(ForkAt::index).max().orElseThrow();
        if (join <= maximumFork || join + 1 >= minimumStart
                || code.get(join + 1).opcode() != Opcode.JUMP
                || code.get(join + 1).argument() != maximumEnd
                || minimumStart != join + 2) {
            throw new IllegalArgumentException(
                    "JOIN must follow all FORKs and jump over contiguous branch bodies");
        }
        List<Range> ranges = forks.stream()
                .map(value -> new Range(value.spec().start(), value.spec().endExclusive(),
                        value.spec().name()))
                .sorted(java.util.Comparator.comparingInt(Range::start)).toList();
        for (int index = 1; index < ranges.size(); index++) {
            if (ranges.get(index - 1).end() != ranges.get(index).start()) {
                throw new IllegalArgumentException("branch bodies must be contiguous and non-overlapping");
            }
        }
        for (int pointer = 0; pointer < code.size(); pointer++) {
            Instruction instruction = code.get(pointer);
            Range containing = containing(ranges, pointer);
            if (containing != null) {
                if (instruction.opcode() == Opcode.FORK || instruction.opcode() == Opcode.JOIN
                        || instruction.opcode() == Opcode.HALT) {
                    throw new IllegalArgumentException("nested fork/join/halt is not allowed in branch "
                            + containing.name());
                }
                if (instruction.opcode() == Opcode.BRANCH_END
                        && pointer != containing.end() - 1) {
                    throw new IllegalArgumentException("BRANCH_END must terminate its branch");
                }
                Integer target = controlTarget(instruction);
                if (target != null && (target < containing.start()
                        || target >= containing.end())) {
                    throw new IllegalArgumentException("control flow cannot leave branch "
                            + containing.name());
                }
            } else {
                if (instruction.opcode() == Opcode.BRANCH_END) {
                    throw new IllegalArgumentException("BRANCH_END outside a branch");
                }
                Integer target = controlTarget(instruction);
                if (target != null && containing(ranges, target) != null) {
                    throw new IllegalArgumentException("control flow cannot enter a branch body");
                }
            }
            if (instruction.opcode() == Opcode.CANCEL_BRANCH) {
                String name = named(instruction);
                if (!names.contains(name)) {
                    throw new IllegalArgumentException("unknown branch " + name);
                }
            }
        }
        for (int pointer = 0; pointer < code.size(); pointer++) {
            if (code.get(pointer).opcode() != Opcode.LOOP) continue;
            for (int body = code.get(pointer).argument(); body < pointer; body++) {
                if (code.get(body).opcode() == Opcode.FORK) {
                    throw new IllegalArgumentException("FORK cannot be repeated by LOOP");
                }
            }
        }
    }

    private static boolean isJump(Instruction instruction) {
        return instruction.opcode() == Opcode.JUMP
                || instruction.opcode() == Opcode.JUMP_IF_FALSE
                || instruction.opcode() == Opcode.LOOP;
    }

    private static Integer controlTarget(Instruction instruction) {
        if (isJump(instruction)) return instruction.argument();
        if (instruction.opcode() == Opcode.ITERATOR_BEGIN
                || instruction.opcode() == Opcode.ITERATOR_NEXT) {
            return iterator(instruction).target();
        }
        return null;
    }

    private static Range containing(List<Range> ranges, int pointer) {
        return ranges.stream().filter(range -> pointer >= range.start() && pointer < range.end())
                .findFirst().orElse(null);
    }

    private static AdvancedOperand.IteratorSpec iterator(Instruction instruction) {
        return (AdvancedOperand.IteratorSpec) instruction.advanced();
    }

    private static AdvancedOperand.ForkSpec fork(Instruction instruction) {
        return (AdvancedOperand.ForkSpec) instruction.advanced();
    }

    private static String named(Instruction instruction) {
        return ((AdvancedOperand.Named) instruction.advanced()).name();
    }

    private static ManaCostModel.Input aggregate(List<Instruction> instructions) {
        Objects.requireNonNull(instructions, "instructions");
        ManaCostModel.Input total = ManaCostModel.Input.ZERO;
        for (int index = 0; index < instructions.size(); index++) {
            Instruction instruction = instructions.get(index);
            total = total.plus(Objects.requireNonNull(instruction, "instruction").cost());
            if (instruction.opcode() == Opcode.LOOP && instruction.secondArgument() > 1) {
                ManaCostModel.Input body = ManaCostModel.Input.ZERO;
                for (int bodyIndex = instruction.argument(); bodyIndex < index; bodyIndex++) {
                    body = body.plus(instructions.get(bodyIndex).cost());
                }
                total = total.plus(body.times(instruction.secondArgument() - 1));
            }
            if (instruction.opcode() == Opcode.ITERATOR_BEGIN) {
                AdvancedOperand.IteratorSpec spec = iterator(instruction);
                int next = findIteratorNext(instructions, spec.name(), index + 1);
                ManaCostModel.Input body = ManaCostModel.Input.ZERO;
                for (int bodyIndex = index + 1; bodyIndex <= next; bodyIndex++) {
                    body = body.plus(instructions.get(bodyIndex).cost());
                }
                total = total.plus(body.times(spec.maximumSteps() - 1));
            }
        }
        return total;
    }

    private static int findIteratorNext(List<Instruction> instructions, String name, int start) {
        for (int index = start; index < instructions.size(); index++) {
            Instruction instruction = instructions.get(index);
            if (instruction.opcode() == Opcode.ITERATOR_NEXT
                    && iterator(instruction).name().equals(name)) return index;
        }
        throw new IllegalArgumentException("iterator has no NEXT: " + name);
    }

    private record Range(int start, int end, String name) { }
    private record ForkAt(int index, AdvancedOperand.ForkSpec spec) { }
}
