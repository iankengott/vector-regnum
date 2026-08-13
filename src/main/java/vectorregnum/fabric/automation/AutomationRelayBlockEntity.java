package vectorregnum.fabric.automation;

import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import vectorregnum.core.automation.AutomationDataFrame;
import vectorregnum.core.automation.AutomationInvocation;
import vectorregnum.core.automation.AutomationRule;
import vectorregnum.core.circle.CirclePersistence;
import vectorregnum.core.circle.MagicCircle;
import vectorregnum.fabric.CircleAuthoringService;
import vectorregnum.fabric.VectorRegnumMod;

/** Persistent program/configuration; all mutation happens on its server tick. */
public final class AutomationRelayBlockEntity extends BlockEntity {
    private String program = "";
    private UUID owner;
    private AutomationRule rule = AutomationRule.risingEdge();
    private int observedPower;
    private int lastBridgePower;
    private long lastAcceptedTick = -1;
    private long acceptedActivations;
    private long successfulActivations;
    private String lastOutcome = "never activated";
    private Map<String, Long> lastData = Map.of();

    public AutomationRelayBlockEntity(BlockPos pos, BlockState state) {
        super(AutomationContent.AUTOMATION_RELAY_ENTITY, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state,
            AutomationRelayBlockEntity relay) {
        if (!(world instanceof ServerWorld serverWorld)) return;
        int current = serverWorld.getReceivedRedstonePower(pos);
        int previous = relay.observedPower;
        relay.observedPower = current;
        if (relay.configured() && relay.rule.shouldTrigger(previous, current,
                serverWorld.getTime(), relay.lastAcceptedTick)) {
            AutomationDataFrame frame = AutomationDataBridges.snapshot(serverWorld, pos, current);
            if (AutomationService.submit(relay.owner, serverWorld, pos,
                    AutomationInvocation.TriggerCause.REDSTONE, frame)) {
                relay.lastAcceptedTick = serverWorld.getTime();
                relay.acceptedActivations++;
                relay.markDirty();
            }
        } else if (previous != current) {
            relay.markDirty();
        }
    }

    public boolean configure(ServerPlayerEntity player, MagicCircle circle, AutomationRule newRule) {
        if (owner != null && !owner.equals(player.getUuid()) && !player.hasPermissionLevel(2)) {
            return false;
        }
        owner = player.getUuid();
        program = CirclePersistence.encode(circle);
        rule = newRule;
        lastOutcome = "programmed; awaiting signal";
        markDirty();
        return true;
    }

    public boolean reconfigureRule(ServerPlayerEntity player, AutomationRule newRule) {
        if (!owns(player) || !configured()) return false;
        rule = newRule;
        markDirty();
        return true;
    }

    public boolean requestRemote(ServerPlayerEntity player) {
        if (owner == null || !owner.equals(player.getUuid()) || !configured()
                || !(world instanceof ServerWorld serverWorld)) return false;
        if (lastAcceptedTick >= 0
                && serverWorld.getTime() - lastAcceptedTick < rule.cooldownTicks()) return false;
        int power = serverWorld.getReceivedRedstonePower(pos);
        AutomationDataFrame base = AutomationDataBridges.snapshot(serverWorld, pos, power);
        LinkedHashMap<String, Long> channels = new LinkedHashMap<>(base.channels());
        if (channels.size() < AutomationDataFrame.MAX_CHANNELS) {
            channels.put("remote.request", 1L);
        }
        AutomationDataFrame frame = new AutomationDataFrame(power, base.worldTick(), channels);
        boolean accepted = AutomationService.submit(owner, serverWorld, pos,
                AutomationInvocation.TriggerCause.REMOTE, frame);
        if (accepted) {
            lastAcceptedTick = serverWorld.getTime();
            acceptedActivations++;
            markDirty();
        }
        return accepted;
    }

    void execute(ServerPlayerEntity player, AutomationInvocation invocation) {
        if (!owns(player) || !configured()) {
            recordOutcome(false, "owner or program changed before dispatch", invocation.data());
            return;
        }
        try {
            MagicCircle circle = CirclePersistence.decode(program);
            boolean succeeded = CircleAuthoringService.activateCircleAt(player, circle, true,
                    Vec3d.ofCenter(pos).add(0.0, 0.55, 0.0));
            recordOutcome(succeeded, succeeded ? "cast accepted" : "cast rejected",
                    invocation.data());
        } catch (RuntimeException exception) {
            VectorRegnumMod.LOGGER.warn("Rejected corrupt automation relay at {}", pos, exception);
            recordOutcome(false, "stored program is corrupt", invocation.data());
        }
    }

    /** Applies the same persisted cooldown when a foreign data adapter is the producer. */
    boolean acceptDataDispatch(AutomationInvocation invocation, long serverTick) {
        if (invocation.cause() != AutomationInvocation.TriggerCause.DATA_BRIDGE) return true;
        if (owner == null || !owner.equals(invocation.owner()) || !configured()
                || (lastAcceptedTick >= 0 && serverTick - lastAcceptedTick < rule.cooldownTicks())) {
            return false;
        }
        lastAcceptedTick = serverTick;
        acceptedActivations++;
        markDirty();
        return true;
    }

    void recordUnavailable(AutomationInvocation invocation, String reason) {
        recordOutcome(false, reason, invocation.data());
    }

    private void recordOutcome(boolean success, String outcome, AutomationDataFrame frame) {
        if (success) successfulActivations++;
        lastBridgePower = frame.redstonePower();
        lastData = frame.channels();
        lastOutcome = outcome;
        markDirty();
        if (world != null) world.updateComparators(pos, getCachedState().getBlock());
    }

    public void reportStatus(ServerPlayerEntity player) {
        String status = configured()
                ? String.format(Locale.ROOT, "%s • %s threshold %d • cooldown %dt • input %d • %d/%d successful",
                        lastOutcome, rule.mode().name().toLowerCase(Locale.ROOT), rule.threshold(),
                        rule.cooldownTicks(), lastBridgePower, successfulActivations, acceptedActivations)
                : "unprogrammed — use /vectorregnum automation program <x> <y> <z>";
        player.sendMessage(Text.literal("Automation relay: " + status)
                .formatted(Formatting.AQUA), false);
        if (!lastData.isEmpty()) {
            player.sendMessage(Text.literal("Last bridge frame: " + lastData)
                    .formatted(Formatting.GRAY), false);
        }
    }

    public boolean owns(ServerPlayerEntity player) {
        return owner != null && (owner.equals(player.getUuid()) || player.hasPermissionLevel(2));
    }

    public boolean configured() {
        return owner != null && !program.isBlank();
    }

    public int comparatorOutput() {
        return lastBridgePower;
    }

    public Optional<UUID> owner() {
        return Optional.ofNullable(owner);
    }

    public long acceptedActivations() {
        return acceptedActivations;
    }

    public long successfulActivations() {
        return successfulActivations;
    }

    public AutomationRule rule() {
        return rule;
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        program = nbt.getString("program");
        owner = parseUuid(nbt.getString("owner"));
        observedPower = Math.clamp(nbt.getInt("observed_power"), 0, 15);
        lastBridgePower = Math.clamp(nbt.getInt("bridge_power"), 0, 15);
        lastAcceptedTick = nbt.contains("last_accepted_tick") ? nbt.getLong("last_accepted_tick") : -1;
        acceptedActivations = Math.max(0, nbt.getLong("accepted_activations"));
        successfulActivations = Math.min(acceptedActivations,
                Math.max(0, nbt.getLong("successful_activations")));
        lastOutcome = nbt.getString("last_outcome");
        if (lastOutcome.isBlank()) lastOutcome = "restored";
        NbtCompound data = nbt.getCompound("last_data");
        LinkedHashMap<String, Long> restoredData = new LinkedHashMap<>();
        data.getKeys().stream().sorted().limit(AutomationDataFrame.MAX_CHANNELS)
                .forEach(key -> {
                    if (key.matches("[a-z][a-z0-9_.-]{0,31}")) {
                        restoredData.put(key, data.getLong(key));
                    }
                });
        lastData = Map.copyOf(restoredData);
        rule = restoreRule(nbt);
        if (!program.isBlank()) {
            try {
                CirclePersistence.decode(program);
            } catch (RuntimeException exception) {
                program = "";
                lastOutcome = "corrupt program removed during load";
            }
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        if (!program.isBlank()) nbt.putString("program", program);
        if (owner != null) nbt.putString("owner", owner.toString());
        nbt.putString("mode", rule.mode().name());
        nbt.putInt("threshold", rule.threshold());
        nbt.putInt("cooldown", rule.cooldownTicks());
        nbt.putInt("observed_power", observedPower);
        nbt.putInt("bridge_power", lastBridgePower);
        nbt.putLong("last_accepted_tick", lastAcceptedTick);
        nbt.putLong("accepted_activations", acceptedActivations);
        nbt.putLong("successful_activations", successfulActivations);
        nbt.putString("last_outcome", lastOutcome);
        if (!lastData.isEmpty()) {
            NbtCompound data = new NbtCompound();
            lastData.forEach(data::putLong);
            nbt.put("last_data", data);
        }
    }

    private static UUID parseUuid(String value) {
        try {
            return value.isBlank() ? null : UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static AutomationRule restoreRule(NbtCompound nbt) {
        try {
            return new AutomationRule(AutomationRule.TriggerMode.valueOf(nbt.getString("mode")),
                    nbt.getInt("threshold"), nbt.getInt("cooldown"));
        } catch (RuntimeException exception) {
            return AutomationRule.risingEdge();
        }
    }
}
