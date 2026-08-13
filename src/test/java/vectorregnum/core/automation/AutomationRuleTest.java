package vectorregnum.core.automation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AutomationRuleTest {
    @Test
    void risingEdgeUsesThresholdAndCooldown() {
        AutomationRule rule = new AutomationRule(
                AutomationRule.TriggerMode.RISING_EDGE, 8, 20);
        assertFalse(rule.shouldTrigger(0, 7, 100, -1));
        assertTrue(rule.shouldTrigger(7, 8, 100, -1));
        assertFalse(rule.shouldTrigger(8, 15, 101, -1));
        assertFalse(rule.shouldTrigger(0, 15, 119, 100));
        assertTrue(rule.shouldTrigger(0, 15, 120, 100));
    }

    @Test
    void fallingChangeAndHighModesAreExplicit() {
        assertTrue(new AutomationRule(AutomationRule.TriggerMode.FALLING_EDGE, 5, 1)
                .shouldTrigger(5, 4, 1, -1));
        assertTrue(new AutomationRule(AutomationRule.TriggerMode.CHANGE, 5, 1)
                .shouldTrigger(2, 3, 1, -1));
        assertTrue(new AutomationRule(AutomationRule.TriggerMode.WHILE_HIGH, 12, 1)
                .shouldTrigger(15, 12, 1, -1));
    }

    @Test
    void malformedSignalsAndUnboundedCooldownsFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> new AutomationRule(AutomationRule.TriggerMode.RISING_EDGE, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new AutomationRule(AutomationRule.TriggerMode.RISING_EDGE, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> AutomationRule.risingEdge().shouldTrigger(-1, 0, 1, -1));
    }
}
