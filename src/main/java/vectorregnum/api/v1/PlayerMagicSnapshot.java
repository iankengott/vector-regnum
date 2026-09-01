package vectorregnum.api.v1;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable read-only player identity, channel, and unlock projection. */
public record PlayerMagicSnapshot(UUID playerId, String naturalElement, String channel,
        List<String> unlockIds) {
    public static final int MAX_UNLOCKS = 256;

    public PlayerMagicSnapshot {
        Objects.requireNonNull(playerId, "playerId");
        naturalElement = ApiValidation.naturalElement(naturalElement, "naturalElement");
        channel = ApiValidation.element(channel, "channel");
        Objects.requireNonNull(unlockIds, "unlockIds");
        if (unlockIds.size() > MAX_UNLOCKS) {
            throw new IllegalArgumentException("at most " + MAX_UNLOCKS + " unlock IDs are allowed");
        }
        ArrayList<String> sorted = new ArrayList<>(unlockIds.size());
        for (String unlockId : unlockIds) {
            sorted.add(ApiValidation.identifier(unlockId, "unlockId"));
        }
        sorted.sort(String::compareTo);
        for (int index = 1; index < sorted.size(); index++) {
            if (sorted.get(index - 1).equals(sorted.get(index))) {
                throw new IllegalArgumentException("unlockIds must not contain duplicates");
            }
        }
        unlockIds = List.copyOf(sorted);
    }
}
