package vectorregnum.neoforge.progression;

import static vectorregnum.neoforge.progression.LibraryOpcode.*;
import static vectorregnum.neoforge.progression.SpellInstruction.number;
import static vectorregnum.neoforge.progression.SpellInstruction.op;
import static vectorregnum.neoforge.progression.SpellInstruction.text;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Curated semantic spell programs. The opcodes deliberately describe behavior rather
 * than Fabric calls so the library can be lowered into vm2 without rewriting content.
 */
public final class ProgressionSpellLibrary {
    public static final Set<String> FIVE_SPELL_EXPANSION_IDS = Set.of(
            "fireball", "teleport", "storm_arc", "tidal_prison", "stone_aegis");

    public static final List<SpellDefinition> ALL = List.of(
            spell("ember_lance", "Ember Lance", SpellCategory.COMBAT, 1, 85,
                    ProgressionUnlock.COMBAT_WEAVING,
                    op(ORIGIN_SELF), op(LOOK_VECTOR), op(ELEMENT_FIRE), op(SHAPE_PROJECTILE),
                    number(SET_MAGNITUDE, "power", 2), op(APPLY_DAMAGE), op(EXECUTE)),
            spell("chain_ice", "Chain Ice", SpellCategory.COMBAT, 3, 240,
                    ProgressionUnlock.COMBAT_WEAVING,
                    op(ORIGIN_TARGET), number(SELECT_NEARBY_ENTITIES, "radius", 7), op(FILTER_HOSTILE),
                    op(ELEMENT_ICE), number(REPEAT_BOUNDED, "count", 4), op(APPLY_SLOW),
                    op(APPLY_DAMAGE), op(EXECUTE)),
            spell("gravity_slam", "Gravity Slam", SpellCategory.COMBAT, 2, 170,
                    ProgressionUnlock.COMBAT_WEAVING,
                    op(ORIGIN_SELF), number(SELECT_NEARBY_ENTITIES, "radius", 5), op(FILTER_HOSTILE),
                    number(SET_MAGNITUDE, "power", 3), text(APPLY_IMPULSE, "direction", "down"),
                    op(APPLY_DAMAGE), op(EXECUTE)),
            spell("fireball", "Fireball", SpellCategory.COMBAT, 2, 135,
                    ProgressionUnlock.COMBAT_WEAVING,
                    op(ORIGIN_SELF), op(LOOK_VECTOR), op(ELEMENT_FIRE), op(SHAPE_PROJECTILE),
                    number(SET_RADIUS, "blocks", 3), number(SET_MAGNITUDE, "power", 3),
                    number(SET_TARGET_LIMIT, "count", 6), op(APPLY_EXPLOSION), op(EXECUTE)),
            spell("storm_arc", "Storm Arc", SpellCategory.COMBAT, 2, 160,
                    ProgressionUnlock.COMBAT_WEAVING,
                    op(ORIGIN_SELF), number(SELECT_NEARBY_ENTITIES, "radius", 8), op(FILTER_HOSTILE),
                    op(ELEMENT_LIGHTNING), op(SHAPE_AURA), number(SET_TARGET_LIMIT, "count", 6),
                    number(SET_MAGNITUDE, "power", 2.2), op(APPLY_DAMAGE), op(EXECUTE)),
            spell("tidal_prison", "Tidal Prison", SpellCategory.COMBAT, 2, 145,
                    ProgressionUnlock.COMBAT_WEAVING,
                    op(ORIGIN_SELF), number(SELECT_NEARBY_ENTITIES, "radius", 6), op(FILTER_HOSTILE),
                    op(ELEMENT_WATER), op(SHAPE_AURA), number(SET_TARGET_LIMIT, "count", 6),
                    number(SET_DURATION, "ticks", 120), op(APPLY_SLOW), op(EXECUTE)),

            spell("aegis_shell", "Aegis Shell", SpellCategory.DEFENSE, 2, 155,
                    ProgressionUnlock.DEFENSIVE_WEAVING,
                    op(ORIGIN_SELF), op(ELEMENT_ARCANE), op(SHAPE_BARRIER),
                    number(SET_RADIUS, "blocks", 2.5), number(SET_DURATION, "ticks", 120), op(EXECUTE)),
            spell("kinetic_ward", "Kinetic Ward", SpellCategory.DEFENSE, 3, 210,
                    ProgressionUnlock.DEFENSIVE_WEAVING,
                    op(ORIGIN_SELF), number(SELECT_NEARBY_ENTITIES, "radius", 4), op(FILTER_HOSTILE),
                    number(SET_MAGNITUDE, "power", 2.5), text(APPLY_IMPULSE, "direction", "away"),
                    number(SET_DURATION, "ticks", 80), op(EXECUTE)),
            spell("stone_aegis", "Stone Aegis", SpellCategory.DEFENSE, 2, 150,
                    ProgressionUnlock.DEFENSIVE_WEAVING,
                    op(ORIGIN_SELF), op(ELEMENT_EARTH), op(SHAPE_BARRIER),
                    number(SET_RADIUS, "blocks", 2.5), number(SET_DURATION, "ticks", 160),
                    op(EXECUTE)),

            spell("vector_step", "Vector Step", SpellCategory.MOVEMENT, 1, 65,
                    ProgressionUnlock.MOVEMENT_WEAVING,
                    op(ORIGIN_SELF), op(LOOK_VECTOR), number(SET_MAGNITUDE, "power", 1.4),
                    text(APPLY_IMPULSE, "target", "caster"), op(EXECUTE)),
            spell("featherfall", "Featherfall", SpellCategory.MOVEMENT, 1, 45,
                    ProgressionUnlock.MOVEMENT_WEAVING,
                    op(ORIGIN_SELF), number(SET_DURATION, "ticks", 200), op(APPLY_FEATHERFALL), op(EXECUTE)),
            spell("teleport", "Teleport", SpellCategory.MOVEMENT, 2, 125,
                    ProgressionUnlock.MOVEMENT_WEAVING,
                    op(ORIGIN_SELF), op(LOOK_VECTOR), op(RAYCAST_BLOCK), op(ELEMENT_SPACE),
                    op(TELEPORT_CASTER), op(EXECUTE)),

            spell("mage_light", "Mage Light", SpellCategory.UTILITY, 1, 30,
                    ProgressionUnlock.CRYSTAL_HARVEST,
                    op(RAYCAST_BLOCK), number(SET_DURATION, "ticks", 1200), op(PLACE_LIGHT), op(EXECUTE)),
            spell("excavate", "Excavate", SpellCategory.UTILITY, 2, 180,
                    ProgressionUnlock.MANA_STORAGE,
                    op(RAYCAST_BLOCK), number(SET_RADIUS, "blocks", 2), text(BREAK_BLOCKS, "mode", "safe"),
                    op(EXECUTE)),
            spell("stoneweave", "Stoneweave", SpellCategory.UTILITY, 2, 130,
                    ProgressionUnlock.MANA_STORAGE,
                    op(RAYCAST_BLOCK), text(TRANSMUTE_BLOCK, "into", "minecraft:stone"), op(EXECUTE)),

            spell("life_sense", "Life Sense", SpellCategory.DETECTION, 1, 70,
                    ProgressionUnlock.PERCEPTION_WEAVING,
                    op(ORIGIN_SELF), number(SELECT_NEARBY_ENTITIES, "radius", 16), op(FILTER_LIVING),
                    text(EMIT_PARTICLES, "style", "outline"), op(EXECUTE)),
            spell("ore_resonance", "Ore Resonance", SpellCategory.DETECTION, 2, 145,
                    ProgressionUnlock.PERCEPTION_WEAVING,
                    op(ORIGIN_SELF), number(SET_RADIUS, "blocks", 12), op(FILTER_ORE),
                    text(EMIT_PARTICLES, "style", "vein_trace"), op(EXECUTE)),

            spell("sentry_pulse", "Sentry Pulse", SpellCategory.AUTOMATION, 3, 260,
                    ProgressionUnlock.AUTOMATION_WEAVING,
                    op(ORIGIN_SELF), number(SELECT_NEARBY_ENTITIES, "radius", 12), op(FILTER_HOSTILE),
                    op(ELEMENT_ARCANE), op(SHAPE_PROJECTILE), number(REPEAT_BOUNDED, "count", 3),
                    op(APPLY_DAMAGE), op(EXECUTE)),
            spell("harvest_cycle", "Harvest Cycle", SpellCategory.AUTOMATION, 3, 280,
                    ProgressionUnlock.AUTOMATION_WEAVING,
                    op(ORIGIN_SELF), number(SET_RADIUS, "blocks", 6), text(BREAK_BLOCKS, "mode", "mature_crops"),
                    number(WAIT_TICKS, "ticks", 100), number(REPEAT_BOUNDED, "count", 8), op(EXECUTE)),
            spell("redstone_oracle", "Redstone Oracle", SpellCategory.AUTOMATION, 2, 110,
                    ProgressionUnlock.AUTOMATION_WEAVING,
                    op(ORIGIN_SELF), number(SELECT_NEARBY_ENTITIES, "radius", 8), op(FILTER_HOSTILE),
                    number(EMIT_REDSTONE, "strength", 15), op(EXECUTE)));

    public static final Map<String, SpellDefinition> BY_ID;

    static {
        Map<String, SpellDefinition> byId = new LinkedHashMap<>();
        for (SpellDefinition spell : ALL) {
            if (byId.put(spell.id(), spell) != null) {
                throw new IllegalStateException("Duplicate spell id: " + spell.id());
            }
        }
        BY_ID = Map.copyOf(byId);
    }

    private ProgressionSpellLibrary() {
    }

    private static SpellDefinition spell(String id, String title, SpellCategory category,
            int tier, double mana, ProgressionUnlock unlock, SpellInstruction... program) {
        return new SpellDefinition(id, title, category, tier, mana, Set.of(unlock), List.of(program));
    }
}
