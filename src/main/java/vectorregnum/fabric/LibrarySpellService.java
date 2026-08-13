package vectorregnum.fabric;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import vectorregnum.fabric.progression.ProgressionData;
import vectorregnum.fabric.progression.ProgressionSpellLibrary;
import vectorregnum.fabric.progression.ProgressionState;
import vectorregnum.fabric.progression.ProgressionUnlock;
import vectorregnum.fabric.progression.SpellDefinition;
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

    public static boolean cast(ServerPlayerEntity player, String id) {
        return cast(player, id, true, false);
    }

    static boolean castForShowcase(ServerPlayerEntity player, String id) {
        return cast(player, id, false, true);
    }

    private static boolean cast(
            ServerPlayerEntity player, String id, boolean chargeMana, boolean ignoreUnlock) {
        SpellDefinition spell = ProgressionSpellLibrary.BY_ID.get(id);
        if (spell == null || !IMPLEMENTED_SPELL_IDS.contains(id)) {
            player.sendMessage(Text.literal("Unknown library spell: " + id)
                    .formatted(Formatting.RED), false);
            return false;
        }
        if (chargeMana && ManaData.isChannelLocked(player)) {
            player.sendMessage(Text.literal("Mana channel locked for "
                            + ManaData.remainingLockTicks(player) + " more ticks")
                    .formatted(Formatting.RED), true);
            return false;
        }
        ProgressionState progression = ProgressionData.get(player);
        if (!ignoreUnlock && !spell.isUnlocked(progression)) {
            player.sendMessage(Text.literal("Locked: research " + spell.requiredUnlocks().stream()
                            .map(ProgressionUnlock::id).sorted().toList())
                    .formatted(Formatting.RED), false);
            return false;
        }
        var semantic = LibrarySemanticAdapter.adapt(spell);
        if (!SemanticSpellExecutor.preflight(player, semantic.instructions())) return false;
        var program = SemanticVmLowerer.lowerChecked(semantic,
                new LoweringContext(id, player.getUuid().getLeastSignificantBits(), java.util.Map.of()));
        double quotedMana = program.manaCost().total();
        if (chargeMana && !ManaData.ensureAvailable(player, quotedMana)) {
            player.sendMessage(Text.literal(String.format(Locale.ROOT,
                            "%s requires %.0f μ; you have %.1f / %.1f μ",
                            spell.title(), quotedMana, ManaData.available(player),
                            ManaData.capacity(player))).formatted(Formatting.RED), true);
            return false;
        }

        boolean applied = FabricVmService.startSemantic(player, program, chargeMana, spell.title(),
                (owner, steps) -> SemanticSpellExecutor.execute(owner, steps, ignoreUnlock));
        if (!applied) return false;
        player.sendMessage(Text.literal(String.format(Locale.ROOT,
                        "%s woven • %.0f μ • %.1f μ remaining",
                        spell.title(), chargeMana ? quotedMana : 0.0,
                        ManaData.available(player))).formatted(Formatting.AQUA), true);
        return true;
    }

    public static void list(ServerPlayerEntity player) {
        ProgressionState state = ProgressionData.get(player);
        player.sendMessage(Text.literal("VECTOR-REGNUM SPELL LIBRARY • 15 bounded programs")
                .formatted(Formatting.GOLD, Formatting.BOLD), false);
        for (SpellDefinition spell : ProgressionSpellLibrary.ALL) {
            boolean unlocked = spell.isUnlocked(state);
            double quoted = SemanticVmLowerer.lowerChecked(LibrarySemanticAdapter.adapt(spell),
                    new LoweringContext(spell.id(), 0L, java.util.Map.of())).manaCost().total();
            player.sendMessage(Text.literal(String.format(Locale.ROOT,
                            "%s %-18s  T%d  %.0f μ  [%s]",
                            unlocked ? "✓" : "◇", spell.id(), spell.tier(), quoted,
                            spell.category().name().toLowerCase(Locale.ROOT)))
                    .formatted(unlocked ? Formatting.GREEN : Formatting.DARK_GRAY), false);
        }
    }

    public static boolean research(ServerPlayerEntity player, ProgressionUnlock unlock) {
        if (ProgressionData.get(player).has(unlock)) {
            player.sendMessage(Text.literal("Already researched: " + unlock.id())
                    .formatted(Formatting.GRAY), false);
            return true;
        }
        if (unlock != ProgressionUnlock.CRYSTAL_HARVEST
                && !ProgressionData.get(player).has(ProgressionUnlock.MANA_STORAGE)) {
            player.sendMessage(Text.literal("Draw from a crystal source before researching spell schools")
                    .formatted(Formatting.RED), false);
            return false;
        }
        double cost = unlock == ProgressionUnlock.CRYSTAL_HARVEST ? 0.0 : 25.0;
        if (!ManaData.ensureAvailable(player, cost) || !ManaData.trySpend(player, cost)) {
            player.sendMessage(Text.literal("Research needs " + cost + " μ")
                    .formatted(Formatting.RED), false);
            return false;
        }
        ProgressionData.unlock(player, unlock);
        return true;
    }

}
