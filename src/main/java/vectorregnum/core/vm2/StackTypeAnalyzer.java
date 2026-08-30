package vectorregnum.core.vm2;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic forward analysis for the VM's stack and shared-memory contracts.
 *
 * <p>The public result intentionally remains {@link StackAnalysis} for source
 * compatibility. The additional abstract state used while walking the graph
 * proves variable, iterator, branch, watcher, message, and output bounds before
 * a program can be executed.</p>
 */
public final class StackTypeAnalyzer {
    private static final String ROOT = "";
    private static final int MAX_UNKNOWN_OUTPUT_CHARS = 256;

    private StackTypeAnalyzer() {
    }

    public static StackAnalysis analyze(Program program) {
        return analyze(program, VmLimits.DEFAULT.maxStackDepth());
    }

    public static StackAnalysis analyze(Program program, int maximumStackDepth) {
        Objects.requireNonNull(program, "program");
        if (maximumStackDepth < 1) {
            throw new IllegalArgumentException("maximumStackDepth must be positive");
        }
        return new Analyzer(program, maximumStackDepth).run();
    }

    private static final class Analyzer {
        private final List<Instruction> code;
        private final int maximumStackDepth;
        private final VmLimits limits = VmLimits.DEFAULT;
        private final Map<Integer, List<StackType>> entryStacks = new LinkedHashMap<>();
        private final Map<Node, AbstractState> states = new LinkedHashMap<>();
        private final ArrayDeque<Node> pending = new ArrayDeque<>();
        private final List<StackDiagnostic> diagnostics = new ArrayList<>();
        private final Set<String> diagnosticKeys = new LinkedHashSet<>();
        private final Map<String, ForkInfo> forks;
        private final Map<String, AbstractState> branchBaselines = new LinkedHashMap<>();
        private final Map<String, AbstractState> branchResults = new LinkedHashMap<>();
        private final List<AbstractState> waitingJoinStates = new ArrayList<>();
        private final Set<String> reachedForks = new LinkedHashSet<>();
        private final Set<String> cancelledBranches = new LinkedHashSet<>();
        private int joinPointer = -1;
        private boolean joinReleased;
        private int maximumDepth;

        private Analyzer(Program program, int maximumStackDepth) {
            code = program.instructions();
            this.maximumStackDepth = maximumStackDepth;
            forks = collectForks(code);
        }

        private StackAnalysis run() {
            if (code.isEmpty()) {
                return new StackAnalysis(List.of(), Map.of(), 0);
            }
            enqueue(new Node(0, ROOT), new AbstractState());
            while (!pending.isEmpty()) {
                Node node = pending.removeFirst();
                AbstractState state = states.get(node);
                if (state != null) {
                    process(node, state.copy());
                }
            }
            if (!waitingJoinStates.isEmpty() && !joinReleased) {
                int pointer = joinPointer >= 0 ? joinPointer : 0;
                addDiagnostic(new StackDiagnostic(StackDiagnostic.Code.UNJOINED_BRANCH,
                        "JOIN cannot prove that every reached branch terminates",
                        code.get(pointer).source(), pointer));
            }
            return new StackAnalysis(diagnostics, entryStacks, maximumDepth);
        }

