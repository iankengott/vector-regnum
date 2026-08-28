package vectorregnum.neoforge.multiplayer;

import java.util.Locale;
import java.util.UUID;

import vectorregnum.core.Element;
import vectorregnum.core.NaturalElementSelector;
import vectorregnum.neoforge.progression.ManaAffinity;

/** Pure migration rules for persistent player magic state. */
public final class PlayerDataMigration {
    public static final int CURRENT_SCHEMA = 3;

    private PlayerDataMigration() { }

    public static Snapshot migrate(Snapshot stored, boolean deathCopy, double maximumCapacity,
            UUID playerId) {
        double capacity = finiteClamp(stored.capacity(), 0.0, maximumCapacity);
        double mana = finiteClamp(stored.mana(), 0.0, capacity);
        String affinity = normalizeChannel(stored.affinity());
        String naturalElement = normalizeNatural(stored.naturalElement());
        if (naturalElement.isEmpty()) {
            naturalElement = NaturalElementSelector.select(playerId == null
                    ? new UUID(0L, 0L) : playerId).id().toUpperCase(Locale.ROOT);
        }
        String sourceDimension = validDimension(stored.sourceDimension())
                ? stored.sourceDimension() : "";
        long sourcePosition = sourceDimension.isEmpty() ? Long.MIN_VALUE : stored.sourcePosition();
        long channelLockUntil = deathCopy ? 0L : Math.max(0L, stored.channelLockUntil());
        return new Snapshot(CURRENT_SCHEMA, mana, capacity, affinity, naturalElement,
                sourcePosition, sourceDimension, channelLockUntil);
    }

    private static String normalizeChannel(String value) {
        String normalized = value == null ? "" : value.toUpperCase(Locale.ROOT);
        if (normalized.equals("FROST")) normalized = "ICE";
        try {
            return ManaAffinity.valueOf(normalized).name();
        } catch (IllegalArgumentException exception) {
            return ManaAffinity.ARCANE.name();
        }
    }

    private static String normalizeNatural(String value) {
        String normalized = value == null ? "" : value.toUpperCase(Locale.ROOT);
        if (normalized.equals("FROST")) normalized = "ICE";
        return Element.fromId(normalized).filter(Element::isNatural)
                .map(element -> element.id().toUpperCase(Locale.ROOT)).orElse("");
    }

    private static double finiteClamp(double value, double minimum, double maximum) {
        return Math.clamp(Double.isFinite(value) ? value : minimum, minimum, maximum);
    }

    private static boolean validDimension(String value) {
        return value != null && !value.isBlank() && value.length() <= 128
                && value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+");
    }

    public record Snapshot(int schema, double mana, double capacity, String affinity,
            String naturalElement, long sourcePosition, String sourceDimension,
            long channelLockUntil) { }
}
