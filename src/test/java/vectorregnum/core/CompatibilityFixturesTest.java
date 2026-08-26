package vectorregnum.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class CompatibilityFixturesTest {
    private static final CastContext CONTEXT = new CastContext(
            "Archmage_Ian", new Vec3(10.0, 64.0, 10.0), new Vec3(3.0, 0.0, 4.0), 42L);
    private final SpellEngine engine = new SpellEngine();

    @TestFactory
    Stream<DynamicTest> preservesAllNinePrototypeFixtures() {
        return fixtures().stream().map(fixture -> DynamicTest.dynamicTest(fixture.name(), () -> {
            CompiledSpell program = SpellCompiler.compile(fixture.sigils());
            CastResult result = engine.cast(program, CONTEXT);

            assertEquals(fixture.manaCost(), program.totalManaCost(), 0.001);
            assertEquals(fixture.complexity(), program.totalComplexity());
            assertEquals(fixture.success() ? CastResult.Status.SUCCESS : CastResult.Status.SPELL_FAULT,
                    result.status());

            if (fixture.success()) {
                CastResult.Success success = assertInstanceOf(CastResult.Success.class, result);
                assertEquals(1, success.effects().size());
                assertTrue(success.effects().stream().noneMatch(EffectCommand.WildMagic.class::isInstance));
            } else {
                CastResult.SpellFailure failure = assertInstanceOf(CastResult.SpellFailure.class, result);
                assertEquals(fixture.faultIndex(), failure.fault().sourceIndex());
                assertEquals(fixture.category(), failure.fault().wildMagicCategory());
                EffectCommand.WildMagic wild = assertInstanceOf(
                        EffectCommand.WildMagic.class, failure.effects().getFirst());
                assertEquals(fixture.category(), wild.category());
                assertEquals(fixture.faultIndex(), wild.sourceIndex());
            }
        }));
    }

    @Test
    void basicFireboltUsesDynamicNormalizedLookVector() {
        CastResult.Success result = castSuccess(
                sigils("ORIGIN_SELF", "ELEMENT_FIRE", "VECTOR_FORWARD",
                        "SHAPE_PROJECTILE", new Sigil("EXPAND", 1.0), "EXECUTE"));

        SpellSnapshot state = result.snapshot();
        assertEquals(new Vec3(10.0, 64.0, 10.0), state.origin());
        assertEquals(Element.FIRE, state.element().orElseThrow());
        assertEquals(Shape.PROJECTILE, state.shape());
        assertEquals(new Vec3(0.6, 0.0, 0.8), state.direction().orElseThrow());
        assertEquals(1.0, state.radius());
        assertEquals(1.0, state.magnitude());

        EffectCommand.Projectile command = assertInstanceOf(
                EffectCommand.Projectile.class, result.effects().getFirst());
        assertEquals(new Vec3(0.6, 0.0, 0.8), command.direction());
        assertEquals(Element.FIRE, command.element().orElseThrow());
    }

    @Test
    void frostNovaEmitsAuraWithoutRequiringDirection() {
        CastResult.Success result = castSuccess(
                sigils("ORIGIN_SELF", "ELEMENT_ICE", "SHAPE_AURA",
                        new Sigil("EXPAND", 5.0), "EXECUTE"));

        assertTrue(result.snapshot().direction().isEmpty());
        assertEquals(5.0, result.snapshot().radius());
        EffectCommand.Aura command = assertInstanceOf(
                EffectCommand.Aura.class, result.effects().getFirst());
        assertEquals(Element.ICE, command.element().orElseThrow());
    }

    @Test
    void amplifiedFireboltPreservesRadiusAndMagnitude() {
        CastResult.Success result = castSuccess(
                sigils("ORIGIN_SELF", "ELEMENT_FIRE", "VECTOR_FORWARD", "SHAPE_PROJECTILE",
                        new Sigil("EXPAND", 2.0), new Sigil("AMPLIFY", 3.0), "EXECUTE"));

        assertEquals(2.0, result.snapshot().radius());
        assertEquals(3.0, result.snapshot().magnitude());
        assertEquals(184.14, result.manaCost(), 0.001);
        assertEquals(20L, result.complexity());
    }

    private CastResult.Success castSuccess(List<Sigil> sigils) {
        return assertInstanceOf(
                CastResult.Success.class,
                engine.cast(SpellCompiler.compile(sigils), CONTEXT));
    }

    private static List<Sigil> sigils(Object... values) {
        return Stream.of(values)
                .map(value -> value instanceof Sigil sigil ? sigil : new Sigil((String) value))
                .toList();
    }

    private static List<Fixture> fixtures() {
        return List.of(
                new Fixture("Basic Firebolt",
                        sigils("ORIGIN_SELF", "ELEMENT_FIRE", "VECTOR_FORWARD", "SHAPE_PROJECTILE",
                                new Sigil("EXPAND", 1.0), "EXECUTE"),
                        true, -1, null, 85.0, 16),
                new Fixture("Ice Nova",
                        sigils("ORIGIN_SELF", "ELEMENT_ICE", "SHAPE_AURA",
                                new Sigil("EXPAND", 5.0), "EXECUTE"),
                        true, -1, null, 125.9, 14),
                new Fixture("Ground Zero",
                        sigils("ELEMENT_FIRE", "ORIGIN_SELF", "EXECUTE"),
                        false, 0, WildMagicCategory.INTERNAL_MANA_DETONATION, 30.0, 6),
                new Fixture("Premature Expansion",
                        sigils("ORIGIN_SELF", "ELEMENT_ARCANE", new Sigil("EXPAND", 10.0),
                                "SHAPE_AURA", "EXECUTE"),
                        false, 2, WildMagicCategory.UNSTRUCTURED_ELEMENT_BURST, 228.11, 14),
                new Fixture("Greedy Arithmetic",
                        sigils("ORIGIN_SELF", "ELEMENT_FIRE", "VECTOR_FORWARD", "SHAPE_PROJECTILE",
                                new Sigil("EXPAND", "massive"), "EXECUTE"),
                        false, 4, WildMagicCategory.VIOLENT_MISCAST, 80.0, 16),
                new Fixture("Formless Void",
                        sigils("ORIGIN_SELF", "ELEMENT_VOID", "VECTOR_FORWARD", "EXECUTE"),
                        false, 3, WildMagicCategory.UNSTRUCTURED_ELEMENT_BURST, 40.0, 8),
                new Fixture("Blind Projectile",
                        sigils("ORIGIN_SELF", "ELEMENT_FIRE", "SHAPE_PROJECTILE", "EXECUTE"),
                        false, 2, WildMagicCategory.UNSTRUCTURED_ELEMENT_BURST, 55.0, 11),
                new Fixture("Corrupted Rune",
                        sigils("ORIGIN_SELF", "ELEMENT_FIRE", "SHAP_PROJECTILE", "EXECUTE"),
                        false, 2, WildMagicCategory.UNSTRUCTURED_ELEMENT_BURST, 30.0, 6),
                new Fixture("Amplified Firebolt",
                        sigils("ORIGIN_SELF", "ELEMENT_FIRE", "VECTOR_FORWARD", "SHAPE_PROJECTILE",
                                new Sigil("EXPAND", 2.0), new Sigil("AMPLIFY", 3.0), "EXECUTE"),
                        true, -1, null, 184.14, 20));
    }

    private record Fixture(
            String name,
            List<Sigil> sigils,
            boolean success,
            int faultIndex,
            WildMagicCategory category,
            double manaCost,
            long complexity) {
    }
}
