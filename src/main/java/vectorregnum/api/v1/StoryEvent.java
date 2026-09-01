package vectorregnum.api.v1;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable observation delivered to story and administration listeners.
 * Consumers must deduplicate by {@link #deliveryKey()} because durable events
 * can be delivered at least once after restart reconciliation.
 */
public record StoryEvent(UUID eventId, long revision, String kind, long gameTick,
        UUID actorId, String dimensionId, int x, int y, int z, String subjectId,
        String elementId, String outcome) {
    public StoryEvent {
        Objects.requireNonNull(eventId, "eventId");
        if (revision < 0L) {
            throw new IllegalArgumentException("revision must be non-negative");
        }
        kind = ApiValidation.identifier(kind, "kind");
        if (gameTick < 0L) {
            throw new IllegalArgumentException("gameTick must be non-negative");
        }
        Objects.requireNonNull(actorId, "actorId");
        dimensionId = ApiValidation.identifier(dimensionId, "dimensionId");
        subjectId = ApiValidation.optionalIdentifier(subjectId, "subjectId");
        elementId = ApiValidation.optionalIdentifier(elementId, "elementId");
        if (!elementId.isEmpty()) {
            elementId = ApiValidation.element(elementId, "elementId");
        }
        outcome = ApiValidation.identifier(outcome, "outcome");
    }

    public DeliveryKey deliveryKey() {
        return new DeliveryKey(eventId, revision);
    }

    /** Stable key for at-least-once listener delivery. */
    public record DeliveryKey(UUID eventId, long revision) {
        public DeliveryKey {
            Objects.requireNonNull(eventId, "eventId");
            if (revision < 0L) {
                throw new IllegalArgumentException("revision must be non-negative");
            }
        }
    }
}
