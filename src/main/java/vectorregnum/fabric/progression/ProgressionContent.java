package vectorregnum.fabric.progression;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
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

    public static final Block RAW_CRYSTAL_CONDUIT = registerConduit("raw_crystal_conduit",
            Blocks.COPPER_BLOCK, 2, ManaTransportRules.ConduitTier.RAW_CRYSTAL);
    public static final Item RAW_CRYSTAL_CONDUIT_ITEM = registerBlockItem("raw_crystal_conduit",
            RAW_CRYSTAL_CONDUIT, Rarity.COMMON, 64);
    public static final Block RUNED_CONDUIT = registerConduit("runed_conduit",
            Blocks.EXPOSED_COPPER, 4, ManaTransportRules.ConduitTier.RUNED);
    public static final Item RUNED_CONDUIT_ITEM = registerBlockItem("runed_conduit",
            RUNED_CONDUIT, Rarity.UNCOMMON, 64);
    public static final Block RESONANT_CONDUIT = registerConduit("resonant_conduit",
            Blocks.AMETHYST_BLOCK, 7, ManaTransportRules.ConduitTier.RESONANT);
    public static final Item RESONANT_CONDUIT_ITEM = registerBlockItem("resonant_conduit",
            RESONANT_CONDUIT, Rarity.RARE, 64);

    public static final Block CRYSTAL_VIAL = registerReservoir("crystal_vial", Blocks.GLASS, 3,
            ManaReservoir.Tier.CRYSTAL_VIAL, ManaTransportRules.ConduitTier.RAW_CRYSTAL);
    public static final Item CRYSTAL_VIAL_ITEM = registerBlockItem("crystal_vial",
            CRYSTAL_VIAL, Rarity.UNCOMMON, 16);
    public static final Block RUNED_MANA_CELL = registerReservoir("runed_mana_cell",
            Blocks.COPPER_BLOCK, 5, ManaReservoir.Tier.RUNED_CELL,
            ManaTransportRules.ConduitTier.RUNED);
    public static final Item RUNED_MANA_CELL_ITEM = registerBlockItem("runed_mana_cell",
            RUNED_MANA_CELL, Rarity.RARE, 16);
    public static final Block RESONANT_VAULT = registerReservoir("resonant_vault",
            Blocks.OBSIDIAN, 8, ManaReservoir.Tier.RESONANT_VAULT,
            ManaTransportRules.ConduitTier.RESONANT);
    public static final Item RESONANT_VAULT_ITEM = registerBlockItem("resonant_vault",
            RESONANT_VAULT, Rarity.EPIC, 4);

    public static final BlockEntityType<ManaCrystalNodeBlockEntity> MANA_CRYSTAL_NODE_ENTITY =
            Registry.register(
                    Registries.BLOCK_ENTITY_TYPE,
                    Identifier.of("vector_regnum", "mana_crystal_node"),
                    FabricBlockEntityTypeBuilder.create(ManaCrystalNodeBlockEntity::new,
                            MANA_CRYSTAL_NODE).build());

    public static final BlockEntityType<ManaReservoirBlockEntity> MANA_RESERVOIR_ENTITY =
            Registry.register(
                    Registries.BLOCK_ENTITY_TYPE,
                    Identifier.of("vector_regnum", "mana_reservoir"),
                    FabricBlockEntityTypeBuilder.create(ManaReservoirBlockEntity::new,
                            CRYSTAL_VIAL, RUNED_MANA_CELL, RESONANT_VAULT).build());

    private static PlayerManaBridge manaBridge = PlayerManaBridge.DISCONNECTED;

    private ProgressionContent() {
    }

    public static void initialize(PlayerManaBridge bridge) {
        manaBridge = bridge == null ? PlayerManaBridge.DISCONNECTED : bridge;
        ProgressionData.initialize();
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS)
                .register(entries -> entries.add(MANA_CRYSTAL_SHARD));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL)
                .register(entries -> {
                    entries.add(MANA_CRYSTAL_NODE_ITEM);
                    entries.add(RAW_CRYSTAL_CONDUIT_ITEM);
                    entries.add(RUNED_CONDUIT_ITEM);
                    entries.add(RESONANT_CONDUIT_ITEM);
                    entries.add(CRYSTAL_VIAL_ITEM);
                    entries.add(RUNED_MANA_CELL_ITEM);
                    entries.add(RESONANT_VAULT_ITEM);
                });
    }

    public static void initialize() {
        initialize(PlayerManaBridge.DISCONNECTED);
    }

    static PlayerManaBridge manaBridge() {
        return manaBridge;
    }

    private static Block registerConduit(String id, Block base, int light,
            ManaTransportRules.ConduitTier tier) {
        return Registry.register(Registries.BLOCK, Identifier.of("vector_regnum", id),
                new ManaConduitBlock(AbstractBlock.Settings.copy(base)
                        .strength(2.5f, 7.0f).luminance(state -> light), tier));
    }

    private static Block registerReservoir(String id, Block base, int light,
            ManaReservoir.Tier tier, ManaTransportRules.ConduitTier conduitTier) {
        return Registry.register(Registries.BLOCK, Identifier.of("vector_regnum", id),
                new ManaReservoirBlock(AbstractBlock.Settings.copy(base)
                        .strength(tier == ManaReservoir.Tier.RESONANT_VAULT ? 12.0f : 3.5f,
                                tier == ManaReservoir.Tier.RESONANT_VAULT ? 1200.0f : 8.0f)
                        .luminance(state -> light), tier, conduitTier));
    }

    private static Item registerBlockItem(String id, Block block, Rarity rarity, int maxCount) {
        return Registry.register(Registries.ITEM, Identifier.of("vector_regnum", id),
                new BlockItem(block, new Item.Settings().rarity(rarity).maxCount(maxCount)));
    }
}
