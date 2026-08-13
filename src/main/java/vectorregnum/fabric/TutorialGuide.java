package vectorregnum.fabric;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;

/** Gives each player a persistent first-join field manual and starter tome. */
public final class TutorialGuide {
    public static final String FIELD_MANUAL_TITLE_PREFIX = "Vector-Regnum Field Manual v";
    public static final String FIELD_MANUAL_TITLE = FIELD_MANUAL_TITLE_PREFIX + "6";
    private static final int CURRENT_GUIDE_VERSION = 6;
    private static final AttachmentType<Boolean> RECEIVED = AttachmentRegistry.<Boolean>create(
            Identifier.of(VectorRegnumMod.MOD_ID, "received_tutorial_guide"),
            builder -> builder
                    .initializer(() -> false)
                    .persistent(Codec.BOOL)
                    .copyOnDeath());

    private static final AttachmentType<Integer> RECEIVED_VERSION = AttachmentRegistry.<Integer>create(
            Identifier.of(VectorRegnumMod.MOD_ID, "tutorial_guide_version"),
            builder -> builder
                    .initializer(() -> 0)
                    .persistent(Codec.INT)
                    .copyOnDeath());

    private TutorialGuide() {
    }

    public static void initialize() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            if (!player.getAttachedOrCreate(RECEIVED)
                    || player.getAttachedOrCreate(RECEIVED_VERSION) < CURRENT_GUIDE_VERSION) {
                give(player);
                giveStarterTomeIfMissing(player);
            }
        });
    }

    public static void give(ServerPlayerEntity player) {
        player.getInventory().offerOrDrop(createBook());
        player.setAttached(RECEIVED, true);
        player.setAttached(RECEIVED_VERSION, CURRENT_GUIDE_VERSION);
        player.sendMessage(Text.literal(
                        "Vector-Regnum Field Manual added to your inventory — right-click it to begin")
                .formatted(Formatting.GOLD), false);
    }

    private static void giveStarterTomeIfMissing(ServerPlayerEntity player) {
        if (!player.getInventory().contains(stack -> stack.isOf(VectorRegnumContent.SIGIL_TOME))) {
            player.getInventory().offerOrDrop(new ItemStack(VectorRegnumContent.SIGIL_TOME));
        }
    }

    private static ItemStack createBook() {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        List<RawFilteredPair<Text>> pages = List.of(
                page("VECTOR-REGNUM\nThe Realm of Direction\n\n"
                        + "Welcome, compiler. Spells are ordered sigil programs. Correct programs reshape the world; broken programs become Wild Magic."),
                page("QUICK START\n\n"
                        + "1. Build and draw from a mana crystal (next page).\n"
                        + "2. Hold the Firebolt Sigil Tome.\n"
                        + "3. Aim and right-click to cast.\n\n"
                        + "The spell uses your live look vector and stops when it strikes a block or creature."),
                page("MANA\n\n"
                        + "You begin with 0 μ and 0 capacity. Mana never regenerates. Craft a crystal source and shards. Use a shard on the source to grow capacity; then use the source with an empty hand to draw.\n\n"
                        + "The last used source can feed later casts while its chunk is loaded; distance weakens each charge by inverse-square falloff. Tune your channel with /vectorregnum mana attune <element>."),
                page("CRYSTAL RECIPES\n\n"
                        + "Shard (shapeless): amethyst shard + lapis lazuli + glowstone dust.\n\n"
                        + "Source (crafting grid):\nALA\nLCL\nALA\n\n"
                        + "A = amethyst block\nL = lapis block\nC = crying obsidian\n\n"
                        + "Use blaze powder, snowball, ender pearl, or amethyst shard on a source to tune fire, frost, void, or arcane. Natural crystals mature and recharge only while loaded and buried in conductive rock."),
                page("MANA STORAGE\n\n"
                        + "Connect a source to a Crystal Vial with Raw Crystal Conduits. Upgrade both together to a Runed Cell/Conduit, then a Resonant Vault/Conduit.\n\n"
                        + "Raw: 200 μ, 8 blocks, 80%\n"
                        + "Runed: 1,000 μ, 24 blocks, 95%\n"
                        + "Resonant: 8,000 μ, 64 blocks, 100%\n\n"
                        + "Use a store for status; sneak-use to draw. Tune only while empty and idle."),
                page("AUTHOR A CIRCLE\n\n"
                        + "/vectorregnum circle new <id>\n"
                        + "/vectorregnum circle place <ring> <slot> <SIGIL>\n"
                        + "/vectorregnum circle parameter <ring> <slot> <number>\n"
                        + "/vectorregnum circle compile\n\n"
                        + "Ring 0 is outside. Slot 0 is north. Programs read clockwise, then inward."),
                page("EDIT AND RUN\n\n"
                        + "/vectorregnum circle show\n"
                        + "/vectorregnum circle remove <ring> <slot>\n"
                        + "/vectorregnum circle undo\n"
                        + "/vectorregnum circle cast\n\n"
                        + "Compiler feedback names the exact physical ring and slot. Saved circles are versioned and checksummed."),
                page("SPELL MEDIA\n\n"
                        + "/vectorregnum circle bind scroll\n"
                        + "/vectorregnum circle bind book\n"
                        + "/vectorregnum circle bind tablet\n\n"
                        + "Craft blanks first: scroll = paper+ink+amethyst; book = book+lapis+amethyst; tablet = chiseled deepslate+lapis+amethyst. Scrolls burn once, books reuse, tablets anchor permanently."),
                page("THE TICKED VM\n\n"
                        + "The new runtime has typed numbers, booleans, points, vectors, entities, and lists; Push/Pop memory; delay/duration; branches; and bounded loops.\n\n"
                        + "Load an editable typed example with /vectorregnum circle vm_starter. Use circle params for comma-separated vector/list/control parameters. VM_CREATE_FORM adds bounded material forms. It yields safely at per-tick limits."),
                page("PERCEPTION & PHYSICS\n\n"
                        + "The VM can select and raycast entities, then emit validated impulse, acceleration, damping, path, move-toward, and keep-distance effects.\n\n"
                        + "Typed sigils use the VM_ prefix and EXECUTE ends the program. Cost names physical work, range, duration, rarity, memory, perception, and control flow."),
                page("SPELL LIBRARY\n\n"
                        + "Use /vectorregnum library list for 15 spells across combat, defense, movement, utility, detection, and automation.\n\n"
                        + "After drawing crystal mana, spend 25 μ to research a school, for example /vectorregnum research combat_weaving."),
                page("WILD MAGIC\n\n"
                        + "Compiler faults are physical. Depending on how far the spell progressed, failure may detonate internally, burst as an unstructured element, or become a violent miscast.\n\n"
                        + "Use /vectorregnum guide if you need another manual. Admins can use /vectorregnum devkit in a test world."));
        book.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, new WrittenBookContentComponent(
                RawFilteredPair.of(FIELD_MANUAL_TITLE),
                "The Realm of Direction",
                0,
                pages,
                true));
        return book;
    }

    private static RawFilteredPair<Text> page(String contents) {
        return RawFilteredPair.of(Text.literal(contents));
    }
}
