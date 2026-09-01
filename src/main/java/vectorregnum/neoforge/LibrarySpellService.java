package vectorregnum.neoforge;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import vectorregnum.neoforge.progression.ProgressionData;
import vectorregnum.neoforge.progression.ProgressionSpellLibrary;
import vectorregnum.neoforge.progression.ProgressionState;
import vectorregnum.neoforge.progression.ProgressionUnlock;
import vectorregnum.neoforge.progression.SpellDefinition;
import vectorregnum.core.Element;
import vectorregnum.core.casting.CastingMethod;
import vectorregnum.core.semantic.LoweringContext;
import vectorregnum.core.semantic.SemanticProgram;
import vectorregnum.core.semantic.SemanticVmLowerer;

import java.util.Locale;
import java.util.Set;

/** Playable server-side effects for every bounded definition in the curated library. */
public final class LibrarySpellService {
    private static final Set<String> IMPLEMENTED_SPELL_IDS = Set.of(
            "ember_lance", "chain_ice", "gravity_slam",
            "aegis_shell", "kinetic_ward",
            "stone_aegis",
            "vector_step", "featherfall",
            "teleport",
            "mage_light", "excavate", "stoneweave",
            "life_sense", "ore_resonance",
            "sentry_pulse", "harvest_cycle", "redstone_oracle",
            "fireball", "storm_arc", "tidal_prison");
    private static boolean initialized;

    private LibrarySpellService() {
    }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
    }

    public static Set<String> implementedSpellIds() {
        return IMPLEMENTED_SPELL_IDS;
    }

    public static boolean cast(ServerPlayer player, String id) {
        return cast(player, id, true, false);
    }

    static boolean castForShowcase(ServerPlayer player, String id) {
        return cast(player, id, false, true);
    }

    private static boolean cast(
            ServerPlayer player, String id, boolean chargeMana, boolean ignoreUnlock) {
        // Old saved commands remain accepted, but the legacy spelling is not
        // part of the canonical library listing.
        if ("chain_frost".equals(id)) id = "chain_ice";
        SpellDefinition spell = ProgressionSpellLibrary.BY_ID.get(id);
        if (spell == null || !IMPLEMENTED_SPELL_IDS.contains(id)) {
            player.sendSystemMessage(Component.literal("Unknown library spell: " + id)
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        if (chargeMana && ManaData.isChannelLocked(player)) {
            player.sendSystemMessage(Component.literal("Mana channel locked for "
                            + ManaData.remainingLockTicks(player) + " more ticks")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        ProgressionState progression = ProgressionData.get(player);
        if (!ignoreUnlock && !spell.isUnlocked(progression)) {
            player.sendSystemMessage(Component.literal("Locked: research " + spell.requiredUnlocks().stream()
                            .map(ProgressionUnlock::id).sorted().toList())
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        var semantic = LibrarySemanticAdapter.adapt(spell);
        if (!SemanticSpellExecutor.preflight(player, semantic.instructions())) return false;
        var program = SemanticVmLowerer.lowerChecked(semantic,
                new LoweringContext(id, player.getUUID().getLeastSignificantBits(), java.util.Map.of()));
        // startSemantic owns admission, source draw, and the atomic spend in
        // that order, including the one modifier evaluation used for the
        // displayed and committed quote.
        boolean applied = NeoForgeVmService.startSemanticTracked(player, program, chargeMana,
                spell.title(),
                (owner, steps, effects) -> SemanticSpellExecutor.execute(
                        owner, steps, ignoreUnlock, effects),
                vectorregnum.core.casting.CastingMethod.BARE, true,
                net.minecraft.world.item.ItemStack.EMPTY, ignored -> { });
        if (!applied) return false;
        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                        "%s woven • %.1f μ remaining",
                        spell.title(), ManaData.available(player)))
                .withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    public static void list(ServerPlayer player) {
        ProgressionState state = ProgressionData.get(player);
        player.sendSystemMessage(Component.literal("VECTOR-REGNUM SPELL LIBRARY • "
                        + ProgressionSpellLibrary.ALL.size() + " bounded programs")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        for (SpellDefinition spell : ProgressionSpellLibrary.ALL) {
            boolean unlocked = spell.isUnlocked(state);
            SemanticProgram semantic = LibrarySemanticAdapter.adapt(spell);
            var program = SemanticVmLowerer.lowerChecked(semantic,
                    new LoweringContext(spell.id(), 0L, java.util.Map.of()));
            Element element = spellElement(semantic);
            double quoted = CastingResourceService.integratedBaseline(player, spell.title(),
                    element, CastingMethod.BARE,
                    ManaData.adjustedCost(player, program.manaCost().total(), element),
                    program.instructions().size(),
                    ManaData.adjustedUpkeep(player, program.manaCost().duration(), element),
                    ManaData.instability(player, element)).mana();
            player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                            "%s %-18s  T%d  %.0f μ  [%s]",
                            unlocked ? "✓" : "◇", spell.id(), spell.tier(), quoted,
                            spell.category().name().toLowerCase(Locale.ROOT)))
                    .withStyle(unlocked ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY), false);
        }
    }

    public static boolean research(ServerPlayer player, ProgressionUnlock unlock) {
        if (ProgressionData.get(player).has(unlock)) {
            player.sendSystemMessage(Component.literal("Already researched: " + unlock.id())
                    .withStyle(ChatFormatting.GRAY), false);
            return true;
        }
        if (unlock != ProgressionUnlock.CRYSTAL_HARVEST
                && !ProgressionData.get(player).has(ProgressionUnlock.MANA_STORAGE)) {
            player.sendSystemMessage(Component.literal("Draw from a crystal source before researching spell schools")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        double cost = unlock == ProgressionUnlock.CRYSTAL_HARVEST ? 0.0 : 25.0;
        if (!ManaData.ensureAvailable(player, cost) || !ManaData.trySpend(player, cost)) {
            player.sendSystemMessage(Component.literal("Research needs " + cost + " μ")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        ProgressionData.unlock(player, unlock);
        return true;
    }

    private static Element spellElement(SemanticProgram semantic) {
        return semantic.instructions().stream()
                .map(instruction -> instruction.opcode().name())
                .filter(opcode -> opcode.startsWith("ELEMENT_"))
                .map(opcode -> opcode.substring("ELEMENT_".length()))
                .map(Element::fromId)
                .flatMap(java.util.Optional::stream)
                .findFirst()
                .orElse(Element.ARCANE);
    }

}
