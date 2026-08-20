package vectorregnum.neoforge.progression;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class ProgressionState {
    public static final ProgressionState EMPTY = new ProgressionState(Set.of());

    private final Set<ProgressionUnlock> unlocks;

    public ProgressionState(Collection<ProgressionUnlock> unlocks) {
        EnumSet<ProgressionUnlock> copy = EnumSet.noneOf(ProgressionUnlock.class);
        copy.addAll(unlocks);
        this.unlocks = Collections.unmodifiableSet(copy);
    }

    public Set<ProgressionUnlock> unlocks() {
        return unlocks;
    }

    public boolean has(ProgressionUnlock unlock) {
        return unlocks.contains(unlock);
    }

    public boolean hasAll(Collection<ProgressionUnlock> required) {
        return unlocks.containsAll(required);
    }

    public ProgressionState unlock(ProgressionUnlock unlock) {
        if (has(unlock)) {
            return this;
        }
        EnumSet<ProgressionUnlock> next = EnumSet.noneOf(ProgressionUnlock.class);
        next.addAll(unlocks);
        next.add(unlock);
        return new ProgressionState(next);
    }

    public List<String> ids() {
        return unlocks.stream().map(ProgressionUnlock::id).sorted().toList();
    }

    public static ProgressionState fromIds(List<String> ids) {
        return new ProgressionState(ids.stream().map(ProgressionUnlock::byId).toList());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ProgressionState state && unlocks.equals(state.unlocks);
    }

    @Override
    public int hashCode() {
        return unlocks.hashCode();
    }
}
