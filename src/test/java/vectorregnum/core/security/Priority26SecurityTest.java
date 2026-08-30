package vectorregnum.core.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import vectorregnum.core.WildMagicCategory;

class Priority26SecurityTest {
    private static MechanicRequest request(MechanicCapability capability) {
        return new MechanicRequest(capability, 12, 10, 1, true, true, true,
                true, true, true, true);
    }

    @Test
    void capabilityCatalogIsCuratedAndRenderOnlyIsNonGameplay() {
        assertTrue(MechanicCapability.RENDER_ONLY != MechanicCapability.WILD_MAGIC);
        assertFalse(MechanicCapability.RENDER_ONLY.gameplay());
        assertTrue(MechanicCapability.SPELL_DISRUPTION.gameplay());
    }

    @Test
    void commonPolicyFailsClosedForBoundsLifecycleAndPermission() {
        assertTrue(MechanicSecurityPolicy.evaluate(request(MechanicCapability.RENDER_ONLY)).allowed());
        assertEquals(MechanicDecision.Code.RANGE_EXCEEDED,
                MechanicSecurityPolicy.evaluate(new MechanicRequest(
                        MechanicCapability.RENDER_ONLY, 32.01, 10, 1, true, true, true,
                        true, true, true, true)).code());
        assertEquals(MechanicDecision.Code.DIMENSION_MISMATCH,
                MechanicSecurityPolicy.evaluate(new MechanicRequest(
                        MechanicCapability.FORCED_ATTENTION, 4, 10, 1, false, true, true,
                        true, true, true, true)).code());
        assertEquals(MechanicDecision.Code.PERMISSION_DENIED,
                MechanicSecurityPolicy.evaluate(new MechanicRequest(
                        MechanicCapability.FORCED_ATTENTION, 4, 10, 1, true, true, true,
                        false, true, true, true)).code());
    }

    @Test
    void disruptionRequiresActiveSpellStanceWeaponAndFreshWindow() {
        MechanicRequest base = request(MechanicCapability.SPELL_DISRUPTION);
        assertEquals(MechanicDecision.Code.NO_ACTIVE_SPELL,
                SpellDisruptionPolicy.evaluate(base, false, true, true, 1).code());
        assertEquals(MechanicDecision.Code.STANCE_REQUIRED,
                SpellDisruptionPolicy.evaluate(base, true, false, true, 1).code());
        assertEquals(MechanicDecision.Code.WEAPON_REQUIRED,
                SpellDisruptionPolicy.evaluate(base, true, true, false, 1).code());
        assertEquals(MechanicDecision.Code.WINDOW_CLOSED,
                SpellDisruptionPolicy.evaluate(base, true, true, true,
                        MechanicLimits.MAX_DISRUPTION_WINDOW_TICKS + 1).code());
        assertTrue(SpellDisruptionPolicy.evaluate(base, true, true, true, 0).allowed());
    }

    @Test
    void forcedAttentionKeepsAngleStrengthAndDurationBounded() {
        MechanicRequest base = request(MechanicCapability.FORCED_ATTENTION);
        assertTrue(ForcedAttentionPolicy.evaluate(base, 28, .035).allowed());
        assertEquals(MechanicDecision.Code.INVALID_ANGLE,
                ForcedAttentionPolicy.evaluate(base, 46, .035).code());
        assertEquals(MechanicDecision.Code.INVALID_STRENGTH,
                ForcedAttentionPolicy.evaluate(base, 28, 0).code());
        MechanicRequest longRequest = new MechanicRequest(MechanicCapability.FORCED_ATTENTION,
                12, 41, 1, true, true, true, true, true, true, true);
        assertEquals(MechanicDecision.Code.DURATION_EXCEEDED,
                ForcedAttentionPolicy.evaluate(longRequest, 28, .035).code());
    }

    @Test
    void wildMagicResolverIsStableAndAlwaysBounded() {
        for (WildMagicCategory category : WildMagicCategory.values()) {
            WildMagicEnvelope first = WildMagicResolver.resolve(category, 99L);
            WildMagicEnvelope second = WildMagicResolver.resolve(category, 99L);
            assertEquals(first, second);
            assertTrue(first.radius() <= MechanicLimits.MAX_RANGE);
            assertTrue(first.durationTicks() <= MechanicLimits.MAX_DURATION_TICKS);
            assertTrue(first.targetLimit() <= MechanicLimits.MAX_TARGETS);
            assertNotEquals(0L, first.variationSeed());
        }
    }
}