        private void process(Node node, AbstractState state) {
            if (cancelledBranches.contains(node.branchName())) return;
            int pointer = node.pointer();
            Instruction instruction = code.get(pointer);
            registerEntry(pointer, state);
            maximumDepth = Math.max(maximumDepth, state.stack.size());
            if (state.stack.size() > maximumStackDepth) {
                addDiagnostic(new StackDiagnostic(StackDiagnostic.Code.STACK_OVERFLOW,
                        "stack depth " + state.stack.size() + " exceeds limit " + maximumStackDepth,
                        instruction.source(), pointer));
                return;
            }
            try {
                switch (instruction.opcode()) {
                    case PUSH -> {
                        state.stack.add(slot(StackType.of(instruction.literal()), instruction.literal()));
                        advance(node, state);
                    }
                    case POP -> {
                        pop(state, instruction.opcode());
                        advance(node, state);
                    }
                    case DUP -> {
                        state.stack.add(peek(state, instruction.opcode()));
                        advance(node, state);
                    }
                    case ADD -> {
                        binaryAlternatives(state, instruction.opcode(), List.of(
                                signature(StackType.NUMBER, StackType.NUMBER, StackType.NUMBER),
                                signature(StackType.VECTOR, StackType.VECTOR, StackType.VECTOR),
                                signature(StackType.POINT, StackType.VECTOR, StackType.POINT)));
                        advance(node, state);
                    }
                    case SUBTRACT -> {
                        binaryAlternatives(state, instruction.opcode(), List.of(
                                signature(StackType.NUMBER, StackType.NUMBER, StackType.NUMBER),
                                signature(StackType.VECTOR, StackType.VECTOR, StackType.VECTOR),
                                signature(StackType.POINT, StackType.POINT, StackType.VECTOR),
                                signature(StackType.POINT, StackType.VECTOR, StackType.POINT)));
                        advance(node, state);
                    }
                    case MULTIPLY -> {
                        binaryAlternatives(state, instruction.opcode(), List.of(
                                signature(StackType.NUMBER, StackType.NUMBER, StackType.NUMBER),
                                signature(StackType.VECTOR, StackType.NUMBER, StackType.VECTOR),
                                signature(StackType.NUMBER, StackType.VECTOR, StackType.VECTOR)));
                        advance(node, state);
                    }
                    case DIVIDE -> {
                        binaryAlternatives(state, instruction.opcode(), List.of(
                                signature(StackType.NUMBER, StackType.NUMBER, StackType.NUMBER),
                                signature(StackType.VECTOR, StackType.NUMBER, StackType.VECTOR)));
                        advance(node, state);
                    }
                    case EQUALS -> {
                        requireDepth(state, 2, instruction.opcode());
                        pop(state, instruction.opcode());
                        pop(state, instruction.opcode());
                        state.stack.add(new StackSlot(StackType.BOOLEAN, 0));
                        advance(node, state);
                    }
                    case LESS_THAN, GREATER_THAN -> {
                        binary(state, instruction.opcode(), StackType.NUMBER,
                                StackType.NUMBER, StackType.BOOLEAN);
                        advance(node, state);
                    }
                    case NOT -> {
                        unary(state, instruction.opcode(), StackType.BOOLEAN, StackType.BOOLEAN);
                        advance(node, state);
                    }
                    case AND, OR -> {
                        binary(state, instruction.opcode(), StackType.BOOLEAN,
                                StackType.BOOLEAN, StackType.BOOLEAN);
                        advance(node, state);
                    }
                    case JUMP -> jump(node, state, instruction.argument());
                    case JUMP_IF_FALSE -> {
                        requirePop(state, instruction.opcode(), StackType.BOOLEAN);
                        branch(node, state, instruction.argument());
                        advance(node, state);
                    }
                    case LOOP -> {
                        if (instruction.secondArgument() > limits.maxLoopIterations()) {
                            throw fault(StackDiagnostic.Code.LOOP_LIMIT,
                                    "loop declares " + instruction.secondArgument()
                                            + " iterations; limit is " + limits.maxLoopIterations());
                        }
                        int pass = state.loopPasses.merge(pointer, 1, Integer::sum);
                        if (pass < instruction.secondArgument()) {
                            branch(node, state, instruction.argument());
                        } else {
                            state.loopPasses.remove(pointer);
                            advance(node, state);
                        }
                    }
                    case DELAY, SET_DURATION, SEMANTIC -> advance(node, state);
                    case SELECT_RADIUS -> {
                        requirePop(state, instruction.opcode(), StackType.NUMBER);
                        requirePop(state, instruction.opcode(), StackType.POINT);
                        state.stack.add(new StackSlot(StackType.ENTITY_LIST, 0));
                        advance(node, state);
                    }
                    case RAYCAST_ENTITIES -> {
                        requirePop(state, instruction.opcode(), StackType.NUMBER);
                        requirePop(state, instruction.opcode(), StackType.VECTOR);
                        requirePop(state, instruction.opcode(), StackType.POINT);
                        state.stack.add(new StackSlot(StackType.ENTITY_LIST, 0));
                        advance(node, state);
                    }
                    case IMPULSE, ACCELERATION -> {
                        requirePop(state, instruction.opcode(), StackType.VECTOR);
                        requirePop(state, instruction.opcode(), StackType.ENTITY);
                        advance(node, state);
                    }
                    case DAMPING -> {
                        requirePop(state, instruction.opcode(), StackType.NUMBER);
                        requirePop(state, instruction.opcode(), StackType.ENTITY);
                        advance(node, state);
                    }
                    case FOLLOW_PATH -> {
                        requirePop(state, instruction.opcode(), StackType.NUMBER);
                        requirePop(state, instruction.opcode(), StackType.POINT_LIST);
                        requirePop(state, instruction.opcode(), StackType.ENTITY);
                        advance(node, state);
                    }
                    case MOVE_TOWARD -> {
                        requirePop(state, instruction.opcode(), StackType.NUMBER);
                        requirePop(state, instruction.opcode(), StackType.POINT);
                        requirePop(state, instruction.opcode(), StackType.ENTITY);
                        advance(node, state);
                    }
                    case KEEP_DISTANCE -> {
                        requirePop(state, instruction.opcode(), StackType.NUMBER);
                        requirePop(state, instruction.opcode(), StackType.ENTITY);
                        requirePop(state, instruction.opcode(), StackType.ENTITY);
                        advance(node, state);
                    }
                    case HALT -> {
                        if (!node.branchName().equals(ROOT)) {
                            throw fault(StackDiagnostic.Code.UNJOINED_BRANCH,
                                    "branch must terminate with BRANCH_END");
                        }
                    }
                    case STORE_VARIABLE -> {
                        storeVariable(state, instruction);
                        advance(node, state);
                    }
                    case LOAD_VARIABLE -> {
                        loadVariable(state, instruction);
                        advance(node, state);
                    }
                    case ITERATOR_BEGIN -> iteratorBegin(node, state, instruction);
                    case ITERATOR_NEXT -> iteratorNext(node, state, instruction);
                    case COLLISION -> collision(node, state, instruction);
                    case WATCH_VARIABLE -> watchVariable(node, state, instruction);
                    case SIGNAL -> signal(node, state, instruction);
                    case OUTPUT -> output(node, state, instruction);
                    case FORK -> fork(node, state, instruction);
                    case JOIN -> join(node, state);
                    case CANCEL_BRANCH -> cancelBranch(node, state, instruction);
                    case BRANCH_END -> branchEnd(node, state, instruction);
                }
            } catch (AnalysisFault fault) {
                addDiagnostic(new StackDiagnostic(fault.code, fault.getMessage(),
                        instruction.source(), pointer));
            } catch (ArithmeticException overflow) {
                addDiagnostic(new StackDiagnostic(StackDiagnostic.Code.OUTPUT_TOO_LARGE,
                        "bounded analysis counter overflowed", instruction.source(), pointer));
            }
        }

        private void registerEntry(int pointer, AbstractState state) {
            entryStacks.putIfAbsent(pointer, state.stack.stream().map(StackSlot::type).toList());
        }

