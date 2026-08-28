package vectorregnum.neoforge;

import vectorregnum.core.Sigil;

import java.util.List;
import java.util.Map;

public final class SpellPresets {
    public static final List<Sigil> FIREBOLT = List.of(
            new Sigil("ORIGIN_SELF"),
            new Sigil("ELEMENT_FIRE"),
            new Sigil("VECTOR_FORWARD"),
            new Sigil("SHAPE_PROJECTILE"),
            new Sigil("EXPAND", 1.0),
            new Sigil("EXECUTE"));

    public static final List<Sigil> ICE_NOVA = List.of(
            new Sigil("ORIGIN_SELF"),
            new Sigil("ELEMENT_ICE"),
            new Sigil("SHAPE_AURA"),
            new Sigil("EXPAND", 5.0),
            new Sigil("EXECUTE"));

    public static final List<Sigil> AMPLIFIED_FIREBOLT = List.of(
            new Sigil("ORIGIN_SELF"),
            new Sigil("ELEMENT_FIRE"),
            new Sigil("VECTOR_FORWARD"),
            new Sigil("SHAPE_PROJECTILE"),
            new Sigil("EXPAND", 2.0),
            new Sigil("AMPLIFY", 3.0),
            new Sigil("EXECUTE"));

    public static final List<Sigil> INTERNAL_MISCAST = List.of(
            new Sigil("ELEMENT_FIRE"),
            new Sigil("ORIGIN_SELF"),
            new Sigil("EXECUTE"));

    public static final List<Sigil> UNSTRUCTURED_MISCAST = List.of(
            new Sigil("ORIGIN_SELF"),
            new Sigil("ELEMENT_ARCANE"),
            new Sigil("EXPAND", 10.0),
            new Sigil("SHAPE_AURA"),
            new Sigil("EXECUTE"));

    public static final List<Sigil> VIOLENT_MISCAST = List.of(
            new Sigil("ORIGIN_SELF"),
            new Sigil("ELEMENT_FIRE"),
            new Sigil("VECTOR_FORWARD"),
            new Sigil("SHAPE_PROJECTILE"),
            new Sigil("EXPAND", "massive"),
            new Sigil("EXECUTE"));

    public static final Map<String, List<Sigil>> CASTABLE = Map.of(
            "firebolt", FIREBOLT,
            "ice_nova", ICE_NOVA,
            "amplified_firebolt", AMPLIFIED_FIREBOLT);

    public static final Map<String, List<Sigil>> MISCASTS = Map.of(
            "internal", INTERNAL_MISCAST,
            "unstructured", UNSTRUCTURED_MISCAST,
            "violent", VIOLENT_MISCAST);

    private SpellPresets() {
    }
}
