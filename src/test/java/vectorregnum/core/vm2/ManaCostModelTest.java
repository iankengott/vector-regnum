package vectorregnum.core.vm2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import vectorregnum.core.vm2.WorldAccess.SelectionFilter;

class ManaCostModelTest {
    @Test
    void breakdownNamesAndCalculatesEveryRequiredFactorExactly() {
        ManaCostModel.Breakdown cost = ManaCostModel.estimate(
                new ManaCostModel.Input(100, 10, 40, 2, 3, 4, 5));
        assertEquals(1, cost.base());
        assertEquals(5, cost.physicalWork());
        assertEquals(2, cost.range());
        assertEquals(1, cost.duration());
        assertEquals(8, cost.rarity());
        assertEquals(.45, cost.memory(), 1e-12);
        assertEquals(1.6, cost.perception(), 1e-12);
        assertEquals(1, cost.controlFlow());
        assertEquals(20.05, cost.total(), 1e-12);
    }

    @Test
    void remoteCostReflectsInverseSquareSignalFalloff() {
        double atTen = ManaCostModel.estimate(new ManaCostModel.Input(0, 10, 0, 0, 0, 0, 0)).range();
        double atTwenty = ManaCostModel.estimate(new ManaCostModel.Input(0, 20, 0, 0, 0, 0, 0)).range();
        assertEquals(atTen * 4, atTwenty, 1e-12);
    }

    @Test
    void programAggregatesWorkRangeDurationRarityMemoryPerceptionAndControl() {
        SourceLocation s = SourceLocation.at(0, "test");
        Program program = new Program(List.of(
                Instruction.push(new RuntimeValue.NumberValue(1), s),
                Instruction.duration(20, s),
                Instruction.raycast(SelectionFilter.ANY, 12, 3, s),
                Instruction.impulse(25, 2, s),
                Instruction.loop(0, 4, s)));
        assertEquals(100, program.declaredCost().physicalWork());
        assertEquals(48, program.declaredCost().range());
        assertEquals(80, program.declaredCost().durationTicks());
        assertEquals(8, program.declaredCost().rarity());
        assertEquals(8, program.declaredCost().memorySlots());
        assertEquals(12, program.declaredCost().perceptionSamples());
        assertEquals(4, program.declaredCost().controlFlowSteps());
    }

    @Test
    void invalidAndOverflowingInputsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ManaCostModel.Input(Double.NaN, 0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> ManaCostModel.estimate(new ManaCostModel.Input(Double.MAX_VALUE, Double.MAX_VALUE,
                        Integer.MAX_VALUE, Double.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE)));
    }

    @Test
    void loopQuoteConservativelyRepeatsItsBodyCost() {
        SourceLocation s = SourceLocation.at(0, "loop-cost");
        Program once = new Program(List.of(
                Instruction.push(new RuntimeValue.NumberValue(1), s),
                Instruction.impulse(100, 0, s)));
        Program looped = new Program(List.of(
                Instruction.push(new RuntimeValue.NumberValue(1), s),
                Instruction.impulse(100, 0, s),
                Instruction.loop(0, 4, s)));
        assertEquals(once.declaredCost().physicalWork() * 4,
                looped.declaredCost().physicalWork(), 1e-12);
        assertEquals(once.declaredCost().memorySlots() * 4,
                looped.declaredCost().memorySlots());
    }
}
