package vectorregnum.neoforge;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
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

/** Registered spell media backed by checksummed authored-circle payloads. */
public final class SpellMediaContent {
    public static final Item SPELL_SCROLL = Registry.register(
            Registries.ITEM,
            Identifier.of(VectorRegnumMod.MOD_ID, "spell_scroll"),
            new Item(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)));

    public static final Item SPELL_BOOK = Registry.register(
            Registries.ITEM,
            Identifier.of(VectorRegnumMod.MOD_ID, "spell_book"),
            new Item(new Item.Settings().maxCount(1).rarity(Rarity.RARE)));

    public static final Block CARVED_TABLET = Registry.register(
            Registries.BLOCK,
            Identifier.of(VectorRegnumMod.MOD_ID, "carved_spell_tablet"),
            new SpellTabletBlock(AbstractBlock.Settings.copy(Blocks.CHISELED_DEEPSLATE)
                    .strength(-1.0F, 1_200.0F)
                    .luminance(state -> 4)
                    .dropsNothing()));

    public static final Item CARVED_TABLET_ITEM = Registry.register(
            Registries.ITEM,
            Identifier.of(VectorRegnumMod.MOD_ID, "carved_spell_tablet"),
            new BlockItem(CARVED_TABLET,
                    new Item.Settings().maxCount(1).rarity(Rarity.EPIC)));

    public static final BlockEntityType<SpellTabletBlockEntity> TABLET_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(VectorRegnumMod.MOD_ID, "carved_spell_tablet"),
            FabricBlockEntityTypeBuilder.create(SpellTabletBlockEntity::new, CARVED_TABLET).build());

    private SpellMediaContent() {
    }

    public static void initialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(SPELL_SCROLL);
            entries.add(SPELL_BOOK);
            entries.add(CARVED_TABLET_ITEM);
        });
        UseItemCallback.EVENT.register(CircleAuthoringService::useHandheldArtifact);
    }
}
