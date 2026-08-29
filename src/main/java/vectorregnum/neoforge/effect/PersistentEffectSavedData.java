package vectorregnum.neoforge.effect;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;
import vectorregnum.core.effect.PersistentEffectContract;
import vectorregnum.core.effect.PersistentEffectLedger;

/** Per-dimension durable ownership, upkeep, and cleanup state for continuing magic. */
public final class PersistentEffectSavedData extends SavedData {
    public static final String FILE_ID = "vector_regnum_persistent_effects";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SCHEMA = "schema";
    private static final String EFFECTS = "effects";
    private PersistentEffectLedger ledger;

    public PersistentEffectSavedData() {
        this(PersistentEffectLedger.EMPTY);
    }

    public PersistentEffectSavedData(PersistentEffectLedger ledger) {
        this.ledger = ledger == null ? PersistentEffectLedger.EMPTY : ledger;
    }

    public static SavedData.Factory<PersistentEffectSavedData> factory() {
        return new SavedData.Factory<>(PersistentEffectSavedData::new,
                PersistentEffectSavedData::load);
    }

    public static PersistentEffectSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    public PersistentEffectLedger ledger() {
        return ledger;
    }

    /** Equal replacement is a no-op, so retries do not dirty the save again. */
    public boolean replace(PersistentEffectLedger replacement) {
        PersistentEffectLedger normalized = replacement == null
                ? PersistentEffectLedger.EMPTY : replacement;
        if (ledger.equals(normalized)) return false;
        ledger = normalized;
        setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(SCHEMA, PersistentEffectContract.CURRENT_SCHEMA);
        ListTag effects = new ListTag();
        ledger.entries().values().stream()
                .sorted(java.util.Comparator.comparing(PersistentEffectContract::effectId))
                .map(PersistentEffectSavedData::encodeContract)
                .forEach(effects::add);
        tag.put(EFFECTS, effects);
        return tag;
    }

    public static PersistentEffectSavedData load(CompoundTag tag,
            HolderLookup.Provider registries) {
        try {
            int schema = requireInt(tag, SCHEMA);
            if (schema < 1 || schema > PersistentEffectContract.CURRENT_SCHEMA) {
                throw new IllegalArgumentException("unsupported persistent-effect file schema " + schema);
            }
            Tag encoded = tag.get(EFFECTS);
            if (!(encoded instanceof ListTag effects)
                    || (!effects.isEmpty() && effects.getElementType() != Tag.TAG_COMPOUND)) {
                throw new IllegalArgumentException("persistent-effect payload is not a compound list");
            }
            if (effects.size() > PersistentEffectLedger.MAX_WORLD_EFFECTS) {
                throw new IllegalArgumentException("persistent-effect payload exceeds the world cap");
            }
            Map<UUID, PersistentEffectContract> contracts = new LinkedHashMap<>();
            for (Tag element : effects) {
                if (!(element instanceof CompoundTag entry)) {
                    throw new IllegalArgumentException("persistent-effect entry is not a compound");
                }
                PersistentEffectContract contract = decodeContract(entry);
                if (contracts.put(contract.effectId(), contract) != null) {
                    throw new IllegalArgumentException("duplicate persistent-effect id");
                }
            }
            PersistentEffectSavedData data = new PersistentEffectSavedData(
                    contracts.isEmpty() ? PersistentEffectLedger.EMPTY
                            : new PersistentEffectLedger(contracts));
            if (schema != PersistentEffectContract.CURRENT_SCHEMA) data.setDirty();
            return data;
        } catch (RuntimeException exception) {
            LOGGER.warn("Resetting malformed Vector-Regnum persistent effects", exception);
            PersistentEffectSavedData data = new PersistentEffectSavedData();
            data.setDirty();
            return data;
        }
    }

    private static CompoundTag encodeContract(PersistentEffectContract contract) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(SCHEMA, contract.schema());
        tag.putUUID("id", contract.effectId());
        tag.putUUID("owner", contract.ownerId());
        tag.putString("program", contract.programHash());
        tag.putString("dimension", contract.dimension());
        tag.putLong("revision", contract.revision());
        tag.putLong("start", contract.startTick());
        tag.putLong("natural_deadline", contract.naturalDeadlineTick());
        tag.putLong("hard_deadline", contract.hardDeadlineTick());
        tag.putInt("upkeep_interval", contract.upkeepIntervalTicks());
        tag.putLong("next_upkeep", contract.nextUpkeepTick());
        tag.putDouble("upkeep_per_interval", contract.upkeepPerInterval());
        tag.putDouble("prepaid_upkeep", contract.prepaidUpkeep());
        tag.putLong("collapse_seed", contract.collapseSeed());
        tag.putString("state", contract.state().name());
        ListTag handles = new ListTag();
        contract.handles().stream().map(StringTag::valueOf).forEach(handles::add);
        tag.put("handles", handles);
        return tag;
    }

    private static PersistentEffectContract decodeContract(CompoundTag tag) {
        if (!tag.hasUUID("id") || !tag.hasUUID("owner")) {
            throw new IllegalArgumentException("persistent-effect owner or id is missing");
        }
        List<String> handles = new ArrayList<>();
        Tag encodedHandles = tag.get("handles");
        if (!(encodedHandles instanceof ListTag list)
                || list.getElementType() != Tag.TAG_STRING
                || list.size() > PersistentEffectContract.MAX_HANDLES) {
            throw new IllegalArgumentException("persistent-effect handles are malformed");
        }
        for (Tag handle : list) handles.add(handle.getAsString());
        return new PersistentEffectContract(
                requireInt(tag, SCHEMA), tag.getUUID("id"), tag.getUUID("owner"),
                requireString(tag, "program"), requireString(tag, "dimension"),
                requireLong(tag, "revision"), requireLong(tag, "start"),
                requireLong(tag, "natural_deadline"), requireLong(tag, "hard_deadline"),
                requireInt(tag, "upkeep_interval"), requireLong(tag, "next_upkeep"),
                requireDouble(tag, "upkeep_per_interval"),
                requireDouble(tag, "prepaid_upkeep"), requireLong(tag, "collapse_seed"),
                PersistentEffectContract.State.valueOf(requireString(tag, "state")), handles);
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
