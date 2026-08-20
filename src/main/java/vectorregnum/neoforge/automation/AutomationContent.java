package vectorregnum.neoforge.automation;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import vectorregnum.neoforge.VectorRegnumMod;

/** Registry boundary for the programmable relay and its persistent state. */
public final class AutomationContent {
    public static final Block AUTOMATION_RELAY = Registry.register(Registries.BLOCK,
            Identifier.of(VectorRegnumMod.MOD_ID, "automation_relay"),
            new AutomationRelayBlock(AbstractBlock.Settings.copy(Blocks.CHISELED_COPPER)
                    .strength(4.0F, 8.0F).luminance(state -> 3)));

    public static final Item AUTOMATION_RELAY_ITEM = Registry.register(Registries.ITEM,
            Identifier.of(VectorRegnumMod.MOD_ID, "automation_relay"),
            new BlockItem(AUTOMATION_RELAY,
                    new Item.Settings().maxCount(16).rarity(Rarity.RARE)));

    public static final BlockEntityType<AutomationRelayBlockEntity> AUTOMATION_RELAY_ENTITY =
            Registry.register(Registries.BLOCK_ENTITY_TYPE,
                    Identifier.of(VectorRegnumMod.MOD_ID, "automation_relay"),
                    FabricBlockEntityTypeBuilder.create(AutomationRelayBlockEntity::new,
                            AUTOMATION_RELAY).build());

    private AutomationContent() {
    }

    public static void initialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.REDSTONE)
                .register(entries -> entries.add(AUTOMATION_RELAY_ITEM));
    }
}
