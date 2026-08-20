package vectorregnum.neoforge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import vectorregnum.core.semantic.SemanticInstruction;
import vectorregnum.core.semantic.SemanticOpcode;
import vectorregnum.core.semantic.SemanticSchema;
import vectorregnum.neoforge.multiplayer.SpellSecurityPolicy;

/** Opcode-driven server adapter for every curated semantic operation. */
public final class SemanticSpellExecutor {
    private SemanticSpellExecutor() { }

    public static java.util.Set<SemanticOpcode> supportedOpcodes() {
        return java.util.Set.copyOf(java.util.EnumSet.allOf(SemanticOpcode.class));
    }

    /** Reports bounded action multiplicity; REPEAT_BOUNDED repeats the preceding action to count total runs. */
    public static java.util.Map<Integer, Integer> actionMultiplicities(List<SemanticInstruction> steps) {
        java.util.Map<Integer, Integer> result = new java.util.LinkedHashMap<>();
        Integer previousAction = null;
        for (int index = 0; index < steps.size(); index++) {
            SemanticInstruction step = steps.get(index);
            if (isAction(step.opcode())) {
                result.put(index, 1);
                previousAction = index;
            } else if (step.opcode() == SemanticOpcode.REPEAT_BOUNDED && previousAction != null) {
                result.put(previousAction, SemanticSchema.integer(step.operands(), "count"));
            }
        }
        return java.util.Map.copyOf(result);
    }

    private static boolean isAction(SemanticOpcode opcode) {
        return vectorregnum.core.semantic.SemanticCostModel.isRepeatableAction(opcode);
    }

    public static boolean preflight(ServerPlayerEntity player, List<SemanticInstruction> steps) {
        boolean needsBlock = has(steps, SemanticOpcode.PLACE_LIGHT)
                || has(steps, SemanticOpcode.BREAK_BLOCKS)
                || has(steps, SemanticOpcode.TRANSMUTE_BLOCK);
        if (needsBlock && blockHit(player, 32).isEmpty()) {
            player.sendMessage(Text.literal("Spell needs a visible block target")
                    .formatted(Formatting.YELLOW), true);
            return false;
        }
        boolean needsHostile = has(steps, SemanticOpcode.FILTER_HOSTILE)
                && (has(steps, SemanticOpcode.APPLY_DAMAGE)
                    || has(steps, SemanticOpcode.APPLY_IMPULSE));
        if (needsHostile) {
            double radius = operand(steps, SemanticOpcode.SELECT_NEARBY_ENTITIES, "radius", 24);
            if (hostiles(player, radius, 1).isEmpty()) {
                player.sendMessage(Text.literal("Spell found no hostile target")
                        .formatted(Formatting.YELLOW), true);
                return false;
            }
        }
        if (has(steps, SemanticOpcode.SHAPE_PROJECTILE)
                && has(steps, SemanticOpcode.APPLY_DAMAGE)
                && !has(steps, SemanticOpcode.SELECT_NEARBY_ENTITIES)
                && entityHit(player, 32).isEmpty()) {
            player.sendMessage(Text.literal("Spell needs a visible entity target")
                    .formatted(Formatting.YELLOW), true);
            return false;
        }
        return true;
    }

    public static void execute(ServerPlayerEntity player,
            List<SemanticInstruction> steps, boolean force) {
        State state = new State(player, force);
        for (SemanticInstruction instruction : steps) state.accept(instruction);
    }

    private static final class State {
        private final ServerPlayerEntity player;
        private final ServerWorld world;
        private final boolean force;
        private double radius = 8, magnitude = 1;
        private int duration = 1, repeat = 128;
        private String element = "none", shape = "aura", filter = "any";
        private BlockHitResult block;
        private Runnable repeatableAction;

        private State(ServerPlayerEntity player, boolean force) {
            this.player = player; this.world = player.getServerWorld(); this.force = force;
        }