        private void storeVariable(AbstractState state, Instruction instruction) {
            String name = named(instruction);
            StackSlot value = pop(state, instruction.opcode());
            VariableState existing = state.variables.get(name);
            if (existing != null && existing.type() != null && existing.type() != value.type()) {
                throw fault(StackDiagnostic.Code.VARIABLE_TYPE_MISMATCH,
                        "variable '" + name + "' is " + existing.type().displayName()
                                + " but the store provides " + value.type().displayName());
            }
            if (existing == null && state.variables.size() >= limits.maxVariables()) {
                throw fault(StackDiagnostic.Code.VARIABLE_LIMIT,
                        "variable limit " + limits.maxVariables() + " exceeded");
            }
            StackType type = existing != null && existing.type() != null
                    ? existing.type() : value.type();
            int textChars = Math.max(existing == null ? 0 : existing.maximumTextChars(),
                    value.maximumTextChars());
            state.variables.put(name, new VariableState(type, true, textChars));
        }

        private void loadVariable(AbstractState state, Instruction instruction) {
            String name = named(instruction);
            VariableState value = state.variables.get(name);
            if (value == null || !value.definitelyAssigned() || value.type() == null) {
                throw fault(StackDiagnostic.Code.VARIABLE_NOT_FOUND,
                        "variable '" + name + "' is not definitely assigned");
            }
            state.stack.add(new StackSlot(value.type(), value.maximumTextChars()));
        }

        private void iteratorBegin(Node node, AbstractState state, Instruction instruction) {
            AdvancedOperand.IteratorSpec spec = iterator(instruction);
            if (spec.maximumSteps() > limits.maxIteratorSteps()) {
                throw fault(StackDiagnostic.Code.ITERATOR_STEP_LIMIT,
                        "iterator declares " + spec.maximumSteps() + " steps; limit is "
                                + limits.maxIteratorSteps());
            }
            StackSlot list = pop(state, instruction.opcode());
            StackType element = switch (list.type()) {
                case POINT_LIST -> StackType.POINT;
                case ENTITY_LIST -> StackType.ENTITY;
                case NUMBER_LIST -> StackType.NUMBER;
                case BOOLEAN_LIST -> StackType.BOOLEAN;
                case VECTOR_LIST -> StackType.VECTOR;
                case TEXT_LIST -> StackType.TEXT;
                case LIST -> StackType.LIST;
                default -> throw fault(StackDiagnostic.Code.TYPE_MISMATCH,
                        "ITERATOR_BEGIN expects a list but found " + list.type().displayName());
            };
            if (list.knownListSize() >= 0 && list.knownListSize() > spec.maximumSteps()) {
                throw fault(StackDiagnostic.Code.ITERATOR_STEP_LIMIT,
                        "iterator '" + spec.name() + "' list has " + list.knownListSize()
                                + " elements but declares " + spec.maximumSteps() + " steps");
            }
            if (state.iterators.containsKey(spec.name())) {
                throw fault(StackDiagnostic.Code.ITERATOR_NOT_FOUND,
                        "iterator '" + spec.name() + "' is already active");
            }
            if (state.iterators.size() >= limits.maxIterators()) {
                throw fault(StackDiagnostic.Code.ITERATOR_LIMIT,
                        "iterator limit " + limits.maxIterators() + " exceeded");
            }
            state.iterators.put(spec.name(), new IteratorState(element, spec.maximumSteps(), 0,
                    list.knownListSize()));
            state.iteratorSteps = Math.addExact(state.iteratorSteps, 1);
            if (state.iteratorSteps > limits.maxIteratorSteps()) {
                throw fault(StackDiagnostic.Code.ITERATOR_STEP_LIMIT,
                        "global iterator step limit " + limits.maxIteratorSteps() + " exceeded");
            }

            // BEGIN has two explicit paths: an empty snapshot exits immediately;
            // a non-empty snapshot exposes its first item to the body.
            AbstractState empty = state.copy();
            empty.iterators.remove(spec.name());
            enqueue(new Node(spec.target(), node.branchName()), empty);

            AbstractState nonEmpty = state.copy();
            nonEmpty.stack.add(new StackSlot(element, element == StackType.TEXT
                    ? MAX_UNKNOWN_OUTPUT_CHARS : 0));
            advance(node, nonEmpty);
        }

        private void iteratorNext(Node node, AbstractState state, Instruction instruction) {
            AdvancedOperand.IteratorSpec spec = iterator(instruction);
            IteratorState active = state.iterators.get(spec.name());
            if (active == null) {
                throw fault(StackDiagnostic.Code.ITERATOR_NOT_FOUND,
                        "iterator '" + spec.name() + "' is not active");
            }
            AbstractState exhausted = state.copy();
            exhausted.iterators.remove(spec.name());
            enqueue(new Node(node.pointer() + 1, node.branchName()), exhausted);

            int nextStep = Math.addExact(active.steps(), 1);
            boolean mayHaveNext = active.knownListSize() < 0
                    || nextStep < active.knownListSize();
            if (mayHaveNext) {
                if (nextStep > active.maximumSteps() || nextStep > limits.maxIteratorSteps()
                        || state.iteratorSteps >= limits.maxIteratorSteps()) {
                    throw fault(StackDiagnostic.Code.ITERATOR_STEP_LIMIT,
                            "iterator '" + spec.name() + "' exceeded its step limit");
                }
                AbstractState continuing = state.copy();
                continuing.iteratorSteps = Math.addExact(continuing.iteratorSteps, 1);
                continuing.iterators.put(spec.name(), new IteratorState(active.elementType(),
                        active.maximumSteps(), nextStep, active.knownListSize()));
                continuing.stack.add(new StackSlot(active.elementType(),
                        active.elementType() == StackType.TEXT ? MAX_UNKNOWN_OUTPUT_CHARS : 0));
                enqueue(new Node(spec.target(), node.branchName()), continuing);
            }
        }

