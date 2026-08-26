package vectorregnum.core;

import java.util.Locale;
import java.util.List;
import java.util.Optional;

/** Canonical elemental identities and the neutral raw-mana channel. */
public enum Element {
    WATER(true, false),
    FIRE(true, false),
    AIR(true, false),
    EARTH(true, false),
    LIGHTNING(true, false),
    TIME(true, false),
    SPACE(true, false),
    LIGHT(true, false),
    DARK(true, false),
    NATURE(true, false),
    ICE(true, false),
    SOUND(true, false),
    VOID(true, true),
    ARCANE(false, false);

    private static final List<Element> ORDINARY = List.of(
            WATER, FIRE, AIR, EARTH, LIGHTNING, TIME, SPACE,
            LIGHT, DARK, NATURE, ICE, SOUND);
    private static final List<Element> NATURAL = List.of(
            WATER, FIRE, AIR, EARTH, LIGHTNING, TIME, SPACE,
            LIGHT, DARK, NATURE, ICE, SOUND, VOID);

    private final boolean natural;
    private final boolean rare;

    Element(boolean natural, boolean rare) {
        this.natural = natural;
        this.rare = rare;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean isNatural() { return natural; }

    public boolean isOrdinary() { return ORDINARY.contains(this); }

    public boolean isRare() { return rare; }

    public boolean isNeutralMana() { return this == ARCANE; }

    public static List<Element> ordinary() { return ORDINARY; }

    public static List<Element> natural() { return NATURAL; }

    public static Optional<Element> fromId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        try {
            String normalized = id.toUpperCase(Locale.ROOT);
            // Frost is retained only as an input migration alias. It can never
            // be emitted because the canonical enum value and id are ICE.
            if (normalized.equals("FROST")) normalized = "ICE";
            return Optional.of(valueOf(normalized));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
