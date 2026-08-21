package vectorregnum.neoforge.presentation;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

/**
 * Runtime allowlist for built-in vanilla particle emission.
 *
 * <p>When the Veil presentation backend is active it owns every Vector-Regnum
 * particle-based animation; the only vanilla particle this mod may then emit is
 * the enchanting-table particle, which stays a mandatory always-available truth
 * cue. When Veil is absent or failed, the built-in fallback renderer may use
 * its full curated vanilla vocabulary.</p>
 */
public final class VanillaParticleAllowlist {
    private static volatile boolean testingForceFallback;

    private VanillaParticleAllowlist() { }

    /** {@code true} when {@code particle} may be emitted by built-in code right now. */
    public static boolean mayEmit(ParticleOptions particle) {
        if (testingForceFallback || !OptionalPresentationBackend.veilActive()) return true;
        return isEnchant(particle);
    }

    /** The single active-Veil allowlist entry. */
    public static boolean isEnchant(ParticleOptions particle) {
        return particle.getType() == ParticleTypes.ENCHANT;
    }

    /** Test hook simulating a failed backend without loading Veil classes. */
    static void setTestingForceFallback(boolean value) {
        testingForceFallback = value;
    }
}
