package vectorregnum.neoforge.multiplayer;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

/**
 * Per-dimension, server-authoritative persistence for spell claims.
 *
 * <p>NeoForge can persist level attachments, but claims intentionally use
 * explicit {@link SavedData} so immutable ledger replacement and dirty-state
 * ownership stay visible and testable. The file id is constant because
 * {@link ServerLevel#getDataStorage()} already scopes it to one dimension.</p>
 */
public final class ClaimSavedData extends SavedData {
    public static final String FILE_ID = "vector_regnum_spell_claims";
    private static final String SCHEMA_KEY = "schema";
    private static final String CLAIMS_KEY = "claims";
    private static final Logger LOGGER = LogUtils.getLogger();

    private ClaimLedger ledger;

    public ClaimSavedData() {
        this(ClaimLedger.EMPTY);
    }

    public ClaimSavedData(ClaimLedger ledger) {
        this.ledger = ledger == null ? ClaimLedger.EMPTY : ledger.migrated();
    }

    public static SavedData.Factory<ClaimSavedData> factory() {
        return new SavedData.Factory<>(ClaimSavedData::new, ClaimSavedData::load);
    }

    /** Returns the one claim ledger owned by this dimension. */
    public static ClaimSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    public ClaimLedger ledger() {
        return ledger;
    }

    /**
     * Replaces the immutable snapshot and owns the corresponding dirty bit.
     * Equal replacements are deliberately no-ops, making retries idempotent.
     */
    public boolean replace(ClaimLedger replacement) {
        ClaimLedger normalized = replacement == null
                ? ClaimLedger.EMPTY : replacement.migrated();
        if (ledger.equals(normalized)) return false;
        ledger = normalized;
        setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(SCHEMA_KEY, ClaimLedger.CURRENT_SCHEMA);
        DataResult<Tag> encoded = ClaimLedger.CODEC.encodeStart(NbtOps.INSTANCE, ledger);
        encoded.result().ifPresent(value -> tag.put(CLAIMS_KEY, value));
        return tag;
    }

    static ClaimSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        try {
            int schema = tag.contains(SCHEMA_KEY, Tag.TAG_INT)
                    ? tag.getInt(SCHEMA_KEY) : 1;
            if (schema < 1 || schema > ClaimLedger.CURRENT_SCHEMA) {
                throw new IllegalArgumentException("unsupported spell-claim schema " + schema);
            }

            Tag encoded = tag.get(CLAIMS_KEY);
            if (!(encoded instanceof ListTag list)) {
                throw new IllegalArgumentException("spell-claim payload is not a list");
            }

            // Bound the NBT handed to the codec before it materializes a Java
            // list.  Keep one schema marker plus the maximum claim entries.
            ListTag bounded = new ListTag();
            int limit = ClaimLedger.MAX_WORLD_CLAIMS + 1;
            for (int index = 0; index < list.size() && index < limit; index++) {
                Tag entry = list.get(index);
                if (entry.getId() != Tag.TAG_STRING) {
                    throw new IllegalArgumentException("spell-claim entry is not a string");
                }
                bounded.add(entry.copy());
            }
            if (!bounded.isEmpty() && bounded.getString(0).startsWith("schema=")) {
                int encodedSchema;
                try {
                    encodedSchema = Integer.parseInt(
                            bounded.getString(0).substring("schema=".length()));
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("malformed spell-claim schema marker",
                            exception);
                }
                if (encodedSchema < 1 || encodedSchema > ClaimLedger.CURRENT_SCHEMA) {
                    throw new IllegalArgumentException(
                            "unsupported spell-claim schema " + encodedSchema);
                }
            }

            ClaimLedger decoded = ClaimLedger.CODEC.parse(NbtOps.INSTANCE, bounded)
                    .resultOrPartial(error -> LOGGER.warn(
                            "Ignoring malformed Vector-Regnum spell claims: {}", error))
                    .orElseThrow(() -> new IllegalArgumentException("malformed spell claims"));
            ClaimLedger migrated = decoded.migrated();
            ClaimSavedData result = new ClaimSavedData(migrated);
            if (schema != ClaimLedger.CURRENT_SCHEMA || decoded != migrated) result.setDirty();
            return result;
        } catch (RuntimeException exception) {
            // A bad optional claim file must never prevent the dimension from
            // loading.  Replacing it on the next save converges to empty state.
            LOGGER.warn("Resetting malformed Vector-Regnum spell claims", exception);
            ClaimSavedData result = new ClaimSavedData(ClaimLedger.EMPTY);
            result.setDirty();
            return result;
        }
    }
}
