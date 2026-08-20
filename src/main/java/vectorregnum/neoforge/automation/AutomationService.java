package vectorregnum.neoforge.automation;

import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import vectorregnum.core.automation.AutomationDataFrame;
import vectorregnum.core.automation.AutomationEndpoint;
import vectorregnum.core.automation.AutomationInvocation;
import vectorregnum.core.automation.AutomationScheduler;
import vectorregnum.neoforge.VectorRegnumMod;
import vectorregnum.neoforge.multiplayer.SpellSecurityPolicy;

/** Fabric adapter that owns dequeue and performs every world/VM mutation on the server tick. */
public final class AutomationService {
    private static final AutomationScheduler SCHEDULER = new AutomationScheduler();
    private static boolean initialized;

    private AutomationService() {
    }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        AutomationContent.initialize();
        ServerTickEvents.END_SERVER_TICK.register(AutomationService::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> SCHEDULER.clear());
    }

    public static boolean submit(UUID owner, ServerWorld world, BlockPos position,
            AutomationInvocation.TriggerCause cause, AutomationDataFrame frame) {
        AutomationEndpoint endpoint = new AutomationEndpoint(
                world.getRegistryKey().getValue().toString(),
                position.getX(), position.getY(), position.getZ());
        AutomationScheduler.SubmitResult result = SCHEDULER.submit(
                new AutomationInvocation(owner, endpoint, cause, frame));
        if (result != AutomationScheduler.SubmitResult.ACCEPTED) {
            VectorRegnumMod.LOGGER.warn("Rejected automation request for {} at {}: {}",
                    owner, endpoint.stableKey(), result);
        }
        return result == AutomationScheduler.SubmitResult.ACCEPTED;
    }

    /** Thread-safe ingress for integrations that already own an immutable endpoint/frame. */
    public static boolean submitData(UUID owner, AutomationEndpoint endpoint,
            AutomationDataFrame frame) {
        AutomationScheduler.SubmitResult result = SCHEDULER.submit(new AutomationInvocation(
                owner, endpoint, AutomationInvocation.TriggerCause.DATA_BRIDGE, frame));
        return result == AutomationScheduler.SubmitResult.ACCEPTED;
    }

    public static int pendingCount() {
        return SCHEDULER.pendingCount();
    }

    private static void tick(MinecraftServer server) {
        SCHEDULER.drain(invocation -> {
            try {
                dispatch(server, invocation);
            } catch (RuntimeException exception) {
                VectorRegnumMod.LOGGER.error("Automation invocation failed safely at {}",
                        invocation.endpoint().stableKey(), exception);
            }
        });
    }

    private static void dispatch(MinecraftServer server, AutomationInvocation invocation) {
        BlockPos position = new BlockPos(invocation.endpoint().x(),
                invocation.endpoint().y(), invocation.endpoint().z());
        ServerWorld world = null;
        for (ServerWorld candidate : server.getWorlds()) {
            if (candidate.getRegistryKey().getValue().toString()
                    .equals(invocation.endpoint().dimension())) {
                world = candidate;
                break;
            }
        }
        if (world == null || !world.isChunkLoaded(position)) return;
        if (!(world.getBlockEntity(position) instanceof AutomationRelayBlockEntity relay)) return;
        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(invocation.owner());
        if (owner == null) {
            relay.recordUnavailable(invocation, "owner offline; request dropped");
            return;
        }
        if (!SpellSecurityPolicy.canModifyBlock(owner, position, world.getBlockState(position))) {
            relay.recordUnavailable(invocation, "claim or world permission denied dispatch");
            return;
        }
        if (!relay.acceptDataDispatch(invocation, world.getTime())) return;
        relay.execute(owner, invocation);
    }
}
