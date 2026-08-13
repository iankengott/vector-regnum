package vectorregnum.fabric.multiplayer;

/** Pure migration rules for persistent player magic state. */
public final class PlayerDataMigration {
    public static final int CURRENT_SCHEMA = 2;

    private PlayerDataMigration() { }

    public static Snapshot migrate(Snapshot stored, boolean deathCopy, double maximumCapacity) {
        double capacity = finiteClamp(stored.capacity(), 0.0, maximumCapacity);
        double mana = finiteClamp(stored.mana(), 0.0, capacity);
        String affinity = switch (stored.affinity() == null ? "" : stored.affinity().toUpperCase()) {
            case "ARCANE", "FIRE", "FROST", "VOID" -> stored.affinity().toUpperCase();
            default -> "ARCANE";
        };
        String sourceDimension = validDimension(stored.sourceDimension())
                ? stored.sourceDimension() : "";
        long sourcePosition = sourceDimension.isEmpty() ? Long.MIN_VALUE : stored.sourcePosition();
        long channelLockUntil = deathCopy ? 0L : Math.max(0L, stored.channelLockUntil());
        return new Snapshot(CURRENT_SCHEMA, mana, capacity, affinity, sourcePosition,
                sourceDimension, channelLockUntil);
    }

    private static double finiteClamp(double value, double minimum, double maximum) {
        return Math.clamp(Double.isFinite(value) ? value : minimum, minimum, maximum);
    }

    private static boolean validDimension(String value) {
        return value != null && !value.isBlank() && value.length() <= 128
                && value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+");
    }

    public record Snapshot(int schema, double mana, double capacity, String affinity,
            long sourcePosition, String sourceDimension, long channelLockUntil) { }
}
