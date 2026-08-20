package vectorregnum.neoforge.progression;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;
import vectorregnum.neoforge.PlayerAttachmentContent;

/** Persistent, server-authoritative progression discoveries. */
public final class ProgressionData {
    private ProgressionData() {
    }

    public static void initialize() {
        // Registration is owned by vectorregnum.neoforge.PlayerAttachmentContent.
    }

    public static ProgressionState get(ServerPlayer player) {
        return player.getData(PlayerAttachmentContent.PROGRESSION_UNLOCKS);
    }

    public static boolean unlock(ServerPlayer player, ProgressionUnlock unlock) {
        ProgressionState current = get(player);
        if (current.has(unlock)) {
            return false;
        }
        player.setData(PlayerAttachmentContent.PROGRESSION_UNLOCKS, current.unlock(unlock));
        ProgressionSync.send(player);
        player.sendSystemMessage(Component.translatable("message.vector_regnum.progression_unlocked",
                Component.translatable("unlock.vector_regnum." + unlock.id()))
                .withStyle(ChatFormatting.AQUA));
        return true;
    }

    public static int unlockAll(ServerPlayer player) {
        int changed = 0;
        for (ProgressionUnlock unlock : ProgressionUnlock.values()) {
            if (unlock(player, unlock)) {
                changed++;
            }
        }
        ProgressionSync.send(player);
        return changed;
    }
}
