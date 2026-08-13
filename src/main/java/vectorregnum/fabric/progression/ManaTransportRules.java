package vectorregnum.fabric.progression;

/** Deterministic, conservative transport rules for source and storage adapters. */
public final class ManaTransportRules {
    private static final int EFFICIENCY_SCALE = 10_000;

    private ManaTransportRules() {
    }

    public static TransferResult transfer(ManaReservoir source, ManaReservoir destination,
            int requested, int distance, ConduitTier conduit) {
        if (source == null || destination == null || conduit == null) {
            throw new IllegalArgumentException("Transfer endpoints and conduit are required");
        }
        if (requested < 0 || distance < 0) {
            throw new IllegalArgumentException("Requested mana and distance cannot be negative");
        }
        if (requested == 0 || source.stored() == 0 || destination.space() == 0
                || distance > conduit.maximumDistance()) {
            return new TransferResult(source, destination, 0, 0);
        }

        int compatibility = (int) Math.floor(EFFICIENCY_SCALE
                * ManaDrawRules.compatibility(source.affinity(), destination.affinity()));
        int efficiency = conduit.efficiencyBasisPoints() * compatibility / EFFICIENCY_SCALE;
        if (efficiency == 0) {
            return new TransferResult(source, destination, 0, 0);
        }

        int availableInput = Math.min(requested,
                Math.min(source.stored(), conduit.throughputPerTick()));
        int inputForSpace = ceilDivide(destination.space() * EFFICIENCY_SCALE, efficiency);
        int extracted = Math.min(availableInput, inputForSpace);
        int delivered = extracted * efficiency / EFFICIENCY_SCALE;
        if (delivered == 0) {
            return new TransferResult(source, destination, 0, 0);
        }

        ManaReservoir sourceAfter = source.withStored(source.stored() - extracted);
        ManaReservoir destinationAfter = destination.withStored(destination.stored() + delivered);
        return new TransferResult(sourceAfter, destinationAfter, delivered, extracted - delivered);
    }

    private static int ceilDivide(int dividend, int divisor) {
        return (dividend + divisor - 1) / divisor;
    }

    public enum ConduitTier {
        RAW_CRYSTAL(8, 25, 8_000),
        RUNED(24, 100, 9_500),
        RESONANT(64, 400, 10_000);

        private final int maximumDistance;
        private final int throughputPerTick;
        private final int efficiencyBasisPoints;

        ConduitTier(int maximumDistance, int throughputPerTick, int efficiencyBasisPoints) {
            this.maximumDistance = maximumDistance;
            this.throughputPerTick = throughputPerTick;
            this.efficiencyBasisPoints = efficiencyBasisPoints;
        }

        public int maximumDistance() {
            return maximumDistance;
        }

        public int throughputPerTick() {
            return throughputPerTick;
        }

        public int efficiencyBasisPoints() {
            return efficiencyBasisPoints;
        }
    }

    public record TransferResult(ManaReservoir source, ManaReservoir destination,
                                 int delivered, int dissipated) {
        public TransferResult {
            if (source == null || destination == null || delivered < 0 || dissipated < 0) {
                throw new IllegalArgumentException("Invalid mana transfer result");
            }
        }

        public int extracted() {
            return delivered + dissipated;
        }
    }
}
