package vectorregnum.neoforge.automation;

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
import vectorregnum.neoforge.VectorRegnumMod;

/** Registry boundary for the programmable relay and its persistent state. */
public final class AutomationContent {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(VectorRegnumMod.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VectorRegnumMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, VectorRegnumMod.MOD_ID);

    public static final DeferredBlock<AutomationRelayBlock> AUTOMATION_RELAY = BLOCKS.register(
            "automation_relay",
            () -> new AutomationRelayBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CHISELED_COPPER)
                    .strength(4.0F, 8.0F).lightLevel(state -> 3)));

    public static final DeferredItem<BlockItem> AUTOMATION_RELAY_ITEM = ITEMS.register(
            "automation_relay",
            () -> new BlockItem(AUTOMATION_RELAY.get(),
                    new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutomationRelayBlockEntity>>
            AUTOMATION_RELAY_ENTITY = BLOCK_ENTITIES.register("automation_relay",
                    () -> BlockEntityType.Builder.of(AutomationRelayBlockEntity::new,
                            AUTOMATION_RELAY.get()).build(null));

    private AutomationContent() {
    }

    /** Registers the relay and its creative-tab contribution on the mod bus. */
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(AutomationContent::addCreativeTabItems);
    }

    private static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.REDSTONE_BLOCKS)) {
            event.accept(AUTOMATION_RELAY_ITEM);
        }
    }

    public static Block automationRelay() {
        return AUTOMATION_RELAY.get();
    }

    public static Item automationRelayItem() {
        return AUTOMATION_RELAY_ITEM.get();
    }

    public static BlockEntityType<AutomationRelayBlockEntity> automationRelayEntity() {
        return AUTOMATION_RELAY_ENTITY.get();
    }
}
