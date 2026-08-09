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
    private static final AttachmentType<Boolean> RECEIVED = AttachmentRegistry.<Boolean>create(
            Identifier.of(VectorRegnumMod.MOD_ID, "received_tutorial_guide"),
            builder -> builder
                    .initializer(() -> false)
                    .persistent(Codec.BOOL)
                    .copyOnDeath());

    private TutorialGuide() {
    }

    public static void initialize() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            if (!player.getAttachedOrCreate(RECEIVED)) {
                give(player);
                giveStarterTomeIfMissing(player);
            }
        });
    }

    public static void give(ServerPlayerEntity player) {
        player.getInventory().offerOrDrop(createBook());
        player.setAttached(RECEIVED, true);
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
                        + "1. Hold the Firebolt Sigil Tome.\n"
                        + "2. Aim where the spell should travel.\n"
                        + "3. Right-click to cast.\n\n"
                        + "The spell uses your live look vector and stops when it strikes a block or creature."),
                page("MANA\n\n"
                        + "You begin with 500 μ. Mana does not regenerate naturally. Every cast has a computed cost.\n\n"
                        + "Use /vectorregnum mana to inspect what remains. Overspending collapses the circle inward and locks your channel briefly."),
                page("CURRENT SPELLS\n\n"
                        + "/vectorregnum cast firebolt\n"
                        + "/vectorregnum cast frost_nova\n"
                        + "/vectorregnum cast amplified_firebolt\n\n"
                        + "Use /vectorregnum guide if you ever need another copy of this manual."),
                page("PROGRAM RULES\n\n"
                        + "A compatibility spell begins with ORIGIN_SELF, adds element/vector/shape operations, and ends with EXECUTE.\n\n"
                        + "Unknown sigils, invalid order, zero vectors, and instructions after EXECUTE are hard faults."),
                page("WILD MAGIC\n\n"
                        + "Compiler faults are physical. Depending on how far the spell progressed, failure may detonate internally, burst as an unstructured element, or become a violent miscast.\n\n"
                        + "The complete player-authored circle language is still under construction."));
        book.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, new WrittenBookContentComponent(
                RawFilteredPair.of("Vector-Regnum Field Manual"),
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
