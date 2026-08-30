package vectorregnum.core.ritual;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Bounded immutable world ledger for active rituals and terminal audit records. */
public final class CooperativeRitualLedger {
    public static final int MAX_RECORDS = 128;
    public static final CooperativeRitualLedger EMPTY = new CooperativeRitualLedger(Map.of());

    private final Map<UUID, CooperativeRitual> entries;

    public CooperativeRitualLedger(Map<UUID, CooperativeRitual> entries) {
        Objects.requireNonNull(entries, "entries");
        if (entries.size() > MAX_RECORDS) throw new IllegalArgumentException("ritual ledger cap exceeded");
        LinkedHashMap<UUID, CooperativeRitual> copy = new LinkedHashMap<>();
        entries.forEach((id, ritual) -> {
            if (!id.equals(ritual.ritualId())) throw new IllegalArgumentException("ritual ledger key mismatch");
            if (copy.put(id, ritual) != null) throw new IllegalArgumentException("duplicate ritual id");
        });
        this.entries = Map.copyOf(copy);
    }

    public Map<UUID, CooperativeRitual> entries() {
        return entries;
    }

    public CooperativeRitual get(UUID id) {
        return entries.get(id);
    }

    public Change put(CooperativeRitual ritual) {
        Objects.requireNonNull(ritual, "ritual");
        CooperativeRitual previous = entries.get(ritual.ritualId());
        if (ritual.equals(previous)) return new Change(this, false);
        if (previous != null && ritual.revision() < previous.revision()) {
            throw new IllegalArgumentException("ritual revision cannot move backward");
        }
        if (previous == null && entries.size() >= MAX_RECORDS) {
            throw new IllegalStateException("ritual ledger cap reached");
        }
        LinkedHashMap<UUID, CooperativeRitual> updated = new LinkedHashMap<>(entries);
        updated.put(ritual.ritualId(), ritual);
        return new Change(new CooperativeRitualLedger(updated), true);
    }

    public CooperativeRitualLedger remove(UUID id) {
        if (!entries.containsKey(id)) return this;
        LinkedHashMap<UUID, CooperativeRitual> updated = new LinkedHashMap<>(entries);
        updated.remove(id);
        return updated.isEmpty() ? EMPTY : new CooperativeRitualLedger(updated);
    }

    public long activeForLeader(UUID leader) {
        return entries.values().stream().filter(value -> value.leaderId().equals(leader))
                .filter(value -> !value.state().terminal()).count();
    }

    public record Change(CooperativeRitualLedger ledger, boolean changed) {
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof CooperativeRitualLedger other && entries.equals(other.entries);
    }

    @Override
    public int hashCode() {
        return entries.hashCode();
    }
}
