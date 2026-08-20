package vectorregnum.neoforge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import vectorregnum.neoforge.progression.ProgressionSync;

/** Common game-bus integration that preserves the Fabric alpha's item behavior. */
public final class VectorRegnumGameplayEvents {
    private VectorRegnumGameplayEvents() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ProgressionSync.send(player);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        if (stack.is(VectorRegnumContent.SIGIL_TOME.get())) {
            if (event.getEntity().getCooldowns().isOnCooldown(stack.getItem())) {
                finish(event, InteractionResult.FAIL);
                return;
            }
            if (!event.getLevel().isClientSide && event.getEntity() instanceof ServerPlayer player) {
                CastService.cast(player, SpellPresets.FIREBOLT, true);
                player.getCooldowns().addCooldown(stack.getItem(), 20);
            }
            finish(event, InteractionResult.sidedSuccess(event.getLevel().isClientSide));
            return;
        }

        InteractionResultHolder<ItemStack> artifact = CircleAuthoringService.useHandheldArtifact(
                event.getEntity(), event.getLevel(), event.getHand());
        if (artifact.getResult() != InteractionResult.PASS) {
            finish(event, artifact.getResult());
        }
    }

    private static void finish(PlayerInteractEvent.RightClickItem event, InteractionResult result) {
        event.setCancellationResult(result);
        event.setCanceled(true);
    }
}
