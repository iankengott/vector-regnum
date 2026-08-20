package vectorregnum.neoforge;

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

/** Registered spell media backed by checksummed authored-circle payloads. */
public final class SpellMediaContent {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VectorRegnumMod.MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(VectorRegnumMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, VectorRegnumMod.MOD_ID);

    public static final DeferredItem<Item> SPELL_SCROLL = ITEMS.register("spell_scroll",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    public static final DeferredItem<Item> SPELL_BOOK = ITEMS.register("spell_book",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    public static final DeferredBlock<SpellTabletBlock> CARVED_TABLET = BLOCKS.register(
            "carved_spell_tablet",
            () -> new SpellTabletBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CHISELED_DEEPSLATE)
                    .strength(-1.0F, 1_200.0F)
                    .lightLevel(state -> 4)
                    .noLootTable()));

    public static final DeferredItem<BlockItem> CARVED_TABLET_ITEM = ITEMS.register(
            "carved_spell_tablet",
            () -> new BlockItem(CARVED_TABLET.get(),
                    new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpellTabletBlockEntity>> TABLET_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("carved_spell_tablet",
                    () -> BlockEntityType.Builder.of(SpellTabletBlockEntity::new,
                            CARVED_TABLET.get()).build(null));

    private SpellMediaContent() {
    }

    /** Registers spell media and its creative-tab contribution on the mod bus. */
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(SpellMediaContent::addCreativeTabItems);
    }

    private static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) {
            event.accept(SPELL_SCROLL);
            event.accept(SPELL_BOOK);
            event.accept(CARVED_TABLET_ITEM);
        }
    }

    /** Runtime accessors keep call sites explicit about deferred resolution. */
    public static Block carvedTablet() {
        return CARVED_TABLET.get();
    }

    public static Item spellScroll() {
        return SPELL_SCROLL.get();
    }

    public static Item spellBook() {
        return SPELL_BOOK.get();
    }

    public static Item carvedTabletItem() {
        return CARVED_TABLET_ITEM.get();
    }

    public static BlockEntityType<SpellTabletBlockEntity> tabletBlockEntity() {
        return TABLET_BLOCK_ENTITY.get();
    }
}
