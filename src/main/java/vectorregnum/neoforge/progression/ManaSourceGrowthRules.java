package vectorregnum.neoforge.progression;

/** Pure tick rules for natural source growth and recharge. */
public final class ManaSourceGrowthRules {
    public static final int TICKS_PER_DAY = 24_000;
    public static final int MAX_CATCH_UP_TICKS = 7 * TICKS_PER_DAY;
    public static final int MAX_GROWTH_STAGE = 3;
    public static final int CHARGES_PER_STAGE = 2;

    private ManaSourceGrowthRules() {
    }

    public static SourceState advance(SourceState state, Environment environment, long elapsedTicks) {
        if (elapsedTicks < 0) {
            throw new IllegalArgumentException("Elapsed ticks cannot be negative");
        }
        if (elapsedTicks == 0 || state.origin() == SourceOrigin.CONSTRUCTED
                || !environment.chunkLoaded() || !environment.geologicallySupported()) {
            return state;
        }

        int appliedTicks = (int) Math.min(elapsedTicks, MAX_CATCH_UP_TICKS);
        long growthTotal = (long) state.growthProgressTicks() + appliedTicks;
        int stagesGained = (int) Math.min(MAX_GROWTH_STAGE,
                growthTotal / growthInterval(environment));
        int growthStage = Math.min(MAX_GROWTH_STAGE, state.growthStage() + stagesGained);
        int growthProgress = growthStage == MAX_GROWTH_STAGE
                ? 0 : (int) (growthTotal % growthInterval(environment));

        int capacity = capacityForStage(growthStage);
        int charges = Math.min(state.charges(), capacity);
        long rechargeTotal = (long) state.rechargeProgressTicks() + appliedTicks;
        int availableCharges = (int) Math.min(capacity,
                rechargeTotal / rechargeInterval(environment));
        int added = Math.min(availableCharges, capacity - charges);
        charges += added;
        int rechargeProgress = charges == capacity
                ? 0 : (int) (rechargeTotal % rechargeInterval(environment));

        return new SourceState(state.origin(), growthStage, charges,
                rechargeProgress, growthProgress);
    }

    public static int capacityForStage(int growthStage) {
        if (growthStage < 0 || growthStage > MAX_GROWTH_STAGE) {
            throw new IllegalArgumentException("Growth stage is outside the supported range");
        }
        return (growthStage + 1) * CHARGES_PER_STAGE;
    }

    public static int rechargeInterval(Environment environment) {
        return TICKS_PER_DAY * (1 + environment.competingSources());
    }

    public static int growthInterval(Environment environment) {
        return 3 * TICKS_PER_DAY * (1 + environment.competingSources());
    }

    public enum SourceOrigin {
        NATURAL,
        CONSTRUCTED
    }

    public record SourceState(SourceOrigin origin, int growthStage, int charges,
                              int rechargeProgressTicks, int growthProgressTicks) {
        public SourceState {
            if (origin == null || growthStage < 0 || growthStage > MAX_GROWTH_STAGE
                    || charges < 0 || charges > capacityForStage(growthStage)
                    || rechargeProgressTicks < 0 || growthProgressTicks < 0) {
                throw new IllegalArgumentException("Invalid mana source state");
            }
        }

        public static SourceState youngNatural() {
            return new SourceState(SourceOrigin.NATURAL, 0, 0, 0, 0);
        }

        public static SourceState fullConstructed() {
            return new SourceState(SourceOrigin.CONSTRUCTED, MAX_GROWTH_STAGE,
                    capacityForStage(MAX_GROWTH_STAGE), 0, 0);
        }
    }

    public record Environment(boolean chunkLoaded, boolean geologicallySupported,
                              int competingSources) {
        public Environment {
            if (competingSources < 0 || competingSources > 3) {
                throw new IllegalArgumentException("Competing sources must be between zero and three");
            }
        }
    }
}
