package vectorregnum.core.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import vectorregnum.core.presentation.PresentationAccessibility;
import vectorregnum.core.presentation.PresentationAccessibilityCodec;
import vectorregnum.core.presentation.PresentationQuality;

class PresentationAccessibilityCodecTest {
    @Test
    void roundTripPreservesVersionedSensoryPreferences() {
        PresentationAccessibility value = new PresentationAccessibility(
                PresentationQuality.MINIMAL, .7, .4, .9, .2, .8, .35, true, true);
        assertEquals(value, PresentationAccessibilityCodec.decode(
                PresentationAccessibilityCodec.encode(value)));
    }

    @Test
    void malformedOrFutureSettingsFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> PresentationAccessibilityCodec.decode("2|FULL|1|1|1|1|1|1|false|false"));
        assertThrows(IllegalArgumentException.class,
                () -> PresentationAccessibilityCodec.decode("1|FULL|2|1|1|1|1|1|false|false"));
        assertThrows(IllegalArgumentException.class,
                () -> PresentationAccessibilityCodec.decode("not-settings"));
    }
}