        private void collision(Node node, AbstractState state, Instruction instruction) {
            AdvancedOperand.RangeSpec spec = range(instruction);
            validateRange(spec, instruction.opcode());
            requireDepth(state, 2, instruction.opcode());
            StackType right = pop(state, instruction.opcode()).type();
            StackType left = pop(state, instruction.opcode()).type();
            if (!collisionType(left) || !collisionType(right)) {
                throw fault(StackDiagnostic.Code.TYPE_MISMATCH,
                        "COLLISION expects point/entity pairs but found ("
                                + left.displayName() + ", " + right.displayName() + ")");
            }
            state.collisionResults = Math.addExact(state.collisionResults, spec.samples());
            if (state.collisionResults > limits.maxSelectionResults()) {
                throw fault(StackDiagnostic.Code.COLLISION_LIMIT,
                        "collision result limit " + limits.maxSelectionResults() + " exceeded");
            }
            state.stack.add(new StackSlot(StackType.BOOLEAN, 0));
            advance(node, state);
        }

        private void watchVariable(Node node, AbstractState state, Instruction instruction) {
            AdvancedOperand.WatchSpec spec = watch(instruction);
            if (spec.declaredRange() > limits.maxPerceptionRange()) {
                throw fault(StackDiagnostic.Code.WATCHER_LIMIT,
                        "watch range exceeds " + limits.maxPerceptionRange());
            }
            VariableState variable = state.variables.get(spec.variable());
            if (variable == null || !variable.definitelyAssigned()) {
                throw fault(StackDiagnostic.Code.VARIABLE_NOT_FOUND,
                        "cannot watch variable '" + spec.variable() + "' before it is assigned");
            }
            requirePop(state, instruction.opcode(), StackType.POINT);
            state.watchers.add(spec.variable());
            if (state.watchers.size() > limits.maxWatchers()) {
                throw fault(StackDiagnostic.Code.WATCHER_LIMIT,
                        "watcher limit " + limits.maxWatchers() + " exceeded");
            }
            advance(node, state);
        }

        private void signal(Node node, AbstractState state, Instruction instruction) {
            AdvancedOperand.RangeSpec spec = range(instruction);
            validateRange(spec, instruction.opcode());
            requireDepth(state, 2, instruction.opcode());
            requirePop(state, instruction.opcode(), StackType.POINT);
            pop(state, instruction.opcode());
            state.signalCount = Math.addExact(state.signalCount, 1);
            if (state.signalCount > limits.maxSignals()) {
                throw fault(StackDiagnostic.Code.SIGNAL_LIMIT,
                        "signal limit " + limits.maxSignals() + " exceeded");
            }
            advance(node, state);
        }

        private void output(Node node, AbstractState state, Instruction instruction) {
            AdvancedOperand.RangeSpec spec = range(instruction);
            validateRange(spec, instruction.opcode());
            requirePop(state, instruction.opcode(), StackType.POINT);
            StackSlot payload = pop(state, instruction.opcode());
            state.outputCount = Math.addExact(state.outputCount, 1);
            state.outputChars = Math.addExact(state.outputChars,
                    payload.maximumTextChars() > 0
                            ? payload.maximumTextChars() : MAX_UNKNOWN_OUTPUT_CHARS);
            if (state.outputCount > limits.maxOutputs()) {
                throw fault(StackDiagnostic.Code.OUTPUT_LIMIT,
                        "output limit " + limits.maxOutputs() + " exceeded");
            }
            if (state.outputChars > limits.maxOutputChars()) {
                throw fault(StackDiagnostic.Code.OUTPUT_TOO_LARGE,
                        "output character limit " + limits.maxOutputChars() + " exceeded");
            }
            advance(node, state);
        }

        private static boolean collisionType(StackType type) {
            return type == StackType.POINT || type == StackType.ENTITY;
        }

        private void fork(Node node, AbstractState state, Instruction instruction) {
            if (!node.branchName().equals(ROOT)) {
                throw fault(StackDiagnostic.Code.BRANCH_LIMIT,
                        "nested FORK is not allowed inside a branch");
            }
            AdvancedOperand.ForkSpec spec = StackTypeAnalyzer.fork(instruction);
            if (forks.size() + 1 > limits.maxActiveBranches()
                    || forks.size() + 1 > limits.maxTotalBranches()) {
                throw fault(StackDiagnostic.Code.BRANCH_LIMIT,
                        "branch limit exceeded (root plus " + forks.size() + " children)");
            }
            if (branchBaselines.putIfAbsent(spec.name(), state.copy()) != null) {
                throw fault(StackDiagnostic.Code.DUPLICATE_BRANCH,
                        "branch '" + spec.name() + "' is already declared");
            }
            reachedForks.add(spec.name());
            enqueue(new Node(spec.start(), spec.name()), state.copy());
            advance(node, state);
        }

        private void join(Node node, AbstractState state) {
            if (!node.branchName().equals(ROOT)) {
                throw fault(StackDiagnostic.Code.UNJOINED_BRANCH,
                        "JOIN is only valid on the root branch");
            }
            joinPointer = node.pointer();
            if (joinReleased) {
                enqueue(new Node(node.pointer() + 1, ROOT), joinState(state));
                return;
            }
            waitingJoinStates.add(state.copy());
            tryReleaseJoin();
        }

