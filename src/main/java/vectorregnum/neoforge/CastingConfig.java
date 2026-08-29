package vectorregnum.neoforge;

import java.util.Map;
import net.neoforged.neoforge.common.ModConfigSpec;
import vectorregnum.core.casting.CastCost;
import vectorregnum.core.casting.CastingPolicy;
import vectorregnum.core.casting.ReagentKind;

/** Server-owned floors, caps, and reagent potency for priority-22 cast quotes. */
public final class CastingConfig {
    public static final double DEFAULT_MINIMUM_UPKEEP = 0.25;
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.DoubleValue MINIMUM_MANA;
    private static final ModConfigSpec.DoubleValue MINIMUM_CASTING_TICKS;
    private static final ModConfigSpec.DoubleValue MINIMUM_UPKEEP;
    private static final ModConfigSpec.DoubleValue MINIMUM_INSTABILITY;
    private static final ModConfigSpec.DoubleValue MAXIMUM_MANA_DISCOUNT;
    private static final ModConfigSpec.DoubleValue MAXIMUM_CASTING_DISCOUNT;
    private static final ModConfigSpec.DoubleValue MAXIMUM_UPKEEP_DISCOUNT;
    private static final ModConfigSpec.DoubleValue MAXIMUM_INSTABILITY_DISCOUNT;
    private static final ModConfigSpec.DoubleValue MANA_PER_REAGENT;
    private static final ModConfigSpec.DoubleValue CASTING_TICKS_PER_REAGENT;
    private static final ModConfigSpec.DoubleValue UPKEEP_PER_REAGENT;
    private static final ModConfigSpec.DoubleValue INSTABILITY_PER_REAGENT;
    private static final ModConfigSpec.IntValue MAXIMUM_PER_KIND;
    private static final ModConfigSpec.IntValue MAXIMUM_TOTAL;
    private static final ModConfigSpec.IntValue MAXIMUM_RITUAL_OFFERING;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Final cost floors. Reagents can never reduce a positive cast below these values.")
                .push("floors");
        MINIMUM_MANA = builder.defineInRange("mana", 1.0, 0.01, 10_000.0);
        MINIMUM_CASTING_TICKS = builder.defineInRange("casting_ticks", 5.0, 1.0, 1_200.0);
        MINIMUM_UPKEEP = builder.defineInRange("upkeep", DEFAULT_MINIMUM_UPKEEP, 0.01, 10_000.0);
        MINIMUM_INSTABILITY = builder.defineInRange("instability", 0.25, 0.01, 100.0);
        builder.pop();

        builder.comment("Maximum absolute reduction that one quote may receive.").push("discount_caps");
        MAXIMUM_MANA_DISCOUNT = builder.defineInRange("mana", 100.0, 0.0, 10_000.0);
        MAXIMUM_CASTING_DISCOUNT = builder.defineInRange("casting_ticks", 100.0, 0.0, 1_200.0);
        MAXIMUM_UPKEEP_DISCOUNT = builder.defineInRange("upkeep", 50.0, 0.0, 10_000.0);
        MAXIMUM_INSTABILITY_DISCOUNT = builder.defineInRange("instability", 1.0, 0.0, 100.0);
        builder.pop();

        builder.comment("Absolute reduction supplied by one staged vanilla reagent.").push("potency");
        MANA_PER_REAGENT = builder.defineInRange("amethyst_mana", 5.0, 0.0, 1_000.0);
        CASTING_TICKS_PER_REAGENT = builder.defineInRange("sugar_casting_ticks", 4.0, 0.0, 200.0);
        UPKEEP_PER_REAGENT = builder.defineInRange("glowstone_upkeep", 1.0, 0.0, 1_000.0);
        INSTABILITY_PER_REAGENT = builder.defineInRange("fermented_eye_instability", 0.15, 0.0, 10.0);
        builder.pop();

        builder.comment("Hard reservation bounds.").push("limits");
        MAXIMUM_PER_KIND = builder.defineInRange("reagents_per_kind", 8, 0, 64);
        MAXIMUM_TOTAL = builder.defineInRange("reagents_per_cast", 16, 0, 128);
        MAXIMUM_RITUAL_OFFERING = builder.defineInRange("ritual_offering_units", 64, 1, 256);
        builder.pop();
        SPEC = builder.build();
    }

    private CastingConfig() {
    }

    public static CastingPolicy policy() {
        CastCost floors = new CastCost(MINIMUM_MANA.get(), MINIMUM_CASTING_TICKS.get(),
                MINIMUM_UPKEEP.get(), MINIMUM_INSTABILITY.get());
        CastCost caps = new CastCost(MAXIMUM_MANA_DISCOUNT.get(), MAXIMUM_CASTING_DISCOUNT.get(),
                MAXIMUM_UPKEEP_DISCOUNT.get(), MAXIMUM_INSTABILITY_DISCOUNT.get());
        return new CastingPolicy(floors, caps, MAXIMUM_PER_KIND.get(), MAXIMUM_TOTAL.get(),
                MAXIMUM_RITUAL_OFFERING.get(), Map.of(
                        ReagentKind.MANA, CastCost.forKind(ReagentKind.MANA, MANA_PER_REAGENT.get()),
                        ReagentKind.CASTING_TIME, CastCost.forKind(ReagentKind.CASTING_TIME,
                                CASTING_TICKS_PER_REAGENT.get()),
                        ReagentKind.UPKEEP, CastCost.forKind(ReagentKind.UPKEEP, UPKEEP_PER_REAGENT.get()),
                        ReagentKind.INSTABILITY, CastCost.forKind(ReagentKind.INSTABILITY,
                                INSTABILITY_PER_REAGENT.get())));
    }
}
