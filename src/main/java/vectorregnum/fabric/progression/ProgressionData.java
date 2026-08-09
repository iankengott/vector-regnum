package vectorregnum.fabric.progression;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/** Persistent, server-authoritative progression discoveries. */
public final class ProgressionData {
    private static final Codec<ProgressionState> CODEC = Codec.STRING.listOf().xmap(
            ProgressionState::fromIds,
            ProgressionState::ids);

    private static final AttachmentType<ProgressionState> STATE = AttachmentRegistry.create(
            Identifier.of("vector_regnum", "progression_unlocks"),
            builder -> builder.initializer(() -> ProgressionState.EMPTY).persistent(CODEC).copyOnDeath());

    private ProgressionData() {
    }

    public static void initialize() {
        // Loading the class registers the attachment type.
    }

    public static ProgressionState get(ServerPlayerEntity player) {
        return player.getAttachedOrCreate(STATE);
    }

    public static boolean unlock(ServerPlayerEntity player, ProgressionUnlock unlock) {
        ProgressionState current = get(player);
        if (current.has(unlock)) {
            return false;
        }
        player.setAttached(STATE, current.unlock(unlock));
        player.sendMessage(Text.translatable("message.vector_regnum.progression_unlocked",
                Text.translatable("unlock.vector_regnum." + unlock.id())).formatted(Formatting.AQUA), false);
        return true;
    }

    public static int unlockAll(ServerPlayerEntity player) {
        int changed = 0;
        for (ProgressionUnlock unlock : ProgressionUnlock.values()) {
            if (unlock(player, unlock)) {
                changed++;
            }
        }
        return changed;
    }
}