        private void tryReleaseJoin() {
            if (joinReleased || !branchResults.keySet().containsAll(reachedForks)) {
                return;
            }
            AbstractState root = null;
            for (AbstractState waiting : waitingJoinStates) {
                root = root == null ? waiting.copy() : mergeJoinRoots(root, waiting);
            }
            if (root == null) return;
            AbstractState joined = joinState(root);
            joinReleased = true;
            waitingJoinStates.clear();
            if (joinPointer + 1 < code.size()) {
                enqueue(new Node(joinPointer + 1, ROOT), joined);
            }
        }

        private AbstractState joinState(AbstractState root) {
            AbstractState joined = root.copy();
            if (reachedForks.isEmpty()) return joined;
            List<String> names = reachedForks.stream().sorted().toList();
            Map<String, VariableState> variables = new LinkedHashMap<>();
            Set<String> variableNames = new LinkedHashSet<>(root.variables.keySet());
            for (String name : names) {
                variableNames.addAll(branchResults.get(name).variables.keySet());
            }
            for (String name : variableNames) {
                VariableState base = root.variables.get(name);
                List<VariableState> values = new ArrayList<>();
                for (String branch : names) {
                    VariableState value = branchResults.get(branch).variables.get(name);
                    values.add(value == null ? base : value);
                }
                VariableState merged = mergeBranchVariables(name, values);
                if (merged != null) variables.put(name, merged);
            }
            joined.variables.clear();
            joined.variables.putAll(variables);
            if (joined.variables.size() > limits.maxVariables()) {
                addJoinDiagnostic(StackDiagnostic.Code.VARIABLE_LIMIT,
                        "variable limit " + limits.maxVariables() + " exceeded");
            }

            for (String branch : names) {
                AbstractState result = branchResults.get(branch);
                if (!sameStack(joined.stack, result.stack)) {
                    addDiagnostic(new StackDiagnostic(StackDiagnostic.Code.BRANCH_STACK_MISMATCH,
                            "branch '" + branch + "' changes the shared stack before JOIN",
                            code.get(joinPointer).source(), joinPointer));
                }
                joined.watchers.addAll(result.watchers);
                AbstractState baseline = branchBaselines.get(branch);
                if (!sameIterators(baseline.iterators, result.iterators)) {
                    addDiagnostic(new StackDiagnostic(StackDiagnostic.Code.ITERATOR_NOT_FOUND,
                            "branch '" + branch + "' changes iterator state before JOIN",
                            code.get(joinPointer).source(), joinPointer));
                }
                joined.iteratorSteps = addBranchDelta(joined.iteratorSteps,
                        result.iteratorSteps, baseline.iteratorSteps);
                joined.signalCount = addBranchDelta(joined.signalCount,
                        result.signalCount, baseline.signalCount);
                joined.outputCount = addBranchDelta(joined.outputCount,
                        result.outputCount, baseline.outputCount);
                joined.outputChars = addBranchDelta(joined.outputChars,
                        result.outputChars, baseline.outputChars);
                joined.collisionResults = addBranchDelta(joined.collisionResults,
                        result.collisionResults, baseline.collisionResults);
            }
            if (joined.watchers.size() > limits.maxWatchers()) {
                addJoinDiagnostic(StackDiagnostic.Code.WATCHER_LIMIT,
                        "watcher limit " + limits.maxWatchers() + " exceeded");
            }
            if (joined.signalCount > limits.maxSignals()) {
                addJoinDiagnostic(StackDiagnostic.Code.SIGNAL_LIMIT,
                        "signal limit " + limits.maxSignals() + " exceeded");
            }
            if (joined.outputCount > limits.maxOutputs()) {
                addJoinDiagnostic(StackDiagnostic.Code.OUTPUT_LIMIT,
                        "output limit " + limits.maxOutputs() + " exceeded");
            }
            if (joined.outputChars > limits.maxOutputChars()) {
                addJoinDiagnostic(StackDiagnostic.Code.OUTPUT_TOO_LARGE,
                        "output character limit " + limits.maxOutputChars() + " exceeded");
            }
            if (joined.collisionResults > limits.maxSelectionResults()) {
                addJoinDiagnostic(StackDiagnostic.Code.COLLISION_LIMIT,
                        "collision result limit " + limits.maxSelectionResults() + " exceeded");
            }
            return joined;
        }

        private void cancelBranch(Node node, AbstractState state, Instruction instruction) {
            String name = named(instruction);
            if (!forks.containsKey(name)) {
                throw fault(StackDiagnostic.Code.UNKNOWN_BRANCH, "unknown branch '" + name + "'");
            }
            if (name.equals(node.branchName())) {
                branchEnd(node, state, instruction);
                cancelledBranches.add(name);
            } else if (!branchResults.containsKey(name)) {
                cancelledBranches.add(name);
                AbstractState baseline = branchBaselines.get(name);
                if (baseline != null) branchResults.put(name, baseline.copy());
                tryReleaseJoin();
            } else {
                advance(node, state);
            }
        }

        private void branchEnd(Node node, AbstractState state, Instruction instruction) {
            if (node.branchName().equals(ROOT)) {
                throw fault(StackDiagnostic.Code.UNJOINED_BRANCH,
                        "BRANCH_END is only valid inside a fork body");
            }
            AbstractState baseline = branchBaselines.get(node.branchName());
            if (baseline == null) {
                throw fault(StackDiagnostic.Code.UNKNOWN_BRANCH,
                        "unknown branch '" + node.branchName() + "'");
            }
            if (!sameStack(baseline.stack, state.stack)) {
                addDiagnostic(new StackDiagnostic(StackDiagnostic.Code.BRANCH_STACK_MISMATCH,
                        "branch '" + node.branchName()
                                + "' must leave the shared stack unchanged at BRANCH_END",
                        instruction.source(), node.pointer()));
            }
            AbstractState previous = branchResults.get(node.branchName());
            branchResults.put(node.branchName(), previous == null
                    ? state.copy() : mergeJoinRoots(previous, state));
            tryReleaseJoin();
        }

