package vectorregnum.fabric.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GuideScrollStateTest {
    @Test
    void clampsWheelKeyboardAndResizeMovementToThePage() {
        GuideScrollState scroll = new GuideScrollState();
        scroll.setExtents(700, 220);
        assertTrue(scroll.canScroll());
        assertTrue(scroll.scrollBy(90));
        assertEquals(90, scroll.offset());
        assertTrue(scroll.scrollBy(10_000));
        assertEquals(480, scroll.offset());
        assertFalse(scroll.scrollBy(1));
        scroll.setExtents(250, 220);
        assertEquals(30, scroll.offset());
        scroll.toStart();
        assertEquals(0, scroll.offset());
        scroll.toEnd();
        assertEquals(30, scroll.offset());
    }

    @Test
    void shortPagesCannotAccumulateHiddenOffset() {
        GuideScrollState scroll = new GuideScrollState();
        scroll.setExtents(150, 220);
        assertFalse(scroll.canScroll());
        assertFalse(scroll.scrollBy(30));
        assertEquals(0, scroll.offset());
    }
}
