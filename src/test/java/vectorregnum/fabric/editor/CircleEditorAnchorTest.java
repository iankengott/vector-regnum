package vectorregnum.fabric.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CircleEditorAnchorTest {
    @Test
    void anchorIsAWorldFaceWithoutAnyPlayerIdentity() {
        CircleEditorAnchor anchor = new CircleEditorAnchor("minecraft:overworld",
                12, 64, -8, CircleEditorAnchor.Face.NORTH);

        assertEquals("minecraft:overworld 12,64,-8 north", anchor.description());
        assertEquals(0, anchor.face().offsetX());
        assertEquals(-1, anchor.face().offsetZ());
    }

    @Test
    void anchorRejectsUnboundedOrMissingWorldCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> new CircleEditorAnchor("",
                0, 64, 0, CircleEditorAnchor.Face.UP));
        assertThrows(IllegalArgumentException.class, () -> new CircleEditorAnchor(
                "minecraft:overworld", 30_000_001, 64, 0, CircleEditorAnchor.Face.UP));
    }
}
