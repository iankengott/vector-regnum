package vectorregnum.core.circle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CirclePersistenceTest {
    @Test
    void circleRoundTripIsCanonicalAndPreservesTypedParameters() {
        MagicCircle circle = new MagicCircle(1, "persist", "A circle with spaces", 2, 8, List.of(
                new PlacedSigil(new CircleCoordinate(1, 0), "EXECUTE", List.of()),
                new PlacedSigil(new CircleCoordinate(0, 3), "EXPAND",
                        List.of(new CircleValue.NumberValue("2.5000"))),
                new PlacedSigil(new CircleCoordinate(0, 0), "ORIGIN_SELF", List.of())));

        String encoded = CirclePersistence.encode(circle);
        MagicCircle decoded = CirclePersistence.decode(encoded);
        assertEquals(circle, decoded);
        assertEquals(encoded, CirclePersistence.encode(decoded));
        CircleValue.NumberValue number = (CircleValue.NumberValue) decoded.sigils().get(1)
                .parameters().getFirst();
        assertEquals("2.5", number.canonicalText());
        assertTrue(encoded.endsWith("\n"));
    }

    @Test
    void checksumDetectsAnyPayloadMutation() {
        String encoded = CirclePersistence.encode(MagicCircleAuthoringTest.validCircle());
        String tampered = encoded.replace("rings\t2", "rings\t3");
        CirclePersistence.PersistenceException fault = assertThrows(
                CirclePersistence.PersistenceException.class,
                () -> CirclePersistence.decode(tampered));
        assertEquals("checksum mismatch", fault.getMessage());
    }

    @Test
    void malformedAndUnsupportedDocumentsFailClosed() {
        assertThrows(CirclePersistence.PersistenceException.class,
                () -> CirclePersistence.decode("vr-circle\t99\n"));
        assertThrows(CirclePersistence.PersistenceException.class,
                () -> CirclePersistence.decode(null));
    }
}
