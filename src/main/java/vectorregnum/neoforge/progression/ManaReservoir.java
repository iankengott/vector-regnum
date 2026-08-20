package vectorregnum.neoforge.progression;

/** Immutable mana storage snapshot suitable for block-entity persistence. */
public record ManaReservoir(Tier tier, ManaAffinity affinity, int stored) {
    public ManaReservoir {
        if (tier == null || affinity == null || stored < 0 || stored > tier.capacity()) {
            throw new IllegalArgumentException("Invalid mana reservoir snapshot");
        }
    }

    public int capacity() {
        return tier.capacity();
    }

    public int space() {
        return capacity() - stored;
    }

    public ManaReservoir withStored(int amount) {
        return new ManaReservoir(tier, affinity, amount);
    }

    public enum Tier {
        CRYSTAL_VIAL(200),
        RUNED_CELL(1_000),
        RESONANT_VAULT(8_000);

        private final int capacity;

        Tier(int capacity) {
            this.capacity = capacity;
        }

        public int capacity() {
            return capacity;
        }
    }
}
