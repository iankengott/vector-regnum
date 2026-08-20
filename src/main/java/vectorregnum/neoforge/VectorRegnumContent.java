package vectorregnum.neoforge;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The small set of standalone Vector-Regnum items. */
public final class VectorRegnumContent {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VectorRegnumMod.MOD_ID);

    public static final DeferredItem<Item> SIGIL_TOME = ITEMS.register("sigil_tome",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));

    private VectorRegnumContent() {
    }

    /** Registers this content and its creative-tab contribution on the mod bus. */
    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(VectorRegnumContent::addCreativeTabItems);
    }

    private static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) {
            event.accept(SIGIL_TOME);
        }
    }
}
