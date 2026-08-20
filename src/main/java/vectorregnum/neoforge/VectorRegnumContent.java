package vectorregnum.neoforge;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.minecraft.util.TypedActionResult;

public final class VectorRegnumContent {
    public static final Item SIGIL_TOME = Registry.register(
            Registries.ITEM,
            Identifier.of(VectorRegnumMod.MOD_ID, "sigil_tome"),
            new Item(new Item.Settings().maxCount(1).rarity(Rarity.RARE)));

    private VectorRegnumContent() {
    }

    public static void initialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS)
                .register(entries -> entries.add(SIGIL_TOME));

        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!stack.isOf(SIGIL_TOME)) {
                return TypedActionResult.pass(stack);
            }
            if (player.getItemCooldownManager().isCoolingDown(SIGIL_TOME)) {
                return TypedActionResult.fail(stack);
            }
            if (!world.isClient() && player instanceof ServerPlayerEntity serverPlayer) {
                CastService.cast(serverPlayer, SpellPresets.FIREBOLT, true);
                serverPlayer.getItemCooldownManager().set(SIGIL_TOME, 20);
            }
            return TypedActionResult.success(stack, world.isClient());
        });
    }
}
