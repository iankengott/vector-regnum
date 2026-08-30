package vectorregnum.neoforge;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.List;

/** Gives each player a persistent first-join field manual and starter tome. */
public final class TutorialGuide {
    public static final String FIELD_MANUAL_TITLE_PREFIX = "Vector-Regnum Field Manual v";
    public static final String FIELD_MANUAL_TITLE = FIELD_MANUAL_TITLE_PREFIX + "13";
    private static final int CURRENT_GUIDE_VERSION = 13;
    private TutorialGuide() {
    }

    public static void initialize() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (!(event.getEntity() instanceof ServerPlayer player)) {
                return;
            }
            if (!player.getData(PlayerAttachmentContent.RECEIVED_TUTORIAL_GUIDE)
                    || player.getData(PlayerAttachmentContent.TUTORIAL_GUIDE_VERSION)
                    < CURRENT_GUIDE_VERSION) {
                give(player);
                giveStarterTomeIfMissing(player);
            }
        });
    }

    public static void give(ServerPlayer player) {
        player.getInventory().placeItemBackInInventory(createBook());
        player.setData(PlayerAttachmentContent.RECEIVED_TUTORIAL_GUIDE, true);
        player.setData(PlayerAttachmentContent.TUTORIAL_GUIDE_VERSION, CURRENT_GUIDE_VERSION);
        player.sendSystemMessage(Component.literal(
                        "Vector-Regnum Field Manual added to your inventory — right-click it to begin")
                .withStyle(ChatFormatting.GOLD));
    }

    private static void giveStarterTomeIfMissing(ServerPlayer player) {
        if (!player.getInventory().contains(stack -> stack.is(VectorRegnumContent.SIGIL_TOME.get()))) {
            player.getInventory().placeItemBackInInventory(
                    new ItemStack(VectorRegnumContent.SIGIL_TOME.get()));
        }
    }

    private static ItemStack createBook() {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        List<Filterable<Component>> pages = List.of(
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
                page("ELEMENTAL IDENTITY\n\n"
                        + "Every character has exactly one permanent natural element. Channel attunement is mutable and changes source efficiency without changing that identity. Arcane is neutral raw mana; Void is rare. Ice is the cold resonance.\n\n"
                        + "Efficiency bands are symmetric: 100% aligned, 75% near, 50% distant, and 25% opposed (never lower). Try /vectorregnum mana attune ice to change your channel."),
                page("CRYSTAL RECIPES\n\n"
                        + "Shard (shapeless): amethyst shard + lapis lazuli + glowstone dust.\n\n"
                        + "Source (crafting grid):\nALA\nLCL\nALA\n\n"
                        + "A = amethyst block\nL = lapis block\nC = crying obsidian\n\n"
                        + "Tune sources with prismarine crystals=water, blaze powder=fire, feather=air, clay ball=earth, copper ingot=lightning, clock=time, ender pearl=space, glowstone dust=light, ink sac=dark, wheat seeds=nature, snowball=ice, echo shard=sound, ender eye=void, or amethyst shard=arcane. Natural crystals mature and recharge only while loaded and buried in conductive rock."),
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
                page("CASTING METHODS\n\n"
                        + "Six methods share one server quote: bare, ritual, engraving, spellbook, scroll, and installed circle.\n\n"
                        + "/vectorregnum circle bind scroll\n"
                        + "/vectorregnum circle bind book\n"
                        + "/vectorregnum circle bind engraving\n"
                        + "/vectorregnum circle bind tablet\n\n"
                        + "Scrolls are single-use. Books reuse. Engravings consume their construction when accepted and remain reusable while placed. Tablets become permanent installed circles."),
                page("REAGENTS & ESCROW\n\n"
                        + "Stage optional reagents with /vectorregnum reagents stage <kind> <count>. Amethyst reduces mana, sugar casting time, glowstone upkeep, and fermented spider eye instability. Use /vectorregnum reagents clear to take them back.\n\n"
                        + "Stage quartz separately with /vectorregnum reagents stage offering <count>. It satisfies a solo ritual offering but grants no discount.\n\n"
                        + "/vectorregnum circle quote <method> shows requested and applied discounts, clipping, and the bounded final quote. /vectorregnum circle ritual requires the staged offering."),
                page("SETTLEMENT\n\n"
                        + "After admission, mana, staged reagents, and a scroll enter server escrow. Success and genuine spell faults or Wild Magic consume them. Policy, unloaded-target, rate, shutdown, owner-lifecycle, and internal failures refund or never withdraw them.\n\n"
                        + "Books and world media remain reusable. Cooperative rituals use a separate exact reservation for every contributor."),
                page("COOPERATIVE RITUALS\n\n"
                        + "Create a split or replicate ritual from your compiled circle with /vectorregnum ritual create <mode> <max_mana> <max_reagents> <max_upkeep>. The leader invites each named contributor with that contributor's exact maxima.\n\n"
                        + "Each contributor must stage their own exact reagent loadout and run /vectorregnum ritual approve <id>. Nothing starts until every invite is approved. Use ritual status, decline, cancel, or start with the short ritual ID."),
                page("RITUAL SAFETY\n\n"
                        + "Approval moves only that contributor's bounded maxima into checksummed server escrow. Split combines the approved resource pool into one circle. Replicate launches one bounded copy per contributor in stable order.\n\n"
                        + "A pre-start decline, disconnect, expiry, invalid launch, or restart mismatch cancels and refunds every reservation exactly once. After start, the normal success and genuine-fault settlement rules apply."),
                page("PERSISTENT MAGIC\n\n"
                        + "Every continuing effect records its owner, program, dimension, endpoint, hard deadline, cleanup handles, and exact prepaid upkeep. Check yours with /vectorregnum effect status.\n\n"
                        + "Unloaded chunks pause reconciliation until loaded; offline owners cannot drive world mutations. Natural conclusions clean exactly once. Unpaid or non-concluding magic collapses into bounded deterministic Wild Magic, then cleans."),
                page("THE TICKED VM\n\n"
                        + "The new runtime has typed numbers, booleans, points, vectors, entities, and lists; Push/Pop memory; delay/duration; branches; and bounded loops.\n\n"
                        + "Load an editable typed example with /vectorregnum circle vm_starter. Use circle params for comma-separated vector/list/control parameters. VM_CREATE_FORM adds bounded material forms. It yields safely at per-tick limits."),
                page("SHARED CONTROL\n\n"
                        + "Advanced programs can store typed variables, walk lists one item per tick, test entity/point collisions, and publish bounded watcher signals or owner output.\n\n"
                        + "Logical branches share Push/Pop memory. They advance in stable server-tick order; every shared operation is atomic and traced. Work, lifetime, branches, messages, and stack depth remain hard-capped. Try /vectorregnum vm control_demo."),
                page("PERCEPTION & PHYSICS\n\n"
                        + "The VM can select and raycast entities, then emit validated impulse, acceleration, damping, path, move-toward, and keep-distance effects.\n\n"
                        + "Typed sigils use the VM_ prefix and EXECUTE ends the program. Cost names physical work, range, duration, rarity, memory, perception, and control flow."),
                page("SPELL LIBRARY\n\n"
                        + "Use /vectorregnum library list for 20 spells across combat, defense, movement, utility, detection, and automation. The newest programs are Fireball, Storm Arc, Tidal Prison, Stone Aegis, and Teleport.\n\n"
                        + "After drawing crystal mana, spend 25 μ to research a school, for example /vectorregnum research combat_weaving."),
                page("WILD MAGIC\n\n"
                        + "Compiler faults are physical. Depending on how far the spell progressed, failure may detonate internally, burst as an unstructured element, or become a violent miscast.\n\n"
                        + "Use /vectorregnum guide if you need another manual. Admins can use /vectorregnum devkit in a test world."));
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough(FIELD_MANUAL_TITLE),
                "The Realm of Direction",
                0,
                pages,
                true));
        return book;
    }

    private static Filterable<Component> page(String contents) {
        return Filterable.passThrough(Component.literal(contents));
    }
}
