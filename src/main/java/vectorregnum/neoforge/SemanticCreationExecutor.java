package vectorregnum.neoforge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import vectorregnum.core.semantic.CreationForm;
import vectorregnum.core.semantic.CreationMaterial;
import vectorregnum.core.semantic.CreationSpec;
import vectorregnum.core.semantic.SemanticInstruction;
import vectorregnum.core.semantic.SemanticOpcode;
import vectorregnum.neoforge.multiplayer.SpellSecurityPolicy;

/** Server-authoritative bounded material/form placement for executable CREATE_FORM. */
public final class SemanticCreationExecutor {
    private static final int MAX_AXIS = 8;
    private static final double MAX_CAST_RANGE = 32.0;

    private SemanticCreationExecutor() { }

    public static void execute(ServerPlayer player, List<SemanticInstruction> steps) {
        for (SemanticInstruction instruction : steps) {
            if (instruction.opcode() == SemanticOpcode.CREATE_FORM) {
                create(player, instruction.creationSpec());
            }
        }
    }

    static int create(ServerPlayer player, CreationSpec spec) {
        ServerLevel world = player.serverLevel();
        BlockPos anchor = anchor(player);
        int requested = Math.min((int) Math.ceil(spec.volume()),
                (int) Math.floor(spec.material().maximumVolume()));
        List<BlockPos> positions = positions(anchor, player.getViewVector(1.0F), spec.form(), requested);
        BlockState state = (spec.permanent() ? Blocks.STONE
                : TemporarySpellContent.createdForm(spec.material())).defaultBlockState();
        int placed = 0;
        for (BlockPos pos : positions) {
            if (pos.distToCenterSqr(player.position()) > MAX_CAST_RANGE * MAX_CAST_RANGE) continue;
            BlockState old = world.getBlockState(pos);
            if (!old.canBeReplaced() || !SpellSecurityPolicy.canModifyBlock(player, pos, old)) continue;
            if (world.setBlock(pos, state, Block.UPDATE_ALL)) {
                placed++;
                if (!spec.permanent()) {
                    world.scheduleTick(pos, state.getBlock(), spec.durationTicks());
                }
            }
        }
        return placed;
    }

    private static BlockPos anchor(ServerPlayer player) {
        HitResult hit = player.pick(MAX_CAST_RANGE, 1.0F, false);
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            return blockHit.getBlockPos().relative(blockHit.getDirection());
        }
        Vec3 forward = player.getViewVector(1.0F).normalize().scale(3.0);
        return BlockPos.containing(player.getEyePosition().add(forward));
    }

    static List<BlockPos> positions(BlockPos anchor, Vec3 look,
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
                    if (include) candidates.add(anchor.offset(x, y, z));
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
