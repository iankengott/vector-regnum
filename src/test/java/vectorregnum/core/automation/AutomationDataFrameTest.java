package vectorregnum.core.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AutomationDataFrameTest {
    @Test
    void frameCopiesAndBoundsExternalData() {
        Map<String, Long> mutable = new HashMap<>();
        mutable.put("redstone.power", 9L);
        AutomationDataFrame frame = new AutomationDataFrame(9, 42, mutable);
        mutable.put("redstone.power", 1L);
        assertEquals(9L, frame.channelOrDefault("redstone.power", -1));
        assertThrows(UnsupportedOperationException.class,
                () -> frame.channels().put("foreign.value", 2L));
    }

    @Test
    void channelCountNamesAndPowerAreValidated() {
        Map<String, Long> tooMany = new LinkedHashMap<>();
        for (int i = 0; i <= AutomationDataFrame.MAX_CHANNELS; i++) {
            tooMany.put("channel." + i, (long) i);
        }
        assertThrows(IllegalArgumentException.class,
                () -> new AutomationDataFrame(0, 0, tooMany));
        assertThrows(IllegalArgumentException.class,
                () -> new AutomationDataFrame(16, 0, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new AutomationDataFrame(0, 0, Map.of("Bad Channel", 1L)));
    }
}
