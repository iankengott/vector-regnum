package vectorregnum.neoforge.automation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import vectorregnum.core.automation.AutomationDataFrame;
import vectorregnum.neoforge.VectorRegnumMod;

/** Bounded extension bridge; registrations may be concurrent, snapshots are server-tick-owned. */
public final class AutomationDataBridges {
    public static final int MAX_BRIDGES = 8;
    private static final CopyOnWriteArrayList<NamedBridge> BRIDGES = new CopyOnWriteArrayList<>();

    private AutomationDataBridges() {
    }

    public static synchronized boolean register(String namespace, AutomationDataBridge bridge) {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(bridge, "bridge");
        if (!namespace.matches("[a-z][a-z0-9_-]{0,15}") || BRIDGES.size() >= MAX_BRIDGES
                || BRIDGES.stream().anyMatch(value -> value.namespace().equals(namespace))) {
            return false;
        }
        BRIDGES.add(new NamedBridge(namespace, bridge));
        return true;
    }

    static AutomationDataFrame snapshot(ServerWorld world, BlockPos position, int redstonePower) {
        LinkedHashMap<String, Long> channels = new LinkedHashMap<>();
        channels.put("redstone.power", (long) redstonePower);
        outer:
        for (NamedBridge named : BRIDGES) {
            try {
                Map<String, Long> values = Map.copyOf(named.bridge().snapshot(world, position));
                for (Map.Entry<String, Long> value : values.entrySet()) {
                    if (channels.size() >= AutomationDataFrame.MAX_CHANNELS) break outer;
                    String key = named.namespace() + "." + value.getKey();
                    if (key.matches("[a-z][a-z0-9_.-]{0,31}") && value.getValue() != null) {
                        channels.putIfAbsent(key, value.getValue());
                    }
                }
            } catch (RuntimeException exception) {
                VectorRegnumMod.LOGGER.warn("Automation data bridge {} failed safely",
                        named.namespace(), exception);
            }
        }
        return new AutomationDataFrame(redstonePower, world.getTime(), channels);
    }

    static void clearForTesting() {
        BRIDGES.clear();
    }

    private record NamedBridge(String namespace, AutomationDataBridge bridge) {
    }
}
