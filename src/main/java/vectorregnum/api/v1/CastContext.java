package vectorregnum.api.v1;

import java.util.Objects;
import java.util.UUID;

/** Immutable context presented to cast-modifier providers. */
public record CastContext(UUID playerId, String spellId, String elementId,
        String castingMethod, CastParameters parameters, long gameTick) {
    public CastContext {
        Objects.requireNonNull(playerId, "playerId");
        ApiValidation.identifier(spellId, "spellId");
        elementId = ApiValidation.element(elementId, "elementId");
        ApiValidation.identifier(castingMethod, "castingMethod");
        Objects.requireNonNull(parameters, "parameters");
        if (gameTick < 0L) {
            throw new IllegalArgumentException("gameTick must be non-negative");
        }
    }
}
