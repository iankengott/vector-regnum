package vectorregnum.core.security;

import vectorregnum.core.WildMagicCategory;

/** Stable seeded resolver; variation changes expression, never safety ceilings. */
public final class WildMagicResolver {
    private WildMagicResolver() { }

    public static WildMagicEnvelope resolve(WildMagicCategory category, long seed) {
        long mixed = mix(seed, category.ordinal());
        double variation = ((mixed >>> 11) & 0x3ff) / 1023.0;
        return switch (category) {
            case INTERNAL_MANA_DETONATION -> new WildMagicEnvelope(category,
                    2.0 + variation, 3.0 + variation * 2.0, 20, 1, mixed);
            case UNSTRUCTURED_ELEMENT_BURST -> new WildMagicEnvelope(category,
                    3.0 + variation * 2.0, 1.0 + variation, 40, 8, mixed);
            case VIOLENT_MISCAST -> new WildMagicEnvelope(category,
                    4.0 + variation * 2.0, 2.0 + variation * 3.0, 50, 4, mixed);
            case COERCIVE_ATTENTION -> new WildMagicEnvelope(category,
                    6.0 + variation * 2.0, 0.8, 30, 4, mixed);
        };
    }

    private static long mix(long seed, int ordinal) {
        long value = seed ^ (0x9E3779B97F4A7C15L * (ordinal + 1L));
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
