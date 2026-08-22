package vectorregnum.neoforge.presentation;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import vectorregnum.core.presentation.PresentationAccessibility;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The runtime allowlist is the enforcement half of priority 20a: with an active
 * Veil backend exactly one vanilla particle may pass — the enchanting-table
 * truth cue. Without Veil the built-in fallback keeps its full vocabulary.
 */
class VanillaParticleAllowlistTest {
    @AfterEach
    void reset() {
        OptionalPresentationBackend.resetForTests();
        VanillaParticleAllowlist.setTestingForceFallback(false);
    }

    @Test
    void veilAbsentKeepsTheFullFallbackVocabulary() {
        OptionalPresentationBackend.resetForTests();
        assertFalse(OptionalPresentationBackend.veilActive());
        assertTrue(VanillaParticleAllowlist.mayEmit(ParticleTypes.FLAME));
        assertTrue(VanillaParticleAllowlist.mayEmit(ParticleTypes.SMOKE));
        assertTrue(VanillaParticleAllowlist.mayEmit(ParticleTypes.ENCHANT));
    }

    @Test
    void activeBackendAdmitsOnlyTheEnchantingTableCue() {
        OptionalPresentationBackend.setBackendForTests(new NoopBackend());
        assertTrue(OptionalPresentationBackend.veilActive());

        assertTrue(VanillaParticleAllowlist.mayEmit(ParticleTypes.ENCHANT));
        assertFalse(VanillaParticleAllowlist.mayEmit(ParticleTypes.FLAME));
        assertFalse(VanillaParticleAllowlist.mayEmit(ParticleTypes.SMOKE));
        assertFalse(VanillaParticleAllowlist.mayEmit(ParticleTypes.END_ROD));
        assertFalse(VanillaParticleAllowlist.mayEmit(ParticleTypes.ELECTRIC_SPARK));
        assertFalse(VanillaParticleAllowlist.mayEmit(ParticleTypes.EXPLOSION_EMITTER));
    }

    @Test
    void failedBackendRestoresTheFallbackVocabulary() {
        OptionalPresentationBackend.setBackendForTests(new NoopBackend());
        OptionalPresentationBackend.cueEnded(1);
        assertFalse(OptionalPresentationBackend.veilActive());
        assertTrue(VanillaParticleAllowlist.mayEmit(ParticleTypes.FLAME));
    }

    private static final class NoopBackend implements ClientPresentationBackend {
        @Override public String id() { return "test-veil"; }
        @Override public void cueStarted(PresentationCueContext cue,
                PresentationAccessibility accessibility) { }
        @Override public void cueTick(PresentationCueContext cue,
                PresentationAccessibility accessibility, int localAge, int duration,
                double envelope) { }
        @Override public void cueEnded(long cueId) {
            // Mirrors a backend fault so the runtime fails closed to built-in.
            throw new IllegalStateException("simulated backend failure");
        }
        @Override public void resourceReloaded() { }
        @Override public void clear() { }
    }
}
