package vectorregnum.core.circle;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Immutable authored circle. Its source order is geometric, not list insertion order. */
public record MagicCircle(
        int schemaVersion,
        String id,
        String name,
        int ringCount,
        int slotsPerRing,
        List<PlacedSigil> sigils) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    public MagicCircle {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported circle schema version " + schemaVersion);
        }
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(sigils, "sigils");
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("circle id must match " + ID.pattern());
        }
        if (name.isBlank() || name.length() > 128) {
            throw new IllegalArgumentException("circle name must contain 1 to 128 characters");
        }
        if (ringCount < 1 || ringCount > 16) {
            throw new IllegalArgumentException("ringCount must be from 1 to 16");
        }
        if (slotsPerRing < 1 || slotsPerRing > 64) {
            throw new IllegalArgumentException("slotsPerRing must be from 1 to 64");
        }
        if (sigils.size() > ringCount * slotsPerRing) {
            throw new IllegalArgumentException("circle contains more sigils than positions");
        }
        sigils = CircleOrder.clockwiseThenInward(List.copyOf(sigils));
        Set<CircleCoordinate> occupied = new HashSet<>();
        for (PlacedSigil sigil : sigils) {
            Objects.requireNonNull(sigil, "sigils cannot contain null");
            sigil.coordinate().requireInside(ringCount, slotsPerRing);
            if (!occupied.add(sigil.coordinate())) {
                throw new IllegalArgumentException("duplicate sigil coordinate " + sigil.coordinate());
            }
        }
    }

    public static MagicCircle empty(String id, String name, int ringCount, int slotsPerRing) {
        return new MagicCircle(CURRENT_SCHEMA_VERSION, id, name, ringCount, slotsPerRing, List.of());
    }

    public List<PlacedSigil> executionOrder() {
        return sigils;
    }
}
