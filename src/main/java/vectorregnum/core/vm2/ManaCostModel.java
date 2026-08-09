package vectorregnum.core.vm2;

import java.util.Objects;

/**
 * Deterministic, side-effect-free cost model. Inputs are authored/compiler-declared
 * upper bounds, so a server can quote a cost before executing a spell.
 */
public final class ManaCostModel {
    private ManaCostModel() {}

    public record Input(double physicalWork, double range, int durationTicks,
            double rarity, int memorySlots, int perceptionSamples, int controlFlowSteps) {
        public static final Input ZERO = new Input(0, 0, 0, 0, 0, 0, 0);

        public Input {
            finiteNonNegative(physicalWork, "physicalWork");
            finiteNonNegative(range, "range");
            finiteNonNegative(rarity, "rarity");
            if (durationTicks < 0 || memorySlots < 0 || perceptionSamples < 0
                    || controlFlowSteps < 0) {
                throw new IllegalArgumentException("integer cost inputs cannot be negative");
            }
        }

        public Input plus(Input other) {
            Objects.requireNonNull(other, "other");
            return new Input(safeAdd(physicalWork, other.physicalWork), safeAdd(range, other.range),
                    Math.addExact(durationTicks, other.durationTicks), safeAdd(rarity, other.rarity),
                    Math.addExact(memorySlots, other.memorySlots),
                    Math.addExact(perceptionSamples, other.perceptionSamples),
                    Math.addExact(controlFlowSteps, other.controlFlowSteps));
        }

        public Input times(int multiplier) {
            if (multiplier < 0) {
                throw new IllegalArgumentException("cost multiplier cannot be negative");
            }
            return new Input(safeMultiply(physicalWork, multiplier), safeMultiply(range, multiplier),
                    Math.multiplyExact(durationTicks, multiplier), safeMultiply(rarity, multiplier),
                    Math.multiplyExact(memorySlots, multiplier),
                    Math.multiplyExact(perceptionSamples, multiplier),
                    Math.multiplyExact(controlFlowSteps, multiplier));
        }
    }

    public record Breakdown(double base, double physicalWork, double range, double duration,
            double rarity, double memory, double perception, double controlFlow, double total) {
        public Breakdown {
            finiteNonNegative(base, "base"); finiteNonNegative(physicalWork, "physicalWork");
            finiteNonNegative(range, "range"); finiteNonNegative(duration, "duration");
            finiteNonNegative(rarity, "rarity"); finiteNonNegative(memory, "memory");
            finiteNonNegative(perception, "perception"); finiteNonNegative(controlFlow, "controlFlow");
            finiteNonNegative(total, "total");
        }
    }

    public static Breakdown estimate(Input input) {
        Objects.requireNonNull(input, "input");
        double base = 1.0;
        double work = StrictMath.sqrt(input.physicalWork()) * 0.5;
        double range = input.range() * input.range() * 0.02;
        double duration = input.durationTicks() * 0.025;
        double rarity = input.rarity() * 4.0;
        double memory = input.memorySlots() * 0.15;
        double perception = input.perceptionSamples() * 0.4;
        double control = input.controlFlowSteps() * 0.2;
        double total = base + work + range + duration + rarity + memory + perception + control;
        if (!Double.isFinite(total)) throw new IllegalArgumentException("cost overflow");
        return new Breakdown(base, work, range, duration, rarity, memory, perception, control, total);
    }

    private static double safeAdd(double left, double right) {
        double result = left + right;
        if (!Double.isFinite(result)) throw new IllegalArgumentException("cost input overflow");
        return result;
    }

    private static double safeMultiply(double value, int multiplier) {
        double result = value * multiplier;
        if (!Double.isFinite(result)) throw new IllegalArgumentException("cost input overflow");
        return result;
    }

    private static void finiteNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
