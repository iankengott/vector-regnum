package vectorregnum.neoforge.ritual;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;
import vectorregnum.core.casting.ReagentKind;
import vectorregnum.core.casting.ReagentLoadout;
import vectorregnum.core.ritual.CooperativeRitual;
import vectorregnum.core.ritual.CooperativeRitualLedger;
import vectorregnum.neoforge.CastingResourceService;

/** Overworld SavedData for restart-safe cooperative ritual state and audit records. */
public final class CooperativeRitualSavedData extends SavedData {
    public static final String FILE_ID = "vector_regnum_cooperative_rituals";
    private static final Logger LOGGER = LogUtils.getLogger();
    private CooperativeRitualLedger ledger;

    public CooperativeRitualSavedData() {
        this(CooperativeRitualLedger.EMPTY);
    }

    public CooperativeRitualSavedData(CooperativeRitualLedger ledger) {
        this.ledger = ledger == null ? CooperativeRitualLedger.EMPTY : ledger;
    }

    public static SavedData.Factory<CooperativeRitualSavedData> factory() {
        return new SavedData.Factory<>(CooperativeRitualSavedData::new, CooperativeRitualSavedData::load);
    }

    public static CooperativeRitualSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    public CooperativeRitualLedger ledger() {
        return ledger;
    }

    public boolean replace(CooperativeRitualLedger replacement) {
        CooperativeRitualLedger normalized = replacement == null
                ? CooperativeRitualLedger.EMPTY : replacement;
        if (normalized.equals(ledger)) return false;
        ledger = normalized;
        setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("schema", CooperativeRitual.CURRENT_SCHEMA);
        ListTag rituals = new ListTag();
        ledger.entries().values().stream()
                .sorted(java.util.Comparator.comparing(CooperativeRitual::ritualId))
                .map(CooperativeRitualSavedData::encodeRitual).forEach(rituals::add);
        tag.put("rituals", rituals);
        return tag;
    }

