package vectorregnum.fabric;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import vectorregnum.core.semantic.CreationForm;
import vectorregnum.core.semantic.CreationMaterial;
import vectorregnum.core.semantic.CreationSpec;
import vectorregnum.core.semantic.SemanticInstruction;
import vectorregnum.core.semantic.SemanticOpcode;
import vectorregnum.fabric.multiplayer.SpellSecurityPolicy;

/** Server-authoritative bounded material/form placement for executable CREATE_FORM. */
public final class SemanticCreationExecutor {
    private static final int MAX_AXIS = 8;
    private static final double MAX_CAST_RANGE = 32.0;

    private SemanticCreationExecutor() { }

    public static void execute(ServerPlayerEntity player, List<SemanticInstruction> steps) {
        for (SemanticInstruction instruction : steps) {
            if (instruction.opcode() == SemanticOpcode.CREATE_FORM) {
                create(player, instruction.creationSpec());
            }
        }
    }

    static int create(ServerPlayerEntity player, CreationSpec spec) {
        ServerWorld world = player.getServerWorld();
        BlockPos anchor = anchor(player);
        int requested = Math.min((int) Math.ceil(spec.volume()),
                (int) Math.floor(spec.material().maximumVolume()));
        List<BlockPos> positions = positions(anchor, player.getRotationVec(1.0F), spec.form(), requested);
        BlockState state = (spec.permanent() ? Blocks.STONE
                : TemporarySpellContent.createdForm(spec.material())).getDefaultState();
        int placed = 0;
        for (BlockPos pos : positions) {
            if (pos.getSquaredDistance(player.getPos()) > MAX_CAST_RANGE * MAX_CAST_RANGE) continue;
            BlockState old = world.getBlockState(pos);
            if (!old.isReplaceable() || !SpellSecurityPolicy.canModifyBlock(player, pos, old)) continue;
            if (world.setBlockState(pos, state, Block.NOTIFY_ALL)) {
                placed++;
                if (!spec.permanent()) {
                    world.scheduleBlockTick(pos, state.getBlock(), spec.durationTicks());
                }
            }
        }
        return placed;
    }

    private static BlockPos anchor(ServerPlayerEntity player) {
        HitResult hit = player.raycast(MAX_CAST_RANGE, 1.0F, false);
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            return blockHit.getBlockPos().offset(blockHit.getSide());
        }
        Vec3d forward = player.getRotationVec(1.0F).normalize().multiply(3.0);
        return BlockPos.ofFloored(player.getEyePos().add(forward));
    }

    static List<BlockPos> positions(BlockPos anchor, Vec3d look,
            CreationForm form, int count) {
        List<BlockPos> candidates = new ArrayList<>();
        int radius = Math.min(MAX_AXIS, Math.max(1, (int) Math.ceil(Math.cbrt(count)) + 1));
        boolean xFacing = Math.abs(look.x) >= Math.abs(look.z);
        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    boolean include = switch (form) {
                        case PROJECTILE -> y == 0 && z == 0 && x >= 0;
                        case FIELD -> y == 0 && x * x + z * z <= radius * radius;
                        case BARRIER -> xFacing ? x == 0 : z == 0;
                        case CONSTRUCT -> Math.abs(x) + Math.abs(y) + Math.abs(z) <= radius;
                        case SURFACE -> y == 0;
                        case VOLUME -> true;
                    };
                    if (include) candidates.add(anchor.add(x, y, z));
                }
            }
        }
        candidates.sort(Comparator.<BlockPos>comparingInt(pos -> Math.abs(pos.getX() - anchor.getX())
                + Math.abs(pos.getY() - anchor.getY()) + Math.abs(pos.getZ() - anchor.getZ()))
                .thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ));
        return List.copyOf(candidates.subList(0, Math.min(count, candidates.size())));
    }
}
