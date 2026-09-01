package vectorregnum.api.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CastModifierTest {
    @Test
    void compositionClampsEveryAggregateDimension() {
        CastModifier high = new CastModifier(2.0, 1.5, 2.0, 1.1);
        CastModifier low = new CastModifier(0.5, 0.5, 0.5, 0.5);
        CastModifier aggregate = high.combine(high).combine(low);

        assertEquals(1.0, aggregate.manaFactor());
        assertEquals(1.0, aggregate.castingTimeFactor());
        assertEquals(1.0, aggregate.upkeepFactor());
        assertEquals(0.605, aggregate.instabilityFactor(), 1.0e-12);
    }

    @Test
    void factorsAndAppliedQuoteAreFiniteAndBounded() {
        CastModifier modifier = new CastModifier(0.5, 2.0, 0.75, 1.25);
        CastParameters quote = new CastParameters(100.0, 40.0, 8.0, 4.0);
        CastParameters adjusted = modifier.apply(quote);

        assertEquals(50.0, adjusted.mana());
        assertEquals(80.0, adjusted.castingTime());
        assertEquals(6.0, adjusted.upkeep());
        assertEquals(5.0, adjusted.instability());
        assertThrows(IllegalArgumentException.class,
                () -> new CastModifier(Double.POSITIVE_INFINITY, 1.0, 1.0, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new CastModifier(1.0, 1.0, 1.0, 2.01));
    }

    @Test
    void identityDoesNotChangeQuote() {
        CastParameters quote = new CastParameters(3.0, 4.0, 5.0, 6.0);
        assertEquals(quote, CastModifier.identity().apply(quote));
        assertEquals(CastModifier.IDENTITY, CastModifier.identity());
    }
}
