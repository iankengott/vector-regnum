package vectorregnum.neoforge.progression;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Canonical, data-facing item-to-channel tuning vocabulary for crystal infrastructure. */
public final class ManaTuningItems {
    private static final Map<Item, ManaAffinity> BY_ITEM;

    static {
        Map<Item, ManaAffinity> values = new LinkedHashMap<>();
        values.put(Items.PRISMARINE_CRYSTALS, ManaAffinity.WATER);
        values.put(Items.BLAZE_POWDER, ManaAffinity.FIRE);
        values.put(Items.FEATHER, ManaAffinity.AIR);
        values.put(Items.CLAY_BALL, ManaAffinity.EARTH);
        values.put(Items.COPPER_INGOT, ManaAffinity.LIGHTNING);
        values.put(Items.CLOCK, ManaAffinity.TIME);
        values.put(Items.ENDER_PEARL, ManaAffinity.SPACE);
        values.put(Items.GLOWSTONE_DUST, ManaAffinity.LIGHT);
        values.put(Items.INK_SAC, ManaAffinity.DARK);
        values.put(Items.WHEAT_SEEDS, ManaAffinity.NATURE);
        values.put(Items.SNOWBALL, ManaAffinity.ICE);
        values.put(Items.ECHO_SHARD, ManaAffinity.SOUND);
        values.put(Items.ENDER_EYE, ManaAffinity.VOID);
        values.put(Items.AMETHYST_SHARD, ManaAffinity.ARCANE);
        BY_ITEM = Map.copyOf(values);
    }

    private ManaTuningItems() {
    }

    public static Optional<ManaAffinity> affinity(ItemStack stack) {
        return stack == null ? Optional.empty() : Optional.ofNullable(BY_ITEM.get(stack.getItem()));
    }

    public static Map<Item, ManaAffinity> entries() {
        return BY_ITEM;
    }
}
