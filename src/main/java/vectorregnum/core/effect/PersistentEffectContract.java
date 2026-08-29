package vectorregnum.core.effect;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable loader-neutral ownership and upkeep record for one continuing cast.
 * One contract may own several bounded world handles, but it pays the single
 * upkeep commitment shown in that cast's quote.
 */
public record PersistentEffectContract(
        int schema,
        UUID effectId,
        UUID ownerId,
        String programHash,
        String dimension,
        long revision,
        long startTick,
        long naturalDeadlineTick,
        long hardDeadlineTick,
        int upkeepIntervalTicks,
        long nextUpkeepTick,
        double upkeepPerInterval,
        double prepaidUpkeep,
        long collapseSeed,
        State state,
        List<String> handles) {
    public static final int CURRENT_SCHEMA = 1;
    public static final long NO_NATURAL_DEADLINE = -1L;
    public static final int MAX_HANDLES = 128;
    public static final int MAX_HANDLE_LENGTH = 16_384;
    public static final int MAX_TEXT_LENGTH = 256;
    public static final long MAX_LIFETIME_TICKS = 72_000L;
    public static final int MAX_UPKEEP_INTERVAL_TICKS = 1_200;
    public static final double MAX_UPKEEP = 5_000.0;

    public enum State {
        ACTIVE,
        CONCLUDING,
        COLLAPSED,
        COLLAPSE_EMITTED,
        CLEANED
    }

    public PersistentEffectContract {
        if (schema < 1 || schema > CURRENT_SCHEMA) {
            throw new IllegalArgumentException("unsupported persistent-effect schema " + schema);
        }
        Objects.requireNonNull(effectId, "effectId");
        Objects.requireNonNull(ownerId, "ownerId");
        programHash = boundedText(programHash, "programHash");
        dimension = boundedText(dimension, "dimension");
        if (revision < 0L || startTick < 0L) {
            throw new IllegalArgumentException("revision and start tick cannot be negative");
        }
        boolean natural = naturalDeadlineTick != NO_NATURAL_DEADLINE;
        if (natural && naturalDeadlineTick <= startTick) {
            throw new IllegalArgumentException("natural deadline must follow the start tick");
        }
        if (hardDeadlineTick <= startTick
                || hardDeadlineTick - startTick > MAX_LIFETIME_TICKS) {
            throw new IllegalArgumentException("hard deadline exceeds the persistent-effect lifetime cap");
        }
        if (natural && naturalDeadlineTick > hardDeadlineTick) {
            throw new IllegalArgumentException("natural deadline cannot follow the hard deadline");
        }
        if (upkeepIntervalTicks < 1 || upkeepIntervalTicks > MAX_UPKEEP_INTERVAL_TICKS) {
            throw new IllegalArgumentException("upkeep interval is outside the server bounds");
        }
        if (nextUpkeepTick < startTick || nextUpkeepTick > hardDeadlineTick) {
            throw new IllegalArgumentException("next upkeep tick is outside the effect lifetime");
        }
        finiteBounded(upkeepPerInterval, "upkeepPerInterval");
        finiteBounded(prepaidUpkeep, "prepaidUpkeep");
        Objects.requireNonNull(state, "state");
        handles = List.copyOf(Objects.requireNonNull(handles, "handles"));
        if (handles.isEmpty() || handles.size() > MAX_HANDLES) {
            throw new IllegalArgumentException("persistent effects require 1.." + MAX_HANDLES + " handles");
        }
        java.util.HashSet<String> unique = new java.util.HashSet<>();
        for (String handle : handles) {
            if (handle == null || handle.isBlank() || handle.length() > MAX_HANDLE_LENGTH) {
                throw new IllegalArgumentException("persistent-effect handle is blank or too long");
            }
            if (!unique.add(handle)) {
                throw new IllegalArgumentException("duplicate persistent-effect handle");
            }
        }
    }

    public static PersistentEffectContract active(UUID effectId, UUID ownerId,
            String programHash, String dimension, long startTick,
            long naturalDeadlineTick, long hardDeadlineTick,
            int upkeepIntervalTicks, double prepaidUpkeep,
            long collapseSeed, List<String> handles) {
        long nextUpkeep = startTick;
        long paymentSpan = Math.max(1L, effectiveDeadline(
                naturalDeadlineTick, hardDeadlineTick) - startTick);
        long installments = Math.max(1L,
                (paymentSpan + upkeepIntervalTicks - 1L) / upkeepIntervalTicks);
        return new PersistentEffectContract(CURRENT_SCHEMA, effectId, ownerId,
                programHash, dimension, 0L, startTick, naturalDeadlineTick,
                hardDeadlineTick, upkeepIntervalTicks, nextUpkeep,
                prepaidUpkeep / installments, prepaidUpkeep, collapseSeed,
                State.ACTIVE, handles);
    }

    public boolean hasNaturalDeadline() {
        return naturalDeadlineTick != NO_NATURAL_DEADLINE;
    }

    public long effectiveDeadlineTick() {
        return effectiveDeadline(naturalDeadlineTick, hardDeadlineTick);
    }

    public PersistentEffectContract withUpkeep(double balance, long nextTick) {
        return copy(revision + 1L, nextTick, upkeepPerInterval, balance, state);
    }

    public PersistentEffectContract withState(State replacement) {
        Objects.requireNonNull(replacement, "replacement");
        if (state == replacement) return this;
        boolean valid = switch (state) {
            case ACTIVE -> replacement == State.CONCLUDING
                    || replacement == State.COLLAPSED || replacement == State.CLEANED;
            case CONCLUDING -> replacement == State.COLLAPSED || replacement == State.CLEANED;
            case COLLAPSED -> replacement == State.COLLAPSE_EMITTED
                    || replacement == State.CLEANED;
            case COLLAPSE_EMITTED -> replacement == State.CLEANED;
            case CLEANED -> false;
        };
        if (!valid) {
            throw new IllegalStateException("persistent-effect state cannot move backward");
        }
        return copy(revision + 1L, nextUpkeepTick, upkeepPerInterval,
                prepaidUpkeep, replacement);
    }

    public PersistentEffectContract withHandles(List<String> replacement) {
        Objects.requireNonNull(replacement, "replacement");
        if (handles.equals(replacement)) return this;
        return new PersistentEffectContract(schema, effectId, ownerId, programHash,
                dimension, revision + 1L, startTick, naturalDeadlineTick,
                hardDeadlineTick, upkeepIntervalTicks, nextUpkeepTick,
                upkeepPerInterval, prepaidUpkeep, collapseSeed, state, replacement);
    }

    private PersistentEffectContract copy(long newRevision, long newNextTick,
            double newPerInterval, double newBalance, State newState) {
        return new PersistentEffectContract(schema, effectId, ownerId, programHash,
                dimension, newRevision, startTick, naturalDeadlineTick,
                hardDeadlineTick, upkeepIntervalTicks, newNextTick,
                newPerInterval, newBalance, collapseSeed, newState, handles);
    }

    private static long effectiveDeadline(long natural, long hard) {
        return natural == NO_NATURAL_DEADLINE ? hard : natural;
    }

    private static String boundedText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(name + " is blank or too long");
        }
        return value;
    }

    private static void finiteBounded(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > MAX_UPKEEP) {
            throw new IllegalArgumentException(name + " must be finite and within 0.." + MAX_UPKEEP);
        }
    }
}