        private void jump(Node node, AbstractState state, int target) {
            enqueue(new Node(target, node.branchName()), state);
        }

        private void branch(Node node, AbstractState state, int target) {
            enqueue(new Node(target, node.branchName()), state.copy());
        }

        private void advance(Node node, AbstractState state) {
            int next = node.pointer() + 1;
            if (next < code.size()) enqueue(new Node(next, node.branchName()), state);
        }

        private void enqueue(Node node, AbstractState incoming) {
            if (node.pointer() < 0 || node.pointer() >= code.size()) return;
            AbstractState existing = states.get(node);
            if (existing == null) {
                states.put(node, incoming.copy());
                pending.addLast(node);
                return;
            }
            AbstractState merged = mergeNormal(node, existing, incoming);
            if (merged != null && !merged.sameAs(existing)) {
                states.put(node, merged);
                pending.addLast(node);
            }
        }

        private AbstractState mergeNormal(Node node, AbstractState left, AbstractState right) {
            if (!sameStack(left.stack, right.stack)) {
                addMergeDiagnostic(node, StackDiagnostic.Code.CONTROL_FLOW_MERGE,
                        "control-flow paths enter with incompatible stacks: "
                                + display(left.stack) + " and " + display(right.stack));
                return null;
            }
            AbstractState merged = left.copy();
            merged.stack.clear();
            for (int index = 0; index < left.stack.size(); index++) {
                StackSlot first = left.stack.get(index);
                StackSlot second = right.stack.get(index);
                merged.stack.add(new StackSlot(first.type(), Math.max(first.maximumTextChars(),
                        second.maximumTextChars()),
                        first.knownListSize() == second.knownListSize()
                                ? first.knownListSize() : -1));
            }
            merged.variables.clear();
            Set<String> names = new LinkedHashSet<>(left.variables.keySet());
            names.addAll(right.variables.keySet());
            for (String name : names) {
                VariableState value = mergeVariable(name, left.variables.get(name),
                        right.variables.get(name), node);
                if (value != null) merged.variables.put(name, value);
            }
            if (merged.variables.size() > limits.maxVariables()) {
                addMergeDiagnostic(node, StackDiagnostic.Code.VARIABLE_LIMIT,
                        "variable limit " + limits.maxVariables() + " exceeded");
                return null;
            }
            merged.iterators.clear();
            Set<String> iteratorNames = new LinkedHashSet<>(left.iterators.keySet());
            iteratorNames.addAll(right.iterators.keySet());
            for (String name : iteratorNames) {
                IteratorState first = left.iterators.get(name);
                IteratorState second = right.iterators.get(name);
                if (first == null || second == null
                        || first.elementType() != second.elementType()
                        || first.maximumSteps() != second.maximumSteps()
                        || first.knownListSize() != second.knownListSize()) {
                    addMergeDiagnostic(node, StackDiagnostic.Code.ITERATOR_NOT_FOUND,
                            "iterator paths do not agree for '" + name + "'");
                    return null;
                }
                merged.iterators.put(name, new IteratorState(first.elementType(),
                        first.maximumSteps(), Math.max(first.steps(), second.steps()),
                        first.knownListSize()));
            }
            merged.loopPasses.clear();
            Set<Integer> loopPointers = new LinkedHashSet<>(left.loopPasses.keySet());
            loopPointers.addAll(right.loopPasses.keySet());
            for (int pointer : loopPointers) {
                merged.loopPasses.put(pointer, Math.max(left.loopPasses.getOrDefault(pointer, 0),
                        right.loopPasses.getOrDefault(pointer, 0)));
            }
            merged.watchers.addAll(right.watchers);
            if (merged.watchers.size() > limits.maxWatchers()) {
                addMergeDiagnostic(node, StackDiagnostic.Code.WATCHER_LIMIT,
                        "watcher limit " + limits.maxWatchers() + " exceeded");
                return null;
            }
            merged.iteratorSteps = Math.max(left.iteratorSteps, right.iteratorSteps);
            merged.collisionResults = Math.max(left.collisionResults, right.collisionResults);
            merged.signalCount = Math.max(left.signalCount, right.signalCount);
            merged.outputCount = Math.max(left.outputCount, right.outputCount);
            merged.outputChars = Math.max(left.outputChars, right.outputChars);
            return merged;
        }

        private AbstractState mergeJoinRoots(AbstractState left, AbstractState right) {
            Node node = new Node(Math.max(0, joinPointer), ROOT);
            AbstractState merged = mergeNormal(node, left, right);
            return merged == null ? left : merged;
        }

        private VariableState mergeBranchVariables(String name, List<VariableState> values) {
            StackType type = null;
            boolean assigned = true;
            int textChars = 0;
            for (VariableState value : values) {
                if (value == null) {
                    assigned = false;
                    continue;
                }
                if (value.type() != null) {
                    if (type != null && type != value.type()) {
                        addJoinDiagnostic(StackDiagnostic.Code.VARIABLE_TYPE_MISMATCH,
                                "variable '" + name + "' has incompatible branch types");
                        return null;
                    }
                    type = value.type();
                }
                assigned &= value.definitelyAssigned();
                textChars = Math.max(textChars, value.maximumTextChars());
            }
            if (type == null && !assigned) return null;
            return new VariableState(type, assigned && type != null, textChars);
        }

