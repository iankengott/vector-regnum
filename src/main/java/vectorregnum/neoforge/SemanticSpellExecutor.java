package vectorregnum.neoforge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
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

    public static boolean preflight(ServerPlayer player, List<SemanticInstruction> steps) {
        boolean needsBlock = has(steps, SemanticOpcode.PLACE_LIGHT)
                || has(steps, SemanticOpcode.BREAK_BLOCKS)
                || has(steps, SemanticOpcode.TRANSMUTE_BLOCK);
        if (needsBlock && blockHit(player, 32).isEmpty()) {
            player.sendSystemMessage(Component.literal("Spell needs a visible block target")
                    .withStyle(ChatFormatting.YELLOW), true);
            return false;
        }
        boolean needsHostile = has(steps, SemanticOpcode.FILTER_HOSTILE)
                && (has(steps, SemanticOpcode.APPLY_DAMAGE)
                    || has(steps, SemanticOpcode.APPLY_IMPULSE));
        if (needsHostile) {
            double radius = operand(steps, SemanticOpcode.SELECT_NEARBY_ENTITIES, "radius", 24);
            if (hostiles(player, radius, 1).isEmpty()) {
                player.sendSystemMessage(Component.literal("Spell found no hostile target")
                        .withStyle(ChatFormatting.YELLOW), true);
                return false;
            }
        }
        if (has(steps, SemanticOpcode.SHAPE_PROJECTILE)
                && has(steps, SemanticOpcode.APPLY_DAMAGE)
                && !has(steps, SemanticOpcode.SELECT_NEARBY_ENTITIES)
                && entityHit(player, 32).isEmpty()) {
            player.sendSystemMessage(Component.literal("Spell needs a visible entity target")
                    .withStyle(ChatFormatting.YELLOW), true);
            return false;
        }
        return true;
    }

    public static void execute(ServerPlayer player,
            List<SemanticInstruction> steps, boolean force) {
        State state = new State(player, force);
        for (SemanticInstruction instruction : steps) state.accept(instruction);
    }

    private static final class State {
        private final ServerPlayer player;
        private final ServerLevel world;
        private final boolean force;
        private double radius = 8, magnitude = 1;
        private int duration = 1, repeat = 128;
        private String element = "none", shape = "aura", filter = "any";
        private BlockHitResult block;
        private Runnable repeatableAction;

        private State(ServerPlayer player, boolean force) {
            this.player = player; this.world = player.serverLevel(); this.force = force;
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
                case APPLY_SLOW -> runRepeatable(() -> livingTargets().forEach(entity -> entity.addEffect(
                        new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, Math.max(duration, 160), 2))));
                case APPLY_FEATHERFALL -> runRepeatable(() -> player.addEffect(new MobEffectInstance(
                        MobEffects.SLOW_FALLING, duration, 0)));
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
            return world.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(radius), entity -> entity != player && entity.isAlive())
                    .stream().sorted(Comparator.comparingDouble(entity -> entity.distanceToSqr(player)))
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
                    .forEach(target -> target.hurt(world.damageSources().magic(), damage));
        }

        private void impulse(String direction) {
            if (direction.equals("caster")) {
                NeoForgeVmService.launchVectorStep(player, false, 0, magnitude);
                return;
            }
            for (LivingEntity target : livingTargets().stream().limit(repeat).toList()) {
                Vec3 vector = direction.equals("down") ? new Vec3(0, -magnitude, 0)
                        : target.position().subtract(player.position()).normalize().scale(magnitude).add(0, .2, 0);
                NeoForgeVmService.launchKineticWard(player, target, vector, false);
            }
        }

        private void barrier() {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, duration, 1));
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, duration, 1));
            player.playSound(SoundEvents.ITEM_SHIELD_BLOCK, .8F, 1.3F);
        }

        private void placeLight() {
            if (block == null) return;
            BlockPos pos = block.getBlockPos().relative(block.getDirection());
            if (world.isEmptyBlock(pos) && SpellSecurityPolicy.canModifyBlock(player, pos,
                    world.getBlockState(pos))) {
                world.setBlock(pos, TemporarySpellContent.mageLight().defaultBlockState(), Block.UPDATE_ALL);
                world.scheduleTick(pos, TemporarySpellContent.mageLight(), duration);
            }
        }

        private void breakBlocks(String mode) {
            if (mode.equals("mature_crops")) {
                int broken = 0;
                for (BlockPos pos : BlockPos.betweenClosed(player.blockPosition().offset(-(int) radius, -2, -(int) radius),
                        player.blockPosition().offset((int) radius, 2, (int) radius))) {
                    BlockState state = world.getBlockState(pos);
                    if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)
                            && canModify(pos, state) && world.destroyBlock(pos, true, player) && ++broken >= 32) break;
                }
                return;
            }
            if (block == null) return;
            BlockPos center = block.getBlockPos(); int maximum = Math.min(64, (int) Math.pow(radius + 1, 3));
            int broken = 0; int r = Math.min(3, (int) Math.ceil(radius / 2));
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
                BlockState state = world.getBlockState(pos);
                if (!state.isAir() && state.getDestroySpeed(world, pos) >= 0 && world.getBlockEntity(pos) == null
                        && canModify(pos, state) && world.destroyBlock(pos, true, player) && ++broken >= maximum) break;
            }
        }

        private void transmute(String into) {
            if (block == null || !into.equals("minecraft:stone")) return;
            BlockPos pos = block.getBlockPos(); BlockState old = world.getBlockState(pos);
            if (!old.isAir() && old.getDestroySpeed(world, pos) >= 0 && world.getBlockEntity(pos) == null
                    && canModify(pos, old)) world.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        }

        private void particles(String style) {
            if (style.equals("outline")) {
                livingTargets().forEach(entity -> entity.addEffect(
                        new MobEffectInstance(MobEffects.GLOWING, Math.max(duration, 200), 0)));
            } else if (style.equals("vein_trace")) {
                int found = 0; BlockPos center = player.blockPosition(); int r = Math.min(12, (int) radius);
                for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
                    if (isOre(world.getBlockState(pos))) {
                        world.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + .5,
                                pos.getY() + .5, pos.getZ() + .5, 8, .2, .2, .2, .02);
                        if (++found >= 32) break;
                    }
                }
            }
        }

        private void redstone(int strength) {
            if (strength == 0 || (!force && hostiles(player, radius, 1).isEmpty())) return;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos pos = player.blockPosition().relative(direction);
                if (world.isEmptyBlock(pos) && SpellSecurityPolicy.canModifyBlock(player, pos,
                        world.getBlockState(pos))) {
                    world.setBlock(pos, TemporarySpellContent.oracleSignal().defaultBlockState(), Block.UPDATE_ALL);
                    world.scheduleTick(pos, TemporarySpellContent.oracleSignal(),
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
    private static java.util.Optional<BlockHitResult> blockHit(ServerPlayer player, double range) {
        HitResult hit = player.pick(range, 1, false);
        return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                ? java.util.Optional.of(block) : java.util.Optional.empty();
    }
    private static java.util.Optional<EntityHitResult> entityHit(ServerPlayer player, double range) {
        Vec3 start = player.getEyePosition(), end = start.add(player.getViewVector(1).scale(range));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, start, end,
                new AABB(start, end).inflate(1),
                entity -> entity instanceof LivingEntity && entity.isAlive(), range * range);
        return java.util.Optional.ofNullable(hit);
    }
    private static List<Monster> hostiles(ServerPlayer player, double radius, int maximum) {
        return player.serverLevel().getEntitiesOfClass(Monster.class,
                player.getBoundingBox().inflate(radius), Entity::isAlive).stream()
                .sorted(Comparator.comparingDouble(entity -> entity.distanceToSqr(player)))
                .limit(maximum).toList();
    }
    private static boolean isOre(BlockState state) {
        return state.is(BlockTags.COAL_ORES) || state.is(BlockTags.COPPER_ORES)
                || state.is(BlockTags.IRON_ORES) || state.is(BlockTags.GOLD_ORES)
                || state.is(BlockTags.REDSTONE_ORES) || state.is(BlockTags.LAPIS_ORES)
                || state.is(BlockTags.EMERALD_ORES) || state.is(BlockTags.DIAMOND_ORES);
    }
}
