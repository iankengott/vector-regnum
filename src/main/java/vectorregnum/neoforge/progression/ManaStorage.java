package vectorregnum.neoforge.progression;

/**
 * A small, engine-independent finite mana store used by blocks, items, and tests.
 * Transfer methods never create or destroy mana and clamp to both endpoints.
 */
public final class ManaStorage {
    private final int capacity;
    private int stored;

    public ManaStorage(int capacity) {
        this(capacity, 0);
    }

    public ManaStorage(int capacity, int stored) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        if (stored < 0 || stored > capacity) {
            throw new IllegalArgumentException("Stored mana must be within capacity");
        }
        this.capacity = capacity;
        this.stored = stored;
    }

    public int capacity() {
        return capacity;
    }

    public int stored() {
        return stored;
    }

    public int space() {
        return capacity - stored;
    }

    public int insert(int requested) {
        requireNonNegative(requested);
        int accepted = Math.min(requested, space());
        stored += accepted;
        return accepted;
    }

    public int extract(int requested) {
        requireNonNegative(requested);
        int extracted = Math.min(requested, stored);
        stored -= extracted;
        return extracted;
    }

    public static int transfer(ManaStorage source, ManaStorage destination, int requested) {
        if (source == destination) {
            return 0;
        }
        requireNonNegative(requested);
        int moved = Math.min(requested, Math.min(source.stored, destination.space()));
        source.stored -= moved;
        destination.stored += moved;
        return moved;
    }

    private static void requireNonNegative(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Mana amount cannot be negative");
        }
    }
}
