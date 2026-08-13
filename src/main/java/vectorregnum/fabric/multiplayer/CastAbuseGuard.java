package vectorregnum.fabric.multiplayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-thread-only bounded admission guard for spell bursts and concurrent VMs. */
public final class CastAbuseGuard {
    public static final int MAX_ACTIVE_PER_PLAYER = 4;
    public static final int MAX_STARTS_PER_WINDOW = 12;
    public static final long WINDOW_TICKS = 20;

    private final Map<UUID, State> actors = new HashMap<>();

    public Admission acquire(UUID actor, long tick) {
        State state = actors.computeIfAbsent(actor, ignored -> new State(tick));
        if (tick < state.windowStart || tick - state.windowStart >= WINDOW_TICKS) {
            state.windowStart = tick;
            state.starts = 0;
        }
        if (state.active >= MAX_ACTIVE_PER_PLAYER) {
            return new Admission(false, "Too many concurrent spells");
        }
        if (state.starts >= MAX_STARTS_PER_WINDOW) {
            return new Admission(false, "Spell start rate exceeded");
        }
        state.active++;
        state.starts++;
        return new Admission(true, "accepted");
    }

    public void release(UUID actor) {
        State state = actors.get(actor);
        if (state == null) return;
        state.active = Math.max(0, state.active - 1);
        if (state.active == 0 && state.starts == 0) actors.remove(actor);
    }

    public int active(UUID actor) {
        State state = actors.get(actor);
        return state == null ? 0 : state.active;
    }

    public void clear(UUID actor) { actors.remove(actor); }
    public void clear() { actors.clear(); }

    public record Admission(boolean accepted, String message) { }

    private static final class State {
        private long windowStart;
        private int starts;
        private int active;

        private State(long windowStart) { this.windowStart = windowStart; }
    }
}