        private VariableState mergeVariable(String name, VariableState first,
                VariableState second, Node node) {
            if (first == null) {
                return second == null ? null
                        : new VariableState(second.type(), false, second.maximumTextChars());
            }
            if (second == null) {
                return new VariableState(first.type(), false, first.maximumTextChars());
            }
            if (first.type() != null && second.type() != null && first.type() != second.type()) {
                addMergeDiagnostic(node, StackDiagnostic.Code.VARIABLE_TYPE_MISMATCH,
                        "variable '" + name + "' has incompatible types on control-flow paths");
                return null;
            }
            StackType type = first.type() != null ? first.type() : second.type();
            return new VariableState(type, first.definitelyAssigned() && second.definitelyAssigned(),
                    Math.max(first.maximumTextChars(), second.maximumTextChars()));
        }

        private void addJoinDiagnostic(StackDiagnostic.Code code, String message) {
            int pointer = joinPointer >= 0 ? joinPointer : 0;
            addDiagnostic(new StackDiagnostic(code, message, this.code.get(pointer).source(), pointer));
        }

        private void addMergeDiagnostic(Node node, StackDiagnostic.Code code, String message) {
            addDiagnostic(new StackDiagnostic(code, message,
                    codeAt(node.pointer()).source(), node.pointer()));
        }

        private Instruction codeAt(int pointer) {
            return code.get(Math.max(0, Math.min(pointer, code.size() - 1)));
        }

        private void validateRange(AdvancedOperand.RangeSpec spec, Opcode opcode) {
            if (spec.declaredRange() > limits.maxPerceptionRange()) {
                throw fault(rangeLimitCode(opcode),
                        opcode + " range exceeds " + limits.maxPerceptionRange());
            }
            if (spec.samples() > limits.maxSelectionResults()) {
                throw fault(StackDiagnostic.Code.COLLISION_LIMIT,
                        opcode + " samples exceed " + limits.maxSelectionResults());
            }
        }

        private StackDiagnostic.Code rangeLimitCode(Opcode opcode) {
            return switch (opcode) {
                case WATCH_VARIABLE -> StackDiagnostic.Code.WATCHER_LIMIT;
                case SIGNAL -> StackDiagnostic.Code.SIGNAL_LIMIT;
                case OUTPUT -> StackDiagnostic.Code.OUTPUT_LIMIT;
                default -> StackDiagnostic.Code.COLLISION_LIMIT;
            };
        }

        private void addDiagnostic(StackDiagnostic diagnostic) {
            StackTypeAnalyzer.addDiagnostic(diagnostics, diagnosticKeys, diagnostic);
        }

        private static Map<String, ForkInfo> collectForks(List<Instruction> code) {
            Map<String, ForkInfo> result = new LinkedHashMap<>();
            for (int index = 0; index < code.size(); index++) {
                Instruction instruction = code.get(index);
                if (instruction.opcode() == Opcode.FORK) {
                    AdvancedOperand.ForkSpec spec = StackTypeAnalyzer.fork(instruction);
                    result.putIfAbsent(spec.name(), new ForkInfo(index, spec));
                }
            }
            return result;
        }
    }

    private static void addDiagnostic(List<StackDiagnostic> diagnostics, Set<String> keys,
            StackDiagnostic diagnostic) {
        String key = diagnostic.instructionPointer() + ":" + diagnostic.code() + ":"
                + diagnostic.message();
        if (keys.add(key)) diagnostics.add(diagnostic);
    }

    private static StackSlot slot(StackType type, RuntimeValue value) {
        int maximumTextChars = value instanceof RuntimeValue.TextValue text
                ? text.value().length() : 0;
        int knownListSize = value instanceof RuntimeValue.ListValue list ? list.values().size() : -1;
        return new StackSlot(type, maximumTextChars, knownListSize);
    }

    private static void unary(AbstractState state, Opcode opcode,
            StackType operand, StackType result) {
        requirePop(state, opcode, operand);
        state.stack.add(new StackSlot(result, 0));
    }

    private static void binary(AbstractState state, Opcode opcode, StackType left,
            StackType right, StackType result) {
        requireDepth(state, 2, opcode);
        StackType actualRight = pop(state, opcode).type();
        StackType actualLeft = pop(state, opcode).type();
        if (actualLeft != left || actualRight != right) {
            throw mismatch(opcode, List.of(left, right), List.of(actualLeft, actualRight));
        }
        state.stack.add(new StackSlot(result, 0));
    }

    private static void binaryAlternatives(AbstractState state, Opcode opcode,
            List<Signature> alternatives) {
        requireDepth(state, 2, opcode);
        StackType actualRight = state.stack.get(state.stack.size() - 1).type();
        StackType actualLeft = state.stack.get(state.stack.size() - 2).type();
        for (Signature alternative : alternatives) {
            if (alternative.left == actualLeft && alternative.right == actualRight) {
                state.stack.removeLast();
                state.stack.removeLast();
                state.stack.add(new StackSlot(alternative.result, 0));
                return;
            }
        }
        String expected = alternatives.stream().map(Signature::display)
                .reduce((left, right) -> left + ", " + right).orElse("none");
        throw new AnalysisFault(StackDiagnostic.Code.TYPE_MISMATCH,
                opcode + " expects " + expected + " but found ("
                        + actualLeft.displayName() + ", " + actualRight.displayName() + ")");
    }

    private static StackSlot requirePop(AbstractState state, Opcode opcode, StackType expected) {
        StackSlot actual = pop(state, opcode);
        if (actual.type() != expected) throw mismatch(opcode, List.of(expected), List.of(actual.type()));
        return actual;
    }

    private static StackSlot peek(AbstractState state, Opcode opcode) {
        requireDepth(state, 1, opcode);
        return state.stack.getLast();
    }

    private static StackSlot pop(AbstractState state, Opcode opcode) {
        requireDepth(state, 1, opcode);
        return state.stack.removeLast();
    }

