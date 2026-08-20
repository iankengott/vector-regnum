package vectorregnum.neoforge.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import vectorregnum.core.circle.CircleValue;

class SigilPaletteTest {
    private final SigilPalette palette = SigilPalette.defaults();

    @Test
    void paletteIsDiscoverableByNameCategoryAndDescription() {
        assertTrue(palette.entries().size() >= 45);
        assertTrue(palette.search("element").stream().map(SigilPalette.Entry::id).toList()
                .containsAll(List.of("ELEMENT_FIRE", "ELEMENT_FROST", "ELEMENT_ARCANE", "ELEMENT_VOID")));
        assertTrue(palette.search("entity list").stream()
                .anyMatch(entry -> entry.id().equals("VM_SELECT_RADIUS")));
        assertEquals("EXECUTE", palette.search("finish the circle").getFirst().id());
    }

    @Test
    void typedParameterSchemasProducePersistenceSafeValues() {
        var vector = palette.entry("VM_PUSH_VECTOR").orElseThrow()
                .parseParameters(List.of("1.25", "-2", "3"));
        assertEquals(3, vector.size());
        assertEquals("1.25", ((CircleValue.NumberValue) vector.getFirst()).canonicalText());

        var bool = palette.entry("VM_PUSH_BOOLEAN").orElseThrow()
                .parseParameters(List.of("TRUE"));
        assertTrue(((CircleValue.BooleanValue) bool.getFirst()).value());

        var entity = palette.entry("VM_PUSH_ENTITY").orElseThrow()
                .parseParameters(List.of("text:player-uuid"));
        assertEquals("player-uuid", ((CircleValue.TextValue) entity.getFirst()).value());
    }

    @Test
    void pointListsRepeatTriplesAndControlValuesRejectFractions() {
        var points = palette.entry("VM_PUSH_POINT_LIST").orElseThrow()
                .parseParameters(List.of("0", "1", "2", "3", "4", "5"));
        assertEquals(6, points.size());
        assertInstanceOf(CircleValue.NumberValue.class, points.getLast());

        assertThrows(IllegalArgumentException.class, () -> palette.entry("VM_PUSH_POINT_LIST")
                .orElseThrow().parseParameters(List.of("0", "1")));
        assertThrows(IllegalArgumentException.class, () -> palette.entry("VM_DELAY")
                .orElseThrow().parseParameters(List.of("1.5")));
        assertThrows(IllegalArgumentException.class, () -> palette.entry("VM_PUSH_BOOLEAN")
                .orElseThrow().parseParameters(List.of("perhaps")));
    }

    @Test
    void creationFormIsDiscoverableAndParsesFiveTypedParameters() {
        var entry = palette.entry("VM_CREATE_FORM").orElseThrow();
        assertEquals(SigilPalette.Category.CREATION, entry.category());
        var values = entry.parseParameters(List.of("stone", "barrier", "8", "200", "true"));
        assertEquals(5, values.size());
        assertInstanceOf(CircleValue.BooleanValue.class, values.get(4));
        assertTrue(palette.search("material form").contains(entry));
    }
}
