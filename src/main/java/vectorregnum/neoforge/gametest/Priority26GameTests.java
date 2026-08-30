package vectorregnum.neoforge.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import vectorregnum.core.EffectCommand;
import vectorregnum.core.Element;
import vectorregnum.core.Vec3;
import vectorregnum.core.WildMagicCategory;
import vectorregnum.core.security.ForcedAttentionPolicy;
import vectorregnum.core.security.MechanicCapability;
import vectorregnum.core.security.MechanicDecision;
import vectorregnum.core.security.MechanicLimits;
import vectorregnum.core.security.MechanicRequest;

/** Real NeoForge server coverage for the priority-26 bounded mechanic contract. */
@GameTestHolder("vector_regnum")
@PrefixGameTestTemplate(false)
public final class Priority26GameTests {
    @GameTest(template = "empty", batch = "priority26_security")
    public void serverLoadsCuratedCapabilityPolicy(GameTestHelper context) {
        MechanicRequest request = new MechanicRequest(MechanicCapability.FORCED_ATTENTION,
                12.0, 20, 1, true, true, true, true, true, true, true);
        context.assertTrue(ForcedAttentionPolicy.evaluate(request, 30.0, .5).allowed(),
                "valid attention request should pass the shared policy");
        context.assertValueEqual(MechanicDecision.Code.RANGE_EXCEEDED,
                vectorregnum.core.security.MechanicSecurityPolicy.evaluate(new MechanicRequest(
                        MechanicCapability.FORCED_ATTENTION, MechanicLimits.MAX_RANGE + 1,
                        20, 1, true, true, true, true, true, true, true)).code(),
                "server policy must reject an over-range request");
        context.succeed();
    }

    @GameTest(template = "empty", batch = "priority26_security")
    public void wildMagicEffectCarriesStableBoundedEnvelope(GameTestHelper context) {
        EffectCommand.WildMagic command = new EffectCommand.WildMagic("priority26",
                WildMagicCategory.COERCIVE_ATTENTION,
                java.util.Optional.of(new Vec3(0, 0, 0)), java.util.Optional.of(Element.VOID),
                java.util.Optional.empty(), 0, "test", 42L);
        context.assertValueEqual(command.envelope(), command.envelope(),
                "Wild Magic envelope must be deterministic");
        context.assertTrue(command.envelope().radius() <= MechanicLimits.MAX_RANGE,
                "Wild Magic radius must be bounded");
        context.assertTrue(command.envelope().targetLimit() <= MechanicLimits.MAX_TARGETS,
                "Wild Magic target count must be bounded");
        context.succeed();
    }

    @GameTest(template = "empty", batch = "priority26_security")
    public void renderOnlyCapabilityCannotBecomeGameplayByPolicy(GameTestHelper context) {
        MechanicRequest request = new MechanicRequest(MechanicCapability.RENDER_ONLY,
                32.0, 1, 16, true, true, true, true, false, false, true);
        context.assertTrue(vectorregnum.core.security.MechanicSecurityPolicy.evaluate(request).allowed(),
                "render-only cues remain available when PvP is disabled");
        context.succeed();
    }
}
