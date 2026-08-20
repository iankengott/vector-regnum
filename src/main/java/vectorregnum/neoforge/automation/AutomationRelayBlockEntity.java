package vectorregnum.neoforge.automation;

import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import vectorregnum.core.automation.AutomationDataFrame;
import vectorregnum.core.automation.AutomationInvocation;
import vectorregnum.core.automation.AutomationRule;
import vectorregnum.core.circle.CirclePersistence;
import vectorregnum.core.circle.MagicCircle;
import vectorregnum.neoforge.CircleAuthoringService;
import vectorregnum.neoforge.VectorRegnumMod;

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
        super(AutomationContent.automationRelayEntity(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state,
            AutomationRelayBlockEntity relay) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        int current = serverLevel.getBestNeighborSignal(pos);
        int previous = relay.observedPower;
        relay.observedPower = current;
        if (relay.configured() && relay.rule.shouldTrigger(previous, current,
                serverLevel.getGameTime(), relay.lastAcceptedTick)) {
            AutomationDataFrame frame = AutomationDataBridges.snapshot(serverLevel, pos, current);
            if (AutomationService.submit(relay.owner, serverLevel, pos,
                    AutomationInvocation.TriggerCause.REDSTONE, frame)) {
                relay.lastAcceptedTick = serverLevel.getGameTime();
                relay.acceptedActivations++;
                relay.setChanged();
            }
        } else if (previous != current) {
            relay.setChanged();
        }
    }

    public boolean configure(ServerPlayer player, MagicCircle circle, AutomationRule newRule) {
        if (owner != null && !owner.equals(player.getUUID()) && !player.hasPermissions(2)) {
            return false;
        }
        owner = player.getUUID();
        program = CirclePersistence.encode(circle);
        rule = newRule;
        lastOutcome = "programmed; awaiting signal";
        setChanged();
        return true;
    }

    public boolean reconfigureRule(ServerPlayer player, AutomationRule newRule) {
        if (!owns(player) || !configured()) return false;
        rule = newRule;
        setChanged();
        return true;
    }

    public boolean requestRemote(ServerPlayer player) {
        if (owner == null || !owner.equals(player.getUUID()) || !configured()
                || !(level instanceof ServerLevel serverLevel)) return false;
        if (lastAcceptedTick >= 0
                && serverLevel.getGameTime() - lastAcceptedTick < rule.cooldownTicks()) return false;
        int power = serverLevel.getBestNeighborSignal(worldPosition);
        AutomationDataFrame base = AutomationDataBridges.snapshot(serverLevel, worldPosition, power);
        LinkedHashMap<String, Long> channels = new LinkedHashMap<>(base.channels());
        if (channels.size() < AutomationDataFrame.MAX_CHANNELS) {
            channels.put("remote.request", 1L);
        }
        AutomationDataFrame frame = new AutomationDataFrame(power, base.worldTick(), channels);
        boolean accepted = AutomationService.submit(owner, serverLevel, worldPosition,
                AutomationInvocation.TriggerCause.REMOTE, frame);
        if (accepted) {
            lastAcceptedTick = serverLevel.getGameTime();
            acceptedActivations++;
            setChanged();
        }
        return accepted;
    }

    void execute(ServerPlayer player, AutomationInvocation invocation) {
        if (!owns(player) || !configured()) {
            recordOutcome(false, "owner or program changed before dispatch", invocation.data());
            return;
        }
        try {
            MagicCircle circle = CirclePersistence.decode(program);
            boolean succeeded = CircleAuthoringService.activateCircleAt(player, circle, true,
                    Vec3.atCenterOf(worldPosition).add(0.0, 0.55, 0.0));
            recordOutcome(succeeded, succeeded ? "cast accepted" : "cast rejected",
                    invocation.data());
        } catch (RuntimeException exception) {
            VectorRegnumMod.LOGGER.warn("Rejected corrupt automation relay at {}", worldPosition, exception);
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
        setChanged();
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
        setChanged();
        if (level != null) level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
    }

    public void reportStatus(ServerPlayer player) {
        String status = configured()
                ? String.format(Locale.ROOT, "%s • %s threshold %d • cooldown %dt • input %d • %d/%d successful",
                        lastOutcome, rule.mode().name().toLowerCase(Locale.ROOT), rule.threshold(),
                        rule.cooldownTicks(), lastBridgePower, successfulActivations, acceptedActivations)
                : "unprogrammed — use /vectorregnum automation program <x> <y> <z>";
        player.sendSystemMessage(Component.literal("Automation relay: " + status)
                .withStyle(ChatFormatting.AQUA), false);
        if (!lastData.isEmpty()) {
            player.sendSystemMessage(Component.literal("Last bridge frame: " + lastData)
                    .withStyle(ChatFormatting.GRAY), false);
        }
    }

    public boolean owns(ServerPlayer player) {
        return owner != null && (owner.equals(player.getUUID()) || player.hasPermissions(2));
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
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);
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
        CompoundTag data = nbt.getCompound("last_data");
        LinkedHashMap<String, Long> restoredData = new LinkedHashMap<>();
        data.getAllKeys().stream().sorted().limit(AutomationDataFrame.MAX_CHANNELS)
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
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);
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
            CompoundTag data = new CompoundTag();
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

    private static AutomationRule restoreRule(CompoundTag nbt) {
        try {
            return new AutomationRule(AutomationRule.TriggerMode.valueOf(nbt.getString("mode")),
                    nbt.getInt("threshold"), nbt.getInt("cooldown"));
        } catch (RuntimeException exception) {
            return AutomationRule.risingEdge();
        }
    }
}
