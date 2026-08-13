package vectorregnum.core.automation;

import java.util.Objects;

/** Pure redstone gate used by relays before they enqueue server-owned work. */
public record AutomationRule(TriggerMode mode, int threshold, int cooldownTicks) {
    public static final int MAX_COOLDOWN_TICKS = 72_000;

    public enum TriggerMode {
        RISING_EDGE,
        FALLING_EDGE,
        CHANGE,
        WHILE_HIGH
    }

    public AutomationRule {
        Objects.requireNonNull(mode, "mode");
        if (threshold < 1 || threshold > 15) {
            throw new IllegalArgumentException("threshold must be between 1 and 15");
        }
        if (cooldownTicks < 1 || cooldownTicks > MAX_COOLDOWN_TICKS) {
            throw new IllegalArgumentException("cooldownTicks must be between 1 and "
                    + MAX_COOLDOWN_TICKS);
        }
    }

    public static AutomationRule risingEdge() {
        return new AutomationRule(TriggerMode.RISING_EDGE, 1, 20);
    }

    public boolean shouldTrigger(int previousPower, int currentPower,
            long worldTick, long lastAcceptedTick) {
        validatePower(previousPower);
        validatePower(currentPower);
        if (worldTick < 0) throw new IllegalArgumentException("worldTick must be non-negative");
        if (lastAcceptedTick >= 0 && worldTick - lastAcceptedTick < cooldownTicks) return false;
        boolean wasHigh = previousPower >= threshold;
        boolean isHigh = currentPower >= threshold;
        return switch (mode) {
            case RISING_EDGE -> !wasHigh && isHigh;
            case FALLING_EDGE -> wasHigh && !isHigh;
            case CHANGE -> previousPower != currentPower;
            case WHILE_HIGH -> isHigh;
        };
    }

    private static void validatePower(int power) {
        if (power < 0 || power > 15) {
            throw new IllegalArgumentException("redstone power must be between 0 and 15");
        }
    }
}
