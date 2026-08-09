package vectorregnum.fabric;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.Blocks;
import vectorregnum.fabric.progression.ProgressionData;
import vectorregnum.fabric.progression.ProgressionSpellLibrary;
import vectorregnum.fabric.progression.ProgressionState;
import vectorregnum.fabric.progression.ProgressionUnlock;
import vectorregnum.fabric.progression.SpellDefinition;
import vectorregnum.fabric.progression.LibrarySpellCostModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Playable server-side effects for every bounded definition in the curated library. */
public final class LibrarySpellService {
    private static final Set<String> IMPLEMENTED_SPELL_IDS = Set.of(
            "ember_lance", "chain_frost", "gravity_slam",
            "aegis_shell", "kinetic_ward",
            "vector_step", "featherfall",
            "mage_light", "excavate", "stoneweave",
            "life_sense", "ore_resonance",
            "sentry_pulse", "harvest_cycle", "redstone_oracle");
    private static final List<HarvestCycle> HARVEST_CYCLES = new ArrayList<>();
    private static boolean initialized;

    private LibrarySpellService() {
    }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        ServerTickEvents.END_SERVER_TICK.register(LibrarySpellService::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            HARVEST_CYCLES.clear();
        });
    }

    public static Set<String> implementedSpellIds() {
        return IMPLEMENTED_SPELL_IDS;
    }

    public static boolean cast(ServerPlayerEntity player, String id) {
        return cast(player, id, true, false);
    }

    static boolean castForShowcase(ServerPlayerEntity player, String id) {
        return cast(player, id, false, true);
    }

    private static boolean cast(
            ServerPlayerEntity player, String id, boolean chargeMana, boolean ignoreUnlock) {
        SpellDefinition spell = ProgressionSpellLibrary.BY_ID.get(id);
        if (spell == null || !IMPLEMENTED_SPELL_IDS.contains(id)) {
            player.sendMessage(Text.literal("Unknown library spell: " + id)
                    .formatted(Formatting.RED), false);
            return false;
        }
        if (chargeMana && ManaData.isChannelLocked(player)) {
            player.sendMessage(Text.literal("Mana channel locked for "
                            + ManaData.remainingLockTicks(player) + " more ticks")
                    .formatted(Formatting.RED), true);
            return false;
        }
        ProgressionState progression = ProgressionData.get(player);
        if (!ignoreUnlock && !spell.isUnlocked(progression)) {
            player.sendMessage(Text.literal("Locked: research " + spell.requiredUnlocks().stream()
                            .map(ProgressionUnlock::id).sorted().toList())
                    .formatted(Formatting.RED), false);
            return false;
        }
        double quotedMana = LibrarySpellCostModel.estimate(spell).total();
        if (chargeMana && !ManaData.ensureAvailable(player, quotedMana)) {
            player.sendMessage(Text.literal(String.format(Locale.ROOT,
                            "%s requires %.0f μ; you have %.1f / %.1f μ",
                            spell.title(), quotedMana, ManaData.available(player),
                            ManaData.capacity(player))).formatted(Formatting.RED), true);
            return false;
        }

        boolean applied = switch (id) {
            case "ember_lance" -> emberLance(player);
            case "chain_frost" -> chainFrost(player);
            case "gravity_slam" -> gravitySlam(player);
            case "aegis_shell" -> aegisShell(player);
            case "kinetic_ward" -> kineticWard(player);
            case "vector_step" -> FabricVmService.launchVectorStep(player, false, 2, 1.4);
            case "featherfall" -> featherfall(player);
            case "mage_light" -> mageLight(player);
            case "excavate" -> excavate(player);
            case "stoneweave" -> stoneweave(player);
            case "life_sense" -> lifeSense(player);
            case "ore_resonance" -> oreResonance(player);
            case "sentry_pulse" -> sentryPulse(player);
            case "harvest_cycle" -> harvestCycle(player);
            case "redstone_oracle" -> redstoneOracle(player, ignoreUnlock);
            default -> false;
        };
        if (!applied) return false;
        if (chargeMana && !ManaData.trySpend(player, quotedMana)) {
            VectorRegnumMod.LOGGER.error("Mana changed during single-threaded library cast {}", id);
            return false;
        }
        player.sendMessage(Text.literal(String.format(Locale.ROOT,
                        "%s executed • %.0f μ • %.1f μ remaining",
                        spell.title(), chargeMana ? quotedMana : 0.0,
                        ManaData.available(player))).formatted(Formatting.AQUA), true);
        return true;
    }

    public static void list(ServerPlayerEntity player) {
        ProgressionState state = ProgressionData.get(player);
        player.sendMessage(Text.literal("VECTOR-REGNUM SPELL LIBRARY • 15 bounded programs")
                .formatted(Formatting.GOLD, Formatting.BOLD), false);
        for (SpellDefinition spell : ProgressionSpellLibrary.ALL) {
            boolean unlocked = spell.isUnlocked(state);
            double quoted = LibrarySpellCostModel.estimate(spell).total();
            player.sendMessage(Text.literal(String.format(Locale.ROOT,
                            "%s %-18s  T%d  %.0f μ  [%s]",
                            unlocked ? "✓" : "◇", spell.id(), spell.tier(), quoted,
                            spell.category().name().toLowerCase(Locale.ROOT)))
                    .formatted(unlocked ? Formatting.GREEN : Formatting.DARK_GRAY), false);
        }
    }

    public static boolean research(ServerPlayerEntity player, ProgressionUnlock unlock) {
        if (ProgressionData.get(player).has(unlock)) {
            player.sendMessage(Text.literal("Already researched: " + unlock.id())
                    .formatted(Formatting.GRAY), false);
            return true;
        }
        if (unlock != ProgressionUnlock.CRYSTAL_HARVEST
                && !ProgressionData.get(player).has(ProgressionUnlock.MANA_STORAGE)) {
            player.sendMessage(Text.literal("Draw from a crystal source before researching spell schools")
                    .formatted(Formatting.RED), false);
            return false;
        }
        double cost = unlock == ProgressionUnlock.CRYSTAL_HARVEST ? 0.0 : 25.0;
        if (!ManaData.ensureAvailable(player, cost) || !ManaData.trySpend(player, cost)) {
            player.sendMessage(Text.literal("Research needs " + cost + " μ")
                    .formatted(Formatting.RED), false);
            return false;
        }
        ProgressionData.unlock(player, unlock);
        return true;
    }

    private static boolean emberLance(ServerPlayerEntity player) {
        return CastService.cast(player, SpellPresets.FIREBOLT, false)
                instanceof vectorregnum.core.CastResult.Success;
    }

    private static boolean chainFrost(ServerPlayerEntity player) {
        List<HostileEntity> targets = hostiles(player, 7.0, 4);
        if (targets.isEmpty()) return noTarget(player, "Chain Frost found no hostile pattern");
        ServerWorld world = player.getServerWorld();
        Vec3d previous = player.getEyePos();
        for (HostileEntity target : targets) {
            drawBeam(world, previous, target.getBodyY(0.5), target.getPos(), ParticleTypes.SNOWFLAKE);
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 160, 2));
            target.damage(world.getDamageSources().magic(), 6.0F);
            previous = target.getPos();
        }
        player.playSound(SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0F, 0.7F);
        return true;
    }

    private static boolean gravitySlam(ServerPlayerEntity player) {
        List<HostileEntity> targets = hostiles(player, 5.0, 32);
        if (targets.isEmpty()) return noTarget(player, "Gravity Slam found no hostile bodies");
        ServerWorld world = player.getServerWorld();
        for (HostileEntity target : targets) {
            target.addVelocity(0.0, -1.35, 0.0);
            target.velocityModified = true;
            target.damage(world.getDamageSources().magic(), 8.0F);
        }
        world.spawnParticles(ParticleTypes.EXPLOSION,
                player.getX(), player.getY(), player.getZ(), 12, 2.5, 0.2, 2.5, 0.03);
        return true;
    }

    private static boolean aegisShell(ServerPlayerEntity player) {
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 120, 1));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 120, 1));
        ring(player.getServerWorld(), player.getPos().add(0, 1, 0), 2.5,
                ParticleTypes.ENCHANT, 48);
        player.playSound(SoundEvents.ITEM_SHIELD_BLOCK, 0.8F, 1.3F);
        return true;
    }

    private static boolean kineticWard(ServerPlayerEntity player) {
        List<HostileEntity> targets = hostiles(player, 4.0, 24);
        if (targets.isEmpty()) return noTarget(player, "Kinetic Ward found no incoming bodies");
        for (HostileEntity target : targets) {
            Vec3d away = target.getPos().subtract(player.getPos());
            if (away.lengthSquared() < 1.0e-6) away = new Vec3d(0, 1, 0);
            FabricVmService.launchKineticWard(player, target,
                    away.normalize().multiply(2.5).add(0, 0.35, 0), false);
        }
        return true;
    }

    private static boolean featherfall(ServerPlayerEntity player) {
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 200, 0));
        player.getServerWorld().spawnParticles(ParticleTypes.CLOUD,
                player.getX(), player.getY(), player.getZ(), 24, 0.5, 0.7, 0.5, 0.02);
        return true;
    }

    private static boolean mageLight(ServerPlayerEntity player) {
        Optional<BlockHitResult> hit = blockHit(player, 32.0);
        if (hit.isEmpty()) return noTarget(player, "Mage Light needs a visible surface");
        ServerWorld world = player.getServerWorld();
        BlockPos target = hit.orElseThrow().getBlockPos().offset(hit.orElseThrow().getSide());
        if (!world.isAir(target)) {
            return noTarget(player, "The light destination is occupied");
        }
        world.setBlockState(target, TemporarySpellContent.MAGE_LIGHT.getDefaultState());
        world.spawnParticles(ParticleTypes.END_ROD,
                target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5,
                30, 0.25, 0.25, 0.25, 0.03);
        return true;
    }

    private static boolean excavate(ServerPlayerEntity player) {
        Optional<BlockHitResult> hit = blockHit(player, 24.0);
        if (hit.isEmpty()) return noTarget(player, "Excavate needs a block target");
        ServerWorld world = player.getServerWorld();
        BlockPos center = hit.orElseThrow().getBlockPos();
        int broken = 0;
        for (BlockPos pos : BlockPos.iterate(center.add(-1, -1, -1), center.add(1, 1, 1))) {
            BlockState state = world.getBlockState(pos);
            if (!state.isAir() && state.getHardness(world, pos) >= 0.0F
                    && world.getBlockEntity(pos) == null && canModify(world, player, pos, state)
                    && broken < 27
                    && world.breakBlock(pos, true, player)) {
                broken++;
            }
        }
        if (broken == 0) return noTarget(player, "Excavate refused protected or unbreakable blocks");
        player.sendMessage(Text.literal("Excavate safely broke " + broken + " blocks")
                .formatted(Formatting.GRAY), false);
        return true;
    }

    private static boolean stoneweave(ServerPlayerEntity player) {
        Optional<BlockHitResult> hit = blockHit(player, 24.0);
        if (hit.isEmpty()) return noTarget(player, "Stoneweave needs a block target");
        ServerWorld world = player.getServerWorld();
        BlockPos pos = hit.orElseThrow().getBlockPos();
        BlockState old = world.getBlockState(pos);
        if (old.isAir() || old.getHardness(world, pos) < 0.0F || world.getBlockEntity(pos) != null) {
            return noTarget(player, "Stoneweave refused that protected material");
        }
        if (!canModify(world, player, pos, old)) {
            return noTarget(player, "Stoneweave was denied by world protection");
        }
        world.setBlockState(pos, Blocks.STONE.getDefaultState());
        world.spawnParticles(ParticleTypes.ENCHANT,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                36, 0.45, 0.45, 0.45, 0.05);
        return true;
    }

    private static boolean lifeSense(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class,
                player.getBoundingBox().expand(16.0), entity -> entity != player && entity.isAlive());
        targets.stream().limit(64).forEach(entity -> {
            entity.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 200, 0));
            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                    entity.getX(), entity.getBodyY(0.5), entity.getZ(),
                    8, 0.25, 0.5, 0.25, 0.02);
        });
        player.sendMessage(Text.literal("Life Sense resolved " + Math.min(64, targets.size()) + " signatures")
                .formatted(Formatting.GREEN), false);
        return true;
    }

    private static boolean oreResonance(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        BlockPos center = player.getBlockPos();
        List<BlockPos> ores = new ArrayList<>();
        for (BlockPos pos : BlockPos.iterate(center.add(-12, -12, -12), center.add(12, 12, 12))) {
            if (isOre(world.getBlockState(pos))) {
                ores.add(pos.toImmutable());
                if (ores.size() >= 32) break;
            }
        }
        for (BlockPos ore : ores) {
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                    ore.getX() + 0.5, ore.getY() + 0.5, ore.getZ() + 0.5,
                    10, 0.25, 0.25, 0.25, 0.02);
        }
        player.sendMessage(Text.literal("Ore Resonance found " + ores.size() + " nearby veins (capped at 32)")
                .formatted(Formatting.AQUA), false);
        return true;
    }

    private static boolean sentryPulse(ServerPlayerEntity player) {
        List<HostileEntity> targets = hostiles(player, 12.0, 3);
        if (targets.isEmpty()) return noTarget(player, "Sentry Pulse reports clear");
        ServerWorld world = player.getServerWorld();
        for (HostileEntity target : targets) {
            drawBeam(world, player.getEyePos().x, player.getEyePos().y, player.getEyePos().z,
                    target.getBodyY(0.5), target.getPos(), ParticleTypes.END_ROD);
            target.damage(world.getDamageSources().magic(), 7.0F);
        }
        return true;
    }

    private static boolean harvestCycle(ServerPlayerEntity player) {
        HARVEST_CYCLES.add(new HarvestCycle(player.getUuid(), player.getServerWorld(),
                player.getBlockPos(), 0, 8));
        player.sendMessage(Text.literal("Harvest Cycle scheduled every 100 ticks × 8")
                .formatted(Formatting.GREEN), false);
        return true;
    }

    private static boolean redstoneOracle(ServerPlayerEntity player, boolean forceForShowcase) {
        boolean hostile = !hostiles(player, 8.0, 1).isEmpty();
        if (!hostile && !forceForShowcase) {
            player.sendMessage(Text.literal("Redstone Oracle output: 0 (no hostile presence)")
                    .formatted(Formatting.GRAY), false);
            return true;
        }
        ServerWorld world = player.getServerWorld();
        BlockPos base = player.getBlockPos();
        BlockPos target = null;
        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos candidate = base.offset(direction);
            if (world.isAir(candidate)) {
                target = candidate;
                break;
            }
        }
        if (target == null) return noTarget(player, "Redstone Oracle has no open output face");
        world.setBlockState(target, TemporarySpellContent.ORACLE_SIGNAL.getDefaultState());
        world.spawnParticles(ParticleTypes.WITCH,
                target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5,
                24, 0.4, 0.4, 0.4, 0.03);
        return true;
    }

    private static void tick(MinecraftServer server) {
        Iterator<HarvestCycle> harvestIterator = HARVEST_CYCLES.iterator();
        while (harvestIterator.hasNext()) {
            HarvestCycle cycle = harvestIterator.next();
            cycle.ticks++;
            if (cycle.ticks < 100) continue;
            cycle.ticks = 0;
            int harvested = harvestMature(cycle.world, cycle.center);
            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(cycle.owner);
            if (owner != null) {
                owner.sendMessage(Text.literal("Harvest Cycle pulse: " + harvested + " mature crops")
                        .formatted(Formatting.GREEN), true);
            }
            if (--cycle.remaining <= 0) harvestIterator.remove();
        }

    }

    private static int harvestMature(ServerWorld world, BlockPos center) {
        int harvested = 0;
        for (BlockPos pos : BlockPos.iterate(center.add(-6, -2, -6), center.add(6, 2, 6))) {
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof CropBlock crop && crop.isMature(state)
                    && world.breakBlock(pos, true) && harvested++ >= 31) {
                break;
            }
        }
        return harvested;
    }

    private static Optional<BlockHitResult> blockHit(ServerPlayerEntity player, double range) {
        HitResult hit = player.raycast(range, 1.0F, false);
        return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                ? Optional.of(block) : Optional.empty();
    }

    private static boolean isOre(BlockState state) {
        return state.isIn(BlockTags.COAL_ORES)
                || state.isIn(BlockTags.COPPER_ORES)
                || state.isIn(BlockTags.IRON_ORES)
                || state.isIn(BlockTags.GOLD_ORES)
                || state.isIn(BlockTags.REDSTONE_ORES)
                || state.isIn(BlockTags.LAPIS_ORES)
                || state.isIn(BlockTags.EMERALD_ORES)
                || state.isIn(BlockTags.DIAMOND_ORES);
    }

    private static boolean canModify(
            ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state) {
        return world.canPlayerModifyAt(player, pos)
                && PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(
                        world, player, pos, state, world.getBlockEntity(pos));
    }

    private static List<HostileEntity> hostiles(
            ServerPlayerEntity player, double radius, int maximum) {
        Vec3d eye = player.getEyePos();
        return player.getServerWorld().getEntitiesByClass(HostileEntity.class,
                        new Box(player.getPos(), player.getPos()).expand(radius),
                        entity -> entity.isAlive())
                .stream().sorted(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(eye)))
                .limit(maximum).toList();
    }

    private static boolean noTarget(ServerPlayerEntity player, String message) {
        player.sendMessage(Text.literal(message).formatted(Formatting.YELLOW), true);
        return false;
    }

    private static void ring(
            ServerWorld world, Vec3d center, double radius, ParticleEffect particle, int points) {
        for (int index = 0; index < points; index++) {
            double angle = Math.PI * 2.0 * index / points;
            world.spawnParticles(particle, center.x + Math.cos(angle) * radius,
                    center.y, center.z + Math.sin(angle) * radius,
                    1, 0.01, 0.01, 0.01, 0.0);
        }
    }

    private static void drawBeam(
            ServerWorld world, Vec3d start, double targetY, Vec3d target, ParticleEffect particle) {
        drawBeam(world, start.x, start.y, start.z, targetY, target, particle);
    }

    private static void drawBeam(
            ServerWorld world, double startX, double startY, double startZ,
            double targetY, Vec3d target, ParticleEffect particle) {
        Vec3d start = new Vec3d(startX, startY, startZ);
        Vec3d end = new Vec3d(target.x, targetY, target.z);
        for (int step = 0; step <= 20; step++) {
            Vec3d point = start.lerp(end, step / 20.0);
            world.spawnParticles(particle, point.x, point.y, point.z,
                    1, 0.01, 0.01, 0.01, 0.0);
        }
    }

    private static final class HarvestCycle {
        private final java.util.UUID owner;
        private final ServerWorld world;
        private final BlockPos center;
        private int ticks;
        private int remaining;

        private HarvestCycle(
                java.util.UUID owner, ServerWorld world, BlockPos center, int ticks, int remaining) {
            this.owner = owner;
            this.world = world;
            this.center = center.toImmutable();
            this.ticks = ticks;
            this.remaining = remaining;
        }
    }

}
