package vectorregnum.fabric.progression;

import net.minecraft.util.StringIdentifiable;

import java.util.Locale;

/** Elemental resonance shared by source nodes and spell draws. */
public enum ManaAffinity implements StringIdentifiable {
    ARCANE,
    FIRE,
    FROST,
    VOID;

    @Override
    public String asString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