    private static void requireDepth(AbstractState state, int count, Opcode opcode) {
        if (state.stack.size() < count) {
            throw new AnalysisFault(StackDiagnostic.Code.STACK_UNDERFLOW,
                    opcode + " needs " + count + " stack value(s), found " + state.stack.size());
        }
    }

    private static AnalysisFault mismatch(Opcode opcode, List<StackType> expected,
            List<StackType> actual) {
        return new AnalysisFault(StackDiagnostic.Code.TYPE_MISMATCH,
                opcode + " expects " + displayTypes(expected) + " but found " + displayTypes(actual));
    }

    private static AnalysisFault fault(StackDiagnostic.Code code, String message) {
        return new AnalysisFault(code, message);
    }

    private static Signature signature(StackType left, StackType right, StackType result) {
        return new Signature(left, right, result);
    }

    private static String display(List<StackSlot> stack) {
        return stack.stream().map(StackSlot::type).map(StackType::displayName).toList().toString();
    }

    private static String displayTypes(List<StackType> stack) {
        return stack.stream().map(StackType::displayName).toList().toString();
    }

    private static boolean sameStack(List<StackSlot> left, List<StackSlot> right) {
        if (left.size() != right.size()) return false;
        for (int index = 0; index < left.size(); index++) {
            if (left.get(index).type() != right.get(index).type()) return false;
        }
        return true;
    }

    private static boolean sameIterators(Map<String, IteratorState> left,
            Map<String, IteratorState> right) {
        if (!left.keySet().equals(right.keySet())) return false;
        for (String name : left.keySet()) {
            IteratorState first = left.get(name);
            IteratorState second = right.get(name);
            if (!first.equals(second)) return false;
        }
        return true;
    }

    private static int addBranchDelta(int base, int result, int baseline) {
        return Math.addExact(base, Math.max(0, result - baseline));
    }

    private static String named(Instruction instruction) {
        return ((AdvancedOperand.Named) instruction.advanced()).name();
    }

    private static AdvancedOperand.IteratorSpec iterator(Instruction instruction) {
        return (AdvancedOperand.IteratorSpec) instruction.advanced();
    }

    private static AdvancedOperand.RangeSpec range(Instruction instruction) {
        return (AdvancedOperand.RangeSpec) instruction.advanced();
    }

    private static AdvancedOperand.WatchSpec watch(Instruction instruction) {
        return (AdvancedOperand.WatchSpec) instruction.advanced();
    }

    private static AdvancedOperand.ForkSpec fork(Instruction instruction) {
        return (AdvancedOperand.ForkSpec) instruction.advanced();
    }

    private record Node(int pointer, String branchName) {
        private Node {
            if (pointer < 0 || branchName == null) {
                throw new IllegalArgumentException("invalid analysis node");
            }
        }
    }

    private record StackSlot(StackType type, int maximumTextChars, int knownListSize) {
        private StackSlot(StackType type, int maximumTextChars) {
            this(type, maximumTextChars, -1);
        }

        private StackSlot {
            Objects.requireNonNull(type, "type");
            if (maximumTextChars < 0 || knownListSize < -1) {
                throw new IllegalArgumentException("invalid stack slot bound");
            }
        }
    }

    private record VariableState(StackType type, boolean definitelyAssigned,
            int maximumTextChars) {
        private VariableState {
            if (maximumTextChars < 0) throw new IllegalArgumentException("negative text bound");
        }
    }

    private record IteratorState(StackType elementType, int maximumSteps, int steps,
            int knownListSize) {
        private IteratorState {
            Objects.requireNonNull(elementType, "elementType");
            if (maximumSteps < 1 || steps < 0 || steps > maximumSteps || knownListSize < -1) {
                throw new IllegalArgumentException("invalid iterator state");
            }
        }
    }

    private static final class AbstractState {
        private final List<StackSlot> stack = new ArrayList<>();
        private final Map<String, VariableState> variables = new LinkedHashMap<>();
        private final Map<String, IteratorState> iterators = new LinkedHashMap<>();
        private final Map<Integer, Integer> loopPasses = new LinkedHashMap<>();
        private final Set<String> watchers = new LinkedHashSet<>();
        private int iteratorSteps;
        private int collisionResults;
        private int signalCount;
        private int outputCount;
        private int outputChars;

        private AbstractState copy() {
            AbstractState copy = new AbstractState();
            copy.stack.addAll(stack);
            copy.variables.putAll(variables);
            copy.iterators.putAll(iterators);
            copy.loopPasses.putAll(loopPasses);
            copy.watchers.addAll(watchers);
            copy.iteratorSteps = iteratorSteps;
            copy.collisionResults = collisionResults;
            copy.signalCount = signalCount;
            copy.outputCount = outputCount;
            copy.outputChars = outputChars;
            return copy;
        }

        private boolean sameAs(AbstractState other) {
            return stack.equals(other.stack) && variables.equals(other.variables)
                    && iterators.equals(other.iterators) && loopPasses.equals(other.loopPasses)
                    && watchers.equals(other.watchers) && iteratorSteps == other.iteratorSteps
                    && collisionResults == other.collisionResults
                    && signalCount == other.signalCount && outputCount == other.outputCount
                    && outputChars == other.outputChars;
        }
    }

    private record ForkInfo(int instructionPointer, AdvancedOperand.ForkSpec spec) {
    }

    private record Signature(StackType left, StackType right, StackType result) {
        private String display() {
            return "(" + left.displayName() + ", " + right.displayName() + ")";
        }
    }

    private static final class AnalysisFault extends RuntimeException {
        private final StackDiagnostic.Code code;

        private AnalysisFault(StackDiagnostic.Code code, String message) {
            super(message, null, false, false);
            this.code = code;
        }
    }
}
