package vectorregnum.fabric.automation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AutomationDataBridgesTest {
    @AfterEach
    void clearRegistry() {
        AutomationDataBridges.clearForTesting();
    }

    @Test
    void registrationIsNamespacedUniqueAndBounded() {
        AutomationDataBridge bridge = (world, pos) -> Map.of("value", 1L);
        assertFalse(AutomationDataBridges.register("Bad Namespace", bridge));
        assertTrue(AutomationDataBridges.register("bridge0", bridge));
        assertFalse(AutomationDataBridges.register("bridge0", bridge));
        for (int index = 1; index < AutomationDataBridges.MAX_BRIDGES; index++) {
            assertTrue(AutomationDataBridges.register("bridge" + index, bridge));
        }
        assertFalse(AutomationDataBridges.register("overflow", bridge));
    }
}
