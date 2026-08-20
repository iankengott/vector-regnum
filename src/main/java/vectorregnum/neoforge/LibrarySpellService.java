package vectorregnum.neoforge;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import vectorregnum.neoforge.progression.ProgressionData;
import vectorregnum.neoforge.progression.ProgressionSpellLibrary;
import vectorregnum.neoforge.progression.ProgressionState;
import vectorregnum.neoforge.progression.ProgressionUnlock;
import vectorregnum.neoforge.progression.SpellDefinition;
import vectorregnum.core.semantic.LoweringContext;
import vectorregnum.core.semantic.SemanticVmLowerer;

import java.util.Locale;
import java.util.Set;

/** Playable server-side effects for every bounded definition in the curated library. */
public final class LibrarySpellService {
    private static final Set<String> IMPLEMENTED_SPELL_IDS = Set.of(
            "ember_lance", "chain_frost", "gravity_slam",
            "aegis_shell", "kinetic_ward",
            "vector_step", "featherfall",
            "mage_light", "excavate", "stoneweave",
            "life_sense", "ore_resonance",
            "sentry_pulse", "harvest_cycle", "redstone_oracle");
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
        double quotedMana = program.manaCost().total();
        if (chargeMana && !ManaData.ensureAvailable(player, quotedMana)) {
            player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                            "%s requires %.0f μ; you have %.1f / %.1f μ",
                            spell.title(), quotedMana, ManaData.available(player),
                            ManaData.capacity(player))).withStyle(ChatFormatting.RED), true);
            return false;
        }

        boolean applied = NeoForgeVmService.startSemantic(player, program, chargeMana, spell.title(),
                (owner, steps) -> SemanticSpellExecutor.execute(owner, steps, ignoreUnlock));
        if (!applied) return false;
        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                        "%s woven • %.0f μ • %.1f μ remaining",
                        spell.title(), chargeMana ? quotedMana : 0.0,
                        ManaData.available(player))).withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    public static void list(ServerPlayer player) {
        ProgressionState state = ProgressionData.get(player);
        player.sendSystemMessage(Component.literal("VECTOR-REGNUM SPELL LIBRARY • 15 bounded programs")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        for (SpellDefinition spell : ProgressionSpellLibrary.ALL) {
            boolean unlocked = spell.isUnlocked(state);
            double quoted = SemanticVmLowerer.lowerChecked(LibrarySemanticAdapter.adapt(spell),
                    new LoweringContext(spell.id(), 0L, java.util.Map.of())).manaCost().total();
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

}
