package vectorregnum.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EngineHardeningTest {
    private static final CastContext CONTEXT = new CastContext(
            "tester", new Vec3(1.0, 2.0, 3.0), new Vec3(0.0, 0.0, -2.0), 99L);
    private final SpellEngine engine = new SpellEngine();

    @Test
    void emptySpellFaultsAtEndOfSource() {
        CastResult.SpellFailure failure = castFailure(List.of());
        assertEquals(FaultCode.MISSING_EXECUTE, failure.fault().code());
        assertEquals(0, failure.fault().sourceIndex());
        assertEquals(WildMagicCategory.INTERNAL_MANA_DETONATION,
                failure.fault().wildMagicCategory());
        assertOnlyWildMagic(failure);
    }

    @Test
    void unterminatedSpellNeverEmitsStructuredEffect() {
        CastResult.SpellFailure failure = castFailure(List.of(
                new Sigil("ORIGIN_SELF"), new Sigil("SHAPE_AURA")));
        assertEquals(FaultCode.MISSING_EXECUTE, failure.fault().code());
        assertEquals(2, failure.fault().sourceIndex());
        assertEquals(WildMagicCategory.VIOLENT_MISCAST, failure.fault().wildMagicCategory());
        assertOnlyWildMagic(failure);
    }

    @Test
    void executeMustBeExactlyTerminal() {
        CastResult.SpellFailure failure = castFailure(List.of(
                new Sigil("ORIGIN_SELF"),
                new Sigil("SHAPE_AURA"),
                new Sigil("EXECUTE"),
                new Sigil("EXPAND", 2.0)));
        assertEquals(FaultCode.INSTRUCTION_AFTER_EXECUTE, failure.fault().code());
        assertEquals(3, failure.fault().sourceIndex());
        assertOnlyWildMagic(failure);

        CastResult.SpellFailure duplicate = castFailure(List.of(
                new Sigil("ORIGIN_SELF"),
                new Sigil("SHAPE_AURA"),
                new Sigil("EXECUTE"),
                new Sigil("EXECUTE")));
        assertEquals(FaultCode.INSTRUCTION_AFTER_EXECUTE, duplicate.fault().code());
        assertEquals(3, duplicate.fault().sourceIndex());
    }

    @Test
    void zeroDynamicLookVectorIsAReproducibleSpellFault() {
        CastContext zeroLook = new CastContext("tester", Vec3.ZERO, Vec3.ZERO, 1L);
        CastResult.SpellFailure failure = assertInstanceOf(
                CastResult.SpellFailure.class,
                engine.cast(SpellCompiler.compile(List.of(
                        new Sigil("ORIGIN_SELF"),
                        new Sigil("VECTOR_FORWARD"),
                        new Sigil("SHAPE_PROJECTILE"),
                        new Sigil("EXECUTE"))), zeroLook));
        assertEquals(FaultCode.DIRECTION_REQUIRED, failure.fault().code());
        assertEquals(1, failure.fault().sourceIndex());
        assertEquals(WildMagicCategory.UNSTRUCTURED_ELEMENT_BURST,
                failure.fault().wildMagicCategory());
    }

    @Test
    void unknownElementAndShapeNamesAreRejectedAtTheirSourceIndex() {
        CastResult.SpellFailure element = castFailure(List.of(
                new Sigil("ORIGIN_SELF"),
                new Sigil("ELEMENT_SPAGHETTI"),
                new Sigil("SHAPE_AURA"),
                new Sigil("EXECUTE")));
        assertEquals(FaultCode.UNKNOWN_ELEMENT, element.fault().code());
        assertEquals(1, element.fault().sourceIndex());

        CastResult.SpellFailure shape = castFailure(List.of(
                new Sigil("ORIGIN_SELF"),
                new Sigil("SHAPE_BANANA"),
                new Sigil("EXECUTE")));
        assertEquals(FaultCode.UNKNOWN_SHAPE, shape.fault().code());
        assertEquals(1, shape.fault().sourceIndex());
    }

    @Test
    void nonFiniteNonPositiveAndOverflowingNumbersFaultWithSafeCosts() {
        for (double value : List.of(Double.NaN, Double.POSITIVE_INFINITY, 0.0, -1.0,
                Double.MAX_VALUE)) {
            CompiledSpell program = SpellCompiler.compile(List.of(
                    new Sigil("ORIGIN_SELF"),
                    new Sigil("SHAPE_AURA"),
                    new Sigil("EXPAND", value),
                    new Sigil("EXECUTE")));
            CastResult.SpellFailure failure = assertInstanceOf(
                    CastResult.SpellFailure.class, engine.cast(program, CONTEXT));
            assertTrue(failure.fault().code() == FaultCode.INVALID_NUMBER
                    || failure.fault().code() == FaultCode.NUMERIC_OVERFLOW);
            assertEquals(2, failure.fault().sourceIndex());
            assertTrue(Double.isFinite(program.totalManaCost()));
            assertTrue(program.totalManaCost() >= 0.0);
        }
    }

    @Test
    void malformedOperandsAreAuthoredSpellFaults() {
        CastResult.SpellFailure wrongType = castFailure(List.of(
                new Sigil("ORIGIN_SELF"),
                new Sigil("SHAPE_AURA"),
                new Sigil("EXPAND", "large"),
                new Sigil("EXECUTE")));
        assertEquals(FaultCode.TYPE_MISMATCH, wrongType.fault().code());

        CastResult.SpellFailure wrongCount = castFailure(List.of(
                new Sigil("ORIGIN_SELF", "ignored-before-hardening"),
                new Sigil("SHAPE_AURA"),
                new Sigil("EXECUTE")));
        assertEquals(FaultCode.OPERAND_COUNT, wrongCount.fault().code());
        assertEquals(0, wrongCount.fault().sourceIndex());
    }

    @Test
    void compilationAndResultsAreDefensivelyImmutable() {
        Object[] suppliedParameters = {2.0};
        Sigil expand = new Sigil("EXPAND", suppliedParameters);
        suppliedParameters[0] = -100.0;
        assertEquals(2.0, expand.parameters().getFirst());
        assertThrows(UnsupportedOperationException.class,
                () -> expand.parameters().add(3.0));

        List<Sigil> source = new ArrayList<>(List.of(
                new Sigil("ORIGIN_SELF"),
                new Sigil("SHAPE_AURA"),
                expand,
                new Sigil("EXECUTE")));
        CompiledSpell program = SpellCompiler.compile(source);
        source.clear();
        assertEquals(4, program.instructionCount());
        assertEquals(64.14, program.totalManaCost(), 0.001);
        assertThrows(UnsupportedOperationException.class,
                () -> program.sourceIndices().add(99));

        CastResult.Success result = assertInstanceOf(
                CastResult.Success.class, engine.cast(program, CONTEXT));
        assertThrows(UnsupportedOperationException.class,
                () -> result.effects().add(result.effects().getFirst()));
    }

    @Test
    void sourceIndicesSurviveCompilationExactly() {
        CompiledSpell program = SpellCompiler.compile(List.of(
                new Sigil("ORIGIN_SELF"),
                new Sigil("ELEMENT_FIRE"),
                new Sigil("TYPO"),
                new Sigil("EXECUTE")));
        assertEquals(List.of(0, 1, 2, 3), program.sourceIndices());
        CastResult.SpellFailure failure = assertInstanceOf(
                CastResult.SpellFailure.class, engine.cast(program, CONTEXT));
        assertEquals(2, failure.fault().sourceIndex());
    }

    @Test
    void wildMagicVariationIsDeterministicForTheSameCast() {
        CompiledSpell program = SpellCompiler.compile(List.of(new Sigil("TYPO")));
        CastResult.SpellFailure first = assertInstanceOf(
                CastResult.SpellFailure.class, engine.cast(program, CONTEXT));
        CastResult.SpellFailure second = assertInstanceOf(
                CastResult.SpellFailure.class, engine.cast(program, CONTEXT));
        EffectCommand.WildMagic firstEffect = assertInstanceOf(
                EffectCommand.WildMagic.class, first.effects().getFirst());
        EffectCommand.WildMagic secondEffect = assertInstanceOf(
                EffectCommand.WildMagic.class, second.effects().getFirst());
        assertEquals(first.fault().wildMagicCategory(), second.fault().wildMagicCategory());
        assertEquals(firstEffect.variationSeed(), secondEffect.variationSeed());
    }

    @Test
    void engineFaultsAreExplicitAndNeverProduceWildMagic() {
        CastResult.EngineFailure nullProgram = assertInstanceOf(
                CastResult.EngineFailure.class, engine.cast(null, CONTEXT));
        assertEquals(CastResult.Status.ENGINE_FAULT, nullProgram.status());
        assertTrue(nullProgram.effects().isEmpty());

        CastResult.EngineFailure nullContext = assertInstanceOf(
                CastResult.EngineFailure.class,
                engine.cast(SpellCompiler.compile(List.of()), null));
        assertTrue(nullContext.effects().isEmpty());

        assertThrows(NullPointerException.class, () -> SpellCompiler.compile(null));
    }

    @Test
    void compatibilityRegistriesContainOnlySupportedNames() {
        assertEquals(Element.FIRE, Element.fromId("fire").orElseThrow());
        assertEquals(Element.ICE, Element.fromId("FROST").orElseThrow());
        assertEquals(Element.ICE, Element.fromId("ice").orElseThrow());
        assertTrue(Element.fromId("").isEmpty());
        assertEquals(Element.WATER, Element.fromId("water").orElseThrow());
        assertEquals(Shape.PROJECTILE, Shape.fromId("projectile").orElseThrow());
        assertTrue(Shape.fromId("cube").isEmpty());
    }

    @Test
    void allPublishedCostsAndEffectNumbersAreFiniteAndNonNegative() {
        CompiledSpell program = SpellCompiler.compile(List.of(
                new Sigil("ORIGIN_SELF"),
                new Sigil("SHAPE_AURA"),
                new Sigil("EXPAND", 2),
                new Sigil("AMPLIFY", 3),
                new Sigil("EXECUTE")));
        CastResult.Success success = assertInstanceOf(
                CastResult.Success.class, engine.cast(program, CONTEXT));
        assertTrue(Double.isFinite(success.manaCost()));
        assertFalse(success.manaCost() < 0.0);
        assertTrue(Double.isFinite(success.snapshot().radius()));
        assertTrue(success.snapshot().radius() > 0.0);
        assertTrue(Double.isFinite(success.snapshot().magnitude()));
        assertTrue(success.snapshot().magnitude() > 0.0);
    }

    private CastResult.SpellFailure castFailure(List<Sigil> sigils) {
        return assertInstanceOf(CastResult.SpellFailure.class,
                engine.cast(SpellCompiler.compile(sigils), CONTEXT));
    }

    private static void assertOnlyWildMagic(CastResult.SpellFailure failure) {
        assertEquals(1, failure.effects().size());
        assertInstanceOf(EffectCommand.WildMagic.class, failure.effects().getFirst());
        assertTrue(failure.effects().stream().noneMatch(EffectCommand.Projectile.class::isInstance));
        assertTrue(failure.effects().stream().noneMatch(EffectCommand.Aura.class::isInstance));
    }
}
