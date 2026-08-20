package vectorregnum.neoforge.progression;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/** Elemental resonance shared by source nodes and spell draws. */
public enum ManaAffinity implements StringRepresentable {
    ARCANE,
    FIRE,
    FROST,
    VOID;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
