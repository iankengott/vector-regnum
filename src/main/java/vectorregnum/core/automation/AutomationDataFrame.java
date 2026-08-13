package vectorregnum.core.automation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A small immutable data bridge shared with an automation cast.
 *
 * <p>Frames are values, never live views of Minecraft state. This permits an
 * integration to submit from another thread without granting it ownership of
 * the VM, world, block entity, or a mutable collection.</p>
 */
public record AutomationDataFrame(int redstonePower, long worldTick,
        Map<String, Long> channels) {
    public static final int MAX_CHANNELS = 16;
    private static final Pattern CHANNEL = Pattern.compile("[a-z][a-z0-9_.-]{0,31}");

    public AutomationDataFrame {
        if (redstonePower < 0 || redstonePower > 15) {
            throw new IllegalArgumentException("redstonePower must be between 0 and 15");
        }
        if (worldTick < 0) {
            throw new IllegalArgumentException("worldTick must be non-negative");
        }
        Objects.requireNonNull(channels, "channels");
        if (channels.size() > MAX_CHANNELS) {
            throw new IllegalArgumentException("at most " + MAX_CHANNELS + " data channels are allowed");
        }
        LinkedHashMap<String, Long> copy = new LinkedHashMap<>();
        channels.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String key = Objects.requireNonNull(entry.getKey(), "channel name");
            Long value = Objects.requireNonNull(entry.getValue(), "channel value");
            if (!CHANNEL.matcher(key).matches()) {
                throw new IllegalArgumentException("invalid data channel: " + key);
            }
            copy.put(key, value);
        });
        channels = Map.copyOf(copy);
    }

    public static AutomationDataFrame redstone(int power, long worldTick) {
        return new AutomationDataFrame(power, worldTick, Map.of("redstone.power", (long) power));
    }

    public long channelOrDefault(String name, long fallback) {
        return channels.getOrDefault(name, fallback);
    }
}