    public static CooperativeRitualSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        try {
            int schema = requireInt(tag, "schema");
            if (schema < 1 || schema > CooperativeRitual.CURRENT_SCHEMA) {
                throw new IllegalArgumentException("unsupported cooperative ritual file schema " + schema);
            }
            Tag raw = tag.get("rituals");
            if (!(raw instanceof ListTag rituals)
                    || (!rituals.isEmpty() && rituals.getElementType() != Tag.TAG_COMPOUND)
                    || rituals.size() > CooperativeRitualLedger.MAX_RECORDS) {
                throw new IllegalArgumentException("cooperative ritual list is malformed or over cap");
            }
            Map<UUID, CooperativeRitual> entries = new LinkedHashMap<>();
            for (Tag element : rituals) {
                CooperativeRitual ritual = decodeRitual((CompoundTag) element);
                if (entries.put(ritual.ritualId(), ritual) != null) {
                    throw new IllegalArgumentException("duplicate cooperative ritual id");
                }
            }
            return new CooperativeRitualSavedData(entries.isEmpty()
                    ? CooperativeRitualLedger.EMPTY : new CooperativeRitualLedger(entries));
        } catch (RuntimeException exception) {
            LOGGER.error("Quarantining malformed Vector-Regnum cooperative ritual ledger", exception);
            CooperativeRitualSavedData reset = new CooperativeRitualSavedData();
            reset.setDirty();
            return reset;
        }
    }

    private static CompoundTag encodeRitual(CooperativeRitual ritual) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", ritual.schema());
        tag.putUUID("id", ritual.ritualId());
        tag.putUUID("leader", ritual.leaderId());
        tag.putString("leader_name", ritual.leaderName());
        tag.putString("title", ritual.title());
        tag.putString("circle", ritual.circlePayload());
        tag.putString("mode", ritual.mode().name());
        tag.putString("dimension", ritual.dimension());
        tag.putLong("created", ritual.createdTick());
        tag.putLong("expires", ritual.expiresTick());
        tag.putLong("revision", ritual.revision());
        tag.putString("state", ritual.state().name());
        tag.putString("reason", ritual.terminalReason());
        ListTag participants = new ListTag();
        ritual.participants().stream().map(CooperativeRitualSavedData::encodeParticipant)
                .forEach(participants::add);
        tag.put("participants", participants);
        return tag;
    }

    private static CompoundTag encodeParticipant(CooperativeRitual.Participant participant) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("player", participant.playerId());
        tag.putString("name", participant.name());
        tag.putDouble("max_mana", participant.terms().maxMana());
        tag.putInt("max_reagents", participant.terms().maxReagentUnits());
        tag.putDouble("max_upkeep", participant.terms().maxUpkeep());
        tag.putString("status", participant.status().name());
        tag.putDouble("allocated_mana", participant.allocatedMana());
        tag.putDouble("allocated_upkeep", participant.allocatedUpkeep());
        for (ReagentKind kind : ReagentKind.values()) {
            tag.putInt("reagent_" + kind.stableId(), participant.loadout().units(kind));
        }
        tag.putInt("offerings", participant.loadout().offeringUnits());
        return tag;
    }

    private static CooperativeRitual decodeRitual(CompoundTag tag) {
        if (!tag.hasUUID("id") || !tag.hasUUID("leader")) {
            throw new IllegalArgumentException("cooperative ritual identity is missing");
        }
        Tag raw = tag.get("participants");
        if (!(raw instanceof ListTag participants)
                || participants.getElementType() != Tag.TAG_COMPOUND
                || participants.isEmpty()
                || participants.size() > CooperativeRitual.MAX_PARTICIPANTS) {
            throw new IllegalArgumentException("cooperative ritual participants are malformed");
        }
        List<CooperativeRitual.Participant> decoded = new ArrayList<>();
        for (Tag participant : participants) decoded.add(decodeParticipant((CompoundTag) participant));
        return new CooperativeRitual(requireInt(tag, "schema"), tag.getUUID("id"),
                tag.getUUID("leader"), requireString(tag, "leader_name"),
                requireString(tag, "title"), requireString(tag, "circle"),
                CooperativeRitual.Mode.valueOf(requireString(tag, "mode")),
                requireString(tag, "dimension"), requireLong(tag, "created"),
                requireLong(tag, "expires"), requireLong(tag, "revision"),
                CooperativeRitual.State.valueOf(requireString(tag, "state")),
                tag.getString("reason"), decoded);
    }

    private static CooperativeRitual.Participant decodeParticipant(CompoundTag tag) {
        if (!tag.hasUUID("player")) throw new IllegalArgumentException("ritual participant identity is missing");
        EnumMap<ReagentKind, Integer> units = new EnumMap<>(ReagentKind.class);
        for (ReagentKind kind : ReagentKind.values()) {
            units.put(kind, requireInt(tag, "reagent_" + kind.stableId()));
        }
        ReagentLoadout loadout = ReagentLoadout.of(units, requireInt(tag, "offerings"),
                CastingResourceService.policy());
        return new CooperativeRitual.Participant(tag.getUUID("player"), requireString(tag, "name"),
                new CooperativeRitual.Terms(requireDouble(tag, "max_mana"),
                        requireInt(tag, "max_reagents"), requireDouble(tag, "max_upkeep")),
                CooperativeRitual.ParticipantStatus.valueOf(requireString(tag, "status")),
                loadout, requireDouble(tag, "allocated_mana"),
                requireDouble(tag, "allocated_upkeep"));
    }

    private static int requireInt(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_INT)) throw new IllegalArgumentException("missing int " + key);
        return tag.getInt(key);
    }

    private static long requireLong(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_LONG)) throw new IllegalArgumentException("missing long " + key);
        return tag.getLong(key);
    }

    private static double requireDouble(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_DOUBLE)) throw new IllegalArgumentException("missing double " + key);
        return tag.getDouble(key);
    }

    private static String requireString(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_STRING)) throw new IllegalArgumentException("missing string " + key);
        return tag.getString(key);
    }
}
