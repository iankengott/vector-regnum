package vectorregnum.neoforge.progression;

import net.minecraft.util.StringRepresentable;
import vectorregnum.core.Element;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Elemental resonance shared by source nodes and spell draws. */
public enum ManaAffinity implements StringRepresentable {
    WATER,
    FIRE,
    AIR,
    EARTH,
    LIGHTNING,
    TIME,
    SPACE,
    LIGHT,
    DARK,
    NATURE,
    ICE,
    SOUND,
    VOID,
    ARCANE,
    /** Decode-only value for pre-priority-21 chunk blockstate palettes. */
    LEGACY_FROST("frost");

    private static final List<ManaAffinity> CHANNEL_VALUES = List.of(
            WATER, FIRE, AIR, EARTH, LIGHTNING, TIME, SPACE, LIGHT, DARK,
            NATURE, ICE, SOUND, VOID, ARCANE);
    private final String serializedName;

    ManaAffinity() {
        this.serializedName = name().toLowerCase(Locale.ROOT);
    }

    ManaAffinity(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    /** Stable user/data ID; frost is accepted only as a legacy input alias. */
    public static Optional<ManaAffinity> fromId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        String canonical = id.trim().toLowerCase(Locale.ROOT);
        if (canonical.equals("frost")) {
            canonical = "ice";
        }
        try {
            return Optional.of(valueOf(canonical.toUpperCase(Locale.ROOT)).canonical());
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    /** All selectable channel resonances in canonical order, including neutral Arcane. */
    public static List<ManaAffinity> channelValues() {
        return CHANNEL_VALUES;
    }

    /** Converges the decode-only pre-21 blockstate value on first load/use. */
    public ManaAffinity canonical() {
        return this == LEGACY_FROST ? ICE : this;
    }

    public Element element() {
        return Element.fromId(canonical().getSerializedName()).orElseThrow(
                () -> new IllegalStateException("Core element missing for " + name()));
    }

    public static ManaAffinity fromElement(Element element) {
        if (element == null) {
            throw new IllegalArgumentException("Element is required");
        }
        return fromId(element.id()).orElseThrow(
                () -> new IllegalArgumentException("Unsupported element: " + element));
    }
}
