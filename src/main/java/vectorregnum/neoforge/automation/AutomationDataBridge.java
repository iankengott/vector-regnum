package vectorregnum.neoforge.automation;

import java.util.Map;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Server-tick-only, read-only adapter for bounded external automation data.
 * Implementations return snapshots; they must never retain or mutate world state.
 */
@FunctionalInterface
public interface AutomationDataBridge {
    Map<String, Long> snapshot(ServerWorld world, BlockPos relayPosition);
}