        private void accept(SemanticInstruction step) {
            switch (step.opcode()) {
                case ORIGIN_SELF, ORIGIN_TARGET, LOOK_VECTOR, RAYCAST_ENTITY, WAIT_TICKS,
                        EXECUTE -> { }
                case RAYCAST_BLOCK -> block = blockHit(player, 32).orElse(null);
                case SELECT_NEARBY_ENTITIES -> radius = SemanticSchema.number(step.operands(), "radius");
                case FILTER_HOSTILE -> filter = "hostile";
                case FILTER_LIVING -> filter = "living";
                case FILTER_ORE -> filter = "ore";
                case ELEMENT_FIRE -> element = "fire";
                case ELEMENT_FROST -> element = "frost";
                case ELEMENT_ARCANE -> element = "arcane";
                case ELEMENT_VOID -> element = "void";
                case SHAPE_PROJECTILE -> shape = "projectile";
                case SHAPE_AURA -> shape = "aura";
                case SHAPE_BARRIER -> shape = "barrier";
                case SET_RADIUS -> radius = SemanticSchema.number(step.operands(), "blocks");
                case SET_MAGNITUDE -> magnitude = SemanticSchema.number(step.operands(), "power");
                case SET_DURATION -> duration = SemanticSchema.integer(step.operands(), "ticks");
                case REPEAT_BOUNDED -> {
                    repeat = SemanticSchema.integer(step.operands(), "count");
                    if (repeatableAction != null) for (int pass = 1; pass < repeat; pass++) repeatableAction.run();
                }
                case APPLY_DAMAGE -> runRepeatable(this::damage);
                case APPLY_IMPULSE -> runRepeatable(() -> impulse(SemanticSchema.text(step.operands(),
                        step.operands().keySet().iterator().next())));
                case APPLY_SLOW -> runRepeatable(() -> livingTargets().forEach(entity -> entity.addStatusEffect(
                        new StatusEffectInstance(StatusEffects.SLOWNESS, Math.max(duration, 160), 2))));
                case APPLY_FEATHERFALL -> runRepeatable(() -> player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.SLOW_FALLING, duration, 0)));
                case PLACE_LIGHT -> runRepeatable(this::placeLight);
                case BREAK_BLOCKS -> runRepeatable(() -> breakBlocks(SemanticSchema.text(step.operands(), "mode")));
                case TRANSMUTE_BLOCK -> runRepeatable(() -> transmute(SemanticSchema.text(step.operands(), "into")));
                case CREATE_FORM -> runRepeatable(() -> SemanticCreationExecutor.create(player, step.creationSpec()));
                case EMIT_PARTICLES -> runRepeatable(() -> particles(SemanticSchema.text(step.operands(), "style")));
                case EMIT_REDSTONE -> runRepeatable(() -> redstone(SemanticSchema.integer(step.operands(), "strength")));
            }
            if (step.opcode() == SemanticOpcode.EXECUTE && shape.equals("barrier")) barrier();
        }

        private void runRepeatable(Runnable action) {
            repeatableAction = action;
            action.run();
        }

        private List<LivingEntity> livingTargets() {
            if (filter.equals("hostile")) return new ArrayList<>(hostiles(player, radius, repeat));
            return world.getEntitiesByClass(LivingEntity.class,
                    player.getBoundingBox().expand(radius), entity -> entity != player && entity.isAlive())
                    .stream().sorted(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(player)))
                    .limit(Math.min(128, Math.max(repeat, 64))).toList();
        }

        private void damage() {
            List<? extends LivingEntity> targets;
            if (shape.equals("projectile") && filter.equals("any")) {
                targets = entityHit(player, 32).map(EntityHitResult::getEntity)
                        .filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast)
                        .map(List::of).orElseGet(List::of);
            } else targets = livingTargets();
            double elemental = element.equals("fire") ? 2 : element.equals("frost") ? 3
                    : element.equals("arcane") ? 4 : element.equals("void") ? 5 : 0;
            float damage = (float) Math.clamp(magnitude * 3.0 + elemental, 1, 20);
            targets.stream().limit(repeat)
                    .filter(target -> SpellSecurityPolicy.canAffectEntity(player, target))
                    .forEach(target -> target.damage(world.getDamageSources().magic(), damage));
        }

        private void impulse(String direction) {
            if (direction.equals("caster")) {
                NeoForgeVmService.launchVectorStep(player, false, 0, magnitude);
                return;
            }
            for (LivingEntity target : livingTargets().stream().limit(repeat).toList()) {
                Vec3d vector = direction.equals("down") ? new Vec3d(0, -magnitude, 0)
                        : target.getPos().subtract(player.getPos()).normalize().multiply(magnitude).add(0, .2, 0);
                NeoForgeVmService.launchKineticWard(player, target, vector, false);
            }
        }

        private void barrier() {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, duration, 1));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, duration, 1));
            player.playSound(SoundEvents.ITEM_SHIELD_BLOCK, .8F, 1.3F);
        }

        private void placeLight() {
            if (block == null) return;
            BlockPos pos = block.getBlockPos().offset(block.getSide());
            if (world.isAir(pos) && SpellSecurityPolicy.canModifyBlock(player, pos,
                    world.getBlockState(pos))) {
                world.setBlockState(pos, TemporarySpellContent.MAGE_LIGHT.getDefaultState());
                world.scheduleBlockTick(pos, TemporarySpellContent.MAGE_LIGHT, duration);
            }
        }

        private void breakBlocks(String mode) {
            if (mode.equals("mature_crops")) {
                int broken = 0;
                for (BlockPos pos : BlockPos.iterate(player.getBlockPos().add(-(int) radius, -2, -(int) radius),
                        player.getBlockPos().add((int) radius, 2, (int) radius))) {
                    BlockState state = world.getBlockState(pos);
                    if (state.getBlock() instanceof CropBlock crop && crop.isMature(state)
                            && canModify(pos, state) && world.breakBlock(pos, true, player) && ++broken >= 32) break;
                }
                return;
            }
            if (block == null) return;
            BlockPos center = block.getBlockPos(); int maximum = Math.min(64, (int) Math.pow(radius + 1, 3));
            int broken = 0; int r = Math.min(3, (int) Math.ceil(radius / 2));
            for (BlockPos pos : BlockPos.iterate(center.add(-r, -r, -r), center.add(r, r, r))) {
                BlockState state = world.getBlockState(pos);
                if (!state.isAir() && state.getHardness(world, pos) >= 0 && world.getBlockEntity(pos) == null
                        && canModify(pos, state) && world.breakBlock(pos, true, player) && ++broken >= maximum) break;
            }
        }

        private void transmute(String into) {
            if (block == null || !into.equals("minecraft:stone")) return;
            BlockPos pos = block.getBlockPos(); BlockState old = world.getBlockState(pos);
            if (!old.isAir() && old.getHardness(world, pos) >= 0 && world.getBlockEntity(pos) == null
                    && canModify(pos, old)) world.setBlockState(pos, Blocks.STONE.getDefaultState());
        }

        private void particles(String style) {
            if (style.equals("outline")) {
                livingTargets().forEach(entity -> entity.addStatusEffect(
                        new StatusEffectInstance(StatusEffects.GLOWING, Math.max(duration, 200), 0)));
            } else if (style.equals("vein_trace")) {
                int found = 0; BlockPos center = player.getBlockPos(); int r = Math.min(12, (int) radius);
                for (BlockPos pos : BlockPos.iterate(center.add(-r, -r, -r), center.add(r, r, r))) {
                    if (isOre(world.getBlockState(pos))) {
                        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + .5,
                                pos.getY() + .5, pos.getZ() + .5, 8, .2, .2, .2, .02);
                        if (++found >= 32) break;
                    }
                }
            }
        }

        private void redstone(int strength) {
            if (strength == 0 || (!force && hostiles(player, radius, 1).isEmpty())) return;
            for (var direction : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
                BlockPos pos = player.getBlockPos().offset(direction);
                if (world.isAir(pos) && SpellSecurityPolicy.canModifyBlock(player, pos,
                        world.getBlockState(pos))) {
                    world.setBlockState(pos, TemporarySpellContent.ORACLE_SIGNAL.getDefaultState());
                    world.scheduleBlockTick(pos, TemporarySpellContent.ORACLE_SIGNAL,
                            Math.max(OracleSignalBlock.LIFETIME_TICKS, duration));
                    return;
                }
            }
        }

        private boolean canModify(BlockPos pos, BlockState state) {
            return SpellSecurityPolicy.canModifyBlock(player, pos, state);
        }
    }

    private static boolean has(List<SemanticInstruction> steps, SemanticOpcode opcode) {
        return steps.stream().anyMatch(step -> step.opcode() == opcode);
    }
    private static double operand(List<SemanticInstruction> steps, SemanticOpcode opcode,
            String key, double fallback) {
        return steps.stream().filter(step -> step.opcode() == opcode).findFirst()
                .map(step -> SemanticSchema.number(step.operands(), key)).orElse(fallback);
    }
    private static java.util.Optional<BlockHitResult> blockHit(ServerPlayerEntity player, double range) {
        HitResult hit = player.raycast(range, 1, false);
        return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                ? java.util.Optional.of(block) : java.util.Optional.empty();
    }
    private static java.util.Optional<EntityHitResult> entityHit(ServerPlayerEntity player, double range) {
        Vec3d start = player.getEyePos(), end = start.add(player.getRotationVec(1).multiply(range));
        EntityHitResult hit = net.minecraft.entity.projectile.ProjectileUtil.raycast(player, start, end,
                new Box(start, end).expand(1), entity -> entity instanceof LivingEntity && entity.isAlive(), range * range);
        return java.util.Optional.ofNullable(hit);
    }
    private static List<HostileEntity> hostiles(ServerPlayerEntity player, double radius, int maximum) {
        return player.getServerWorld().getEntitiesByClass(HostileEntity.class,
                player.getBoundingBox().expand(radius), Entity::isAlive).stream()
                .sorted(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(player)))
                .limit(maximum).toList();
    }
    private static boolean isOre(BlockState state) {
        return state.isIn(BlockTags.COAL_ORES) || state.isIn(BlockTags.COPPER_ORES)
                || state.isIn(BlockTags.IRON_ORES) || state.isIn(BlockTags.GOLD_ORES)
                || state.isIn(BlockTags.REDSTONE_ORES) || state.isIn(BlockTags.LAPIS_ORES)
                || state.isIn(BlockTags.EMERALD_ORES) || state.isIn(BlockTags.DIAMOND_ORES);
    }
}
