package vectorregnum.neoforge.editor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CircleEditorLayoutTest {
    @Test
    void normalGuiScaleViewportKeepsThreePanelsAndFooterOnScreen() {
        CircleEditorLayout layout = CircleEditorLayout.calculate(427, 240);

        assertTrue(layout.compact());
        assertTrue(layout.palette().right() < layout.canvas().x());
        assertTrue(layout.canvas().right() < layout.inspector().x());
        assertTrue(layout.inspector().right() <= 421);
        assertTrue(layout.inspector().bottom() <= layout.footerTop());
        assertTrue(layout.circleCenterX() - layout.circleRadius() >= layout.canvas().x());
        assertTrue(layout.circleCenterX() + layout.circleRadius() <= layout.canvas().right());
    }

    @Test
    void largerViewportPreservesRoomyLayout() {
        CircleEditorLayout layout = CircleEditorLayout.calculate(854, 480);

        assertFalse(layout.compact());
        assertTrue(layout.canvas().width() > layout.palette().width());
        assertTrue(layout.circleRadius() >= 120);
    }

    @Test
    void rejectsViewportTooSmallForUsableControls() {
        assertThrows(IllegalArgumentException.class,
                () -> CircleEditorLayout.calculate(300, 170));
    }
}
