package vectorregnum.fabric.progression;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

/** Registers progression content without touching the mod's shared registry class. */
public final class ProgressionContent {
    public static final Item MANA_CRYSTAL_SHARD = Registry.register(
            Registries.ITEM,
            Identifier.of("vector_regnum", "mana_crystal_shard"),
            new Item(new Item.Settings().rarity(Rarity.UNCOMMON)));

    public static final Block MANA_CRYSTAL_NODE = Registry.register(
            Registries.BLOCK,
            Identifier.of("vector_regnum", "mana_crystal_node"),
            new ManaCrystalNodeBlock(AbstractBlock.Settings.copy(Blocks.AMETHYST_BLOCK)
                    .strength(-1.0f, 1200.0f)
                    .luminance(state -> 5 + state.get(ManaCrystalNodeBlock.CHARGE))));

    public static final Item MANA_CRYSTAL_NODE_ITEM = Registry.register(
            Registries.ITEM,
            Identifier.of("vector_regnum", "mana_crystal_node"),
            new BlockItem(MANA_CRYSTAL_NODE, new Item.Settings().maxCount(1).rarity(Rarity.RARE)));

    private static PlayerManaBridge manaBridge = PlayerManaBridge.DISCONNECTED;

    private ProgressionContent() {
    }

    public static void initialize(PlayerManaBridge bridge) {
        manaBridge = bridge == null ? PlayerManaBridge.DISCONNECTED : bridge;
        ProgressionData.initialize();
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS)
                .register(entries -> entries.add(MANA_CRYSTAL_SHARD));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL)
                .register(entries -> entries.add(MANA_CRYSTAL_NODE_ITEM));
    }

    public static void initialize() {
        initialize(PlayerManaBridge.DISCONNECTED);
    }

    static PlayerManaBridge manaBridge() {
        return manaBridge;
    }
}
