package vectorregnum.core.vm2;

import java.util.ArrayList;
import java.util.List;

/** Reproducible bounded program used by the priority-24 command, GameTests, and visual gate. */
public final class Priority24ProgramFactory {
    private Priority24ProgramFactory() { }

    public static Program create(String casterEntityId, Vector3 origin) {
        List<Instruction> code = new ArrayList<>();
        code.add(Instruction.push(new RuntimeValue.NumberValue(0), at(0, "COUNTER_ZERO")));
        code.add(Instruction.storeVariable("counter", at(1, "STORE_COUNTER")));
        code.add(Instruction.push(new RuntimeValue.PointValue(origin), at(2, "WATCH_POINT")));
        code.add(Instruction.watchVariable("counter", 16, at(3, "WATCH_COUNTER")));
        code.add(Instruction.push(new RuntimeValue.NumberValue(1), at(4, "COUNTER_ONE")));
        code.add(Instruction.storeVariable("counter", at(5, "CHANGE_COUNTER")));
        code.add(Instruction.loadVariable("counter", at(6, "LOAD_COUNTER")));
        code.add(Instruction.push(new RuntimeValue.PointValue(origin), at(7, "OUTPUT_POINT")));
        code.add(Instruction.output(16, at(8, "OUTPUT_COUNTER")));
        code.add(Instruction.push(new RuntimeValue.EntityValue(casterEntityId), at(9, "COLLISION_SELF")));
        code.add(Instruction.push(new RuntimeValue.PointValue(origin), at(10, "COLLISION_POINT")));
        code.add(Instruction.collision(16, 1, at(11, "COLLISION")));
        code.add(Instruction.pop(at(12, "DROP_COLLISION_RESULT")));
        code.add(Instruction.push(new RuntimeValue.BooleanValue(true), at(13, "SIGNAL_VALUE")));
        code.add(Instruction.push(new RuntimeValue.PointValue(origin), at(14, "SIGNAL_POINT")));
        code.add(Instruction.signal(16, at(15, "SIGNAL")));
        code.add(Instruction.push(new RuntimeValue.ListValue(List.of(
                new RuntimeValue.PointValue(origin),
                new RuntimeValue.PointValue(origin.plus(new Vector3(1, 0, 0))))),
                at(16, "ITERATOR_VALUES")));
        code.add(Instruction.iteratorBegin("points", 20, 2, at(17, "ITERATOR_BEGIN")));
        code.add(Instruction.pop(at(18, "ITERATOR_ITEM")));
        code.add(Instruction.iteratorNext("points", 18, at(19, "ITERATOR_NEXT")));
        code.add(Instruction.fork("worker", 25, 28, at(20, "FORK")));
        code.add(Instruction.push(new RuntimeValue.TextValue("main"), at(21, "MAIN_PUSH")));
        code.add(Instruction.pop(at(22, "MAIN_POP")));
        code.add(Instruction.join(at(23, "JOIN")));
        code.add(Instruction.jump(28, at(24, "SKIP_BRANCH_BODY")));
        code.add(Instruction.push(new RuntimeValue.TextValue("branch"), at(25, "BRANCH_PUSH")));
        code.add(Instruction.pop(at(26, "BRANCH_POP")));
        code.add(Instruction.branchEnd(at(27, "BRANCH_END")));
        code.add(Instruction.halt(at(28, "HALT")));
        return new Program(code);
    }

    private static SourceLocation at(int index, String label) {
        return SourceLocation.at(index, label);
    }
}
