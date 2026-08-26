package vectorregnum.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ElementalAffinityMatrixTest {
    @Test
    void canonicalResourceIsACompleteSymmetricFourBandMatrix() {
        ElementalAffinityMatrix matrix = ElementalAffinityMatrix.canonical();
        assertEquals(14, matrix.percentages().size());
        assertEquals(Set.of(Element.values()), matrix.percentages().keySet());
        for (Element left : Element.values()) {
            assertEquals(14, matrix.percentages().get(left).size());
            for (Element right : Element.values()) {
                assertEquals(matrix.percentage(right, left), matrix.percentage(left, right));
                assertTrue(List.of(25, 50, 75, 100).contains(matrix.percentage(left, right)));
                if (left == right) assertEquals(100, matrix.percentage(left, right));
            }
        }
        assertEquals(25, matrix.percentage(Element.VOID, Element.WATER));
        assertEquals(50, matrix.percentage(Element.ARCANE, Element.WATER));
        assertEquals(100, matrix.percentage(Element.ARCANE, Element.ARCANE));
    }

    @Test
    void canonicalMatrixKeepsRepresentativeCuratedRelationships() {
        ElementalAffinityMatrix matrix = ElementalAffinityMatrix.canonical();
        assertEquals(75, matrix.percentage(Element.WATER, Element.ICE));
        assertEquals(50, matrix.percentage(Element.FIRE, Element.LIGHT));
        assertEquals(25, matrix.percentage(Element.FIRE, Element.ICE));
    }

    @Test
    void malformedBandOrAsymmetricCellIsRejectedAtLoad() throws Exception {
        String json;
        try (var stream = getClass().getResourceAsStream(
                "/data/vector_regnum/elemental_affinities.json")) {
            json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThrows(IllegalArgumentException.class,
                () -> ElementalAffinityMatrix.fromJson(json.replace("\"water\": 100", "\"water\": 90")));
        assertThrows(IllegalArgumentException.class,
                () -> ElementalAffinityMatrix.fromJson(json.replaceFirst("\"fire\": 75", "\"fire\": 50")));
    }

    @Test
    void costsAndStabilityUseTheBoundedEfficiencyAndRejectUnsafeBases() {
        ElementalAffinityMatrix matrix = ElementalAffinityMatrix.canonical();
        assertEquals(40.0, matrix.adjustedCost(10, Element.VOID, Element.WATER));
        assertEquals(20.0, matrix.upkeepCost(10, Element.ARCANE, Element.WATER));
        assertEquals(.25, matrix.stabilityEfficiency(Element.VOID, Element.WATER));
        assertThrows(IllegalArgumentException.class,
                () -> matrix.adjustedCost(Double.NaN, Element.WATER, Element.WATER));
        assertThrows(IllegalArgumentException.class,
                () -> matrix.upkeepCost(-1, Element.WATER, Element.WATER));
    }

    @Test
    void selectorIsStableHasExactlyOneVoidBucketAndNeverReturnsArcane() {
        int voids = 0;
        for (int index = 0; index < 64; index++) {
            Element selected = NaturalElementSelector.select(new UUID(0, index));
            if (selected == Element.VOID) voids++;
            assertTrue(selected.isNatural());
            assertTrue(selected != Element.ARCANE);
        }
        assertEquals(1, voids);
        UUID identity = UUID.fromString("12345678-1234-5678-1234-567812345678");
        assertEquals(NaturalElementSelector.select(identity), NaturalElementSelector.select(identity));
    }
}
