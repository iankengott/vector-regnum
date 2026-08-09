package vectorregnum.core.vm2;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Read-only perception boundary implemented by the server adapter. */
public interface WorldAccess {
    Optional<EntitySnapshot> entity(String id);

    Optional<RaycastHit> raycast(Vector3 origin, Vector3 normalizedDirection,
            double maxDistance, SelectionFilter filter);

    List<EntitySnapshot> select(Vector3 center, double radius, SelectionFilter filter);

    WorldAccess EMPTY = new WorldAccess() {
        @Override public Optional<EntitySnapshot> entity(String id) { return Optional.empty(); }
        @Override public Optional<RaycastHit> raycast(Vector3 origin, Vector3 direction,
                double maxDistance, SelectionFilter filter) { return Optional.empty(); }
        @Override public List<EntitySnapshot> select(Vector3 center, double radius,
                SelectionFilter filter) { return List.of(); }
    };

    record EntitySnapshot(String id, Vector3 position, double mass, String kind, Set<String> tags) {
        public EntitySnapshot {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(kind, "kind");
            tags = Set.copyOf(Objects.requireNonNull(tags, "tags"));
            if (id.isBlank() || kind.isBlank()) throw new IllegalArgumentException("id/kind cannot be blank");
            if (!Double.isFinite(mass) || mass <= 0) throw new IllegalArgumentException("mass must be positive");
        }
    }

    record RaycastHit(Vector3 position, Optional<EntitySnapshot> entity, double distance) {
        public RaycastHit {
            Objects.requireNonNull(position, "position");
            entity = Objects.requireNonNull(entity, "entity");
            if (!Double.isFinite(distance) || distance < 0) throw new IllegalArgumentException("bad distance");
        }
    }

    record SelectionFilter(Optional<String> kind, Set<String> requiredTags, boolean includeCaster) {
        public static final SelectionFilter ANY = new SelectionFilter(Optional.empty(), Set.of(), false);
        public SelectionFilter {
            kind = Objects.requireNonNull(kind, "kind");
            requiredTags = Set.copyOf(Objects.requireNonNull(requiredTags, "requiredTags"));
            kind.ifPresent(value -> {
                if (value.isBlank()) throw new IllegalArgumentException("kind cannot be blank");
            });
        }
    }
}
