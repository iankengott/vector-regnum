package vectorregnum.neoforge.progression;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registers progression content without touching the shared registry class. */
public final class ProgressionContent {
    public static final String MOD_ID = "vector_regnum";
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MOD_ID);

    public static final DeferredItem<Item> MANA_CRYSTAL_SHARD = ITEMS.register("mana_crystal_shard",
            () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));

    public static final DeferredBlock<ManaCrystalNodeBlock> MANA_CRYSTAL_NODE = BLOCKS.register(
            "mana_crystal_node",
            () -> new ManaCrystalNodeBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_BLOCK)
                    .strength(-1.0F, 1_200.0F)
                    .lightLevel(state -> 5 + state.getValue(ManaCrystalNodeBlock.CHARGE))));

    public static final DeferredItem<BlockItem> MANA_CRYSTAL_NODE_ITEM = registerBlockItem(
            "mana_crystal_node", MANA_CRYSTAL_NODE, Rarity.RARE, 1);

    public static final DeferredBlock<ManaConduitBlock> RAW_CRYSTAL_CONDUIT = registerConduit(
            "raw_crystal_conduit", Blocks.COPPER_BLOCK, 2, ManaTransportRules.ConduitTier.RAW_CRYSTAL);
    public static final DeferredItem<BlockItem> RAW_CRYSTAL_CONDUIT_ITEM = registerBlockItem(
            "raw_crystal_conduit", RAW_CRYSTAL_CONDUIT, Rarity.COMMON, 64);

    public static final DeferredBlock<ManaConduitBlock> RUNED_CONDUIT = registerConduit(
            "runed_conduit", Blocks.EXPOSED_COPPER, 4, ManaTransportRules.ConduitTier.RUNED);
    public static final DeferredItem<BlockItem> RUNED_CONDUIT_ITEM = registerBlockItem(
            "runed_conduit", RUNED_CONDUIT, Rarity.UNCOMMON, 64);

    public static final DeferredBlock<ManaConduitBlock> RESONANT_CONDUIT = registerConduit(
            "resonant_conduit", Blocks.AMETHYST_BLOCK, 7, ManaTransportRules.ConduitTier.RESONANT);
    public static final DeferredItem<BlockItem> RESONANT_CONDUIT_ITEM = registerBlockItem(
            "resonant_conduit", RESONANT_CONDUIT, Rarity.RARE, 64);

    public static final DeferredBlock<ManaReservoirBlock> CRYSTAL_VIAL = registerReservoir(
            "crystal_vial", Blocks.GLASS, 3, ManaReservoir.Tier.CRYSTAL_VIAL,
            ManaTransportRules.ConduitTier.RAW_CRYSTAL);
    public static final DeferredItem<BlockItem> CRYSTAL_VIAL_ITEM = registerBlockItem(
            "crystal_vial", CRYSTAL_VIAL, Rarity.UNCOMMON, 16);

    public static final DeferredBlock<ManaReservoirBlock> RUNED_MANA_CELL = registerReservoir(
            "runed_mana_cell", Blocks.COPPER_BLOCK, 5, ManaReservoir.Tier.RUNED_CELL,
            ManaTransportRules.ConduitTier.RUNED);
    public static final DeferredItem<BlockItem> RUNED_MANA_CELL_ITEM = registerBlockItem(
            "runed_mana_cell", RUNED_MANA_CELL, Rarity.RARE, 16);

    public static final DeferredBlock<ManaReservoirBlock> RESONANT_VAULT = registerReservoir(
            "resonant_vault", Blocks.OBSIDIAN, 8, ManaReservoir.Tier.RESONANT_VAULT,
            ManaTransportRules.ConduitTier.RESONANT);
    public static final DeferredItem<BlockItem> RESONANT_VAULT_ITEM = registerBlockItem(
            "resonant_vault", RESONANT_VAULT, Rarity.EPIC, 4);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ManaCrystalNodeBlockEntity>>
            MANA_CRYSTAL_NODE_ENTITY = BLOCK_ENTITIES.register("mana_crystal_node",
                    () -> BlockEntityType.Builder.of(ManaCrystalNodeBlockEntity::new,
                            MANA_CRYSTAL_NODE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ManaReservoirBlockEntity>>
            MANA_RESERVOIR_ENTITY = BLOCK_ENTITIES.register("mana_reservoir",
                    () -> BlockEntityType.Builder.of(ManaReservoirBlockEntity::new,
                            CRYSTAL_VIAL.get(), RUNED_MANA_CELL.get(), RESONANT_VAULT.get()).build(null));

    private static PlayerManaBridge manaBridge = PlayerManaBridge.DISCONNECTED;

    private ProgressionContent() {
    }

    /** Registers progression blocks, items, block entities, and creative entries. */
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(ProgressionContent::addCreativeTabItems);
    }

    /** Initializes the loader-neutral progression bridge; registry work is in {@link #register}. */
    public static void initialize(PlayerManaBridge bridge) {
        manaBridge = bridge == null ? PlayerManaBridge.DISCONNECTED : bridge;
        ProgressionData.initialize();
    }

    public static void initialize() {
        initialize(PlayerManaBridge.DISCONNECTED);
    }

    static PlayerManaBridge manaBridge() {
        return manaBridge;
    }

    public static Block manaCrystalNode() {
        return MANA_CRYSTAL_NODE.get();
    }

    public static Item manaCrystalNodeItem() {
        return MANA_CRYSTAL_NODE_ITEM.get();
    }

    public static Item manaCrystalShard() {
        return MANA_CRYSTAL_SHARD.get();
    }

    public static Block rawCrystalConduit() {
        return RAW_CRYSTAL_CONDUIT.get();
    }

    public static Item rawCrystalConduitItem() {
        return RAW_CRYSTAL_CONDUIT_ITEM.get();
    }

    public static Block runedConduit() {
        return RUNED_CONDUIT.get();
    }

    public static Item runedConduitItem() {
        return RUNED_CONDUIT_ITEM.get();
    }

    public static Block resonantConduit() {
        return RESONANT_CONDUIT.get();
    }

    public static Item resonantConduitItem() {
        return RESONANT_CONDUIT_ITEM.get();
    }

    public static Block crystalVial() {
        return CRYSTAL_VIAL.get();
    }

    public static Item crystalVialItem() {
        return CRYSTAL_VIAL_ITEM.get();
    }

    public static Block runedManaCell() {
        return RUNED_MANA_CELL.get();
    }

    public static Item runedManaCellItem() {
        return RUNED_MANA_CELL_ITEM.get();
    }

    public static Block resonantVault() {
        return RESONANT_VAULT.get();
    }

    public static Item resonantVaultItem() {
        return RESONANT_VAULT_ITEM.get();
    }

    public static BlockEntityType<ManaCrystalNodeBlockEntity> manaCrystalNodeEntity() {
        return MANA_CRYSTAL_NODE_ENTITY.get();
    }

    public static BlockEntityType<ManaReservoirBlockEntity> manaReservoirEntity() {
        return MANA_RESERVOIR_ENTITY.get();
    }

    private static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.INGREDIENTS)) {
            event.accept(MANA_CRYSTAL_SHARD);
        } else if (event.getTabKey().equals(CreativeModeTabs.FUNCTIONAL_BLOCKS)) {
            event.accept(MANA_CRYSTAL_NODE_ITEM);
            event.accept(RAW_CRYSTAL_CONDUIT_ITEM);
            event.accept(RUNED_CONDUIT_ITEM);
            event.accept(RESONANT_CONDUIT_ITEM);
            event.accept(CRYSTAL_VIAL_ITEM);
            event.accept(RUNED_MANA_CELL_ITEM);
            event.accept(RESONANT_VAULT_ITEM);
        }
    }

    private static DeferredBlock<ManaConduitBlock> registerConduit(String id, Block base, int light,
            ManaTransportRules.ConduitTier tier) {
        return BLOCKS.register(id, () -> new ManaConduitBlock(BlockBehaviour.Properties.ofFullCopy(base)
                .strength(2.5F, 7.0F).lightLevel(state -> light), tier));
    }

    private static DeferredBlock<ManaReservoirBlock> registerReservoir(String id, Block base, int light,
            ManaReservoir.Tier tier, ManaTransportRules.ConduitTier conduitTier) {
        return BLOCKS.register(id, () -> new ManaReservoirBlock(BlockBehaviour.Properties.ofFullCopy(base)
                .strength(tier == ManaReservoir.Tier.RESONANT_VAULT ? 12.0F : 3.5F,
                        tier == ManaReservoir.Tier.RESONANT_VAULT ? 1_200.0F : 8.0F)
                .lightLevel(state -> light), tier, conduitTier));
    }

    private static DeferredItem<BlockItem> registerBlockItem(String id, DeferredBlock<? extends Block> block,
            Rarity rarity, int maxCount) {
        return ITEMS.register(id, () -> new BlockItem(block.get(),
                new Item.Properties().rarity(rarity).stacksTo(maxCount)));
    }
}
