package vectorregnum.neoforge.automation;

import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;

/**
 * Server-tick-only, read-only adapter for bounded external automation data.
 * Implementations return snapshots; they must never retain or mutate world state.
 */
@FunctionalInterface
public interface AutomationDataBridge {
    Map<String, Long> snapshot(ServerLevel world, BlockPos relayPosition);
}
