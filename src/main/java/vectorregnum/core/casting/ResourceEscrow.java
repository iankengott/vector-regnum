package vectorregnum.core.casting;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable reservation and terminal settlement decision for one cast.
 *
 * <p>This class deliberately does not mutate a Minecraft inventory or mana
 * attachment. It is the loader-neutral transaction state an adapter retains
 * on its server-thread cast record: reserve the quoted amounts before starting
 * execution, then replace the value with {@link #settle(Outcome)}. The first
 * terminal outcome wins; settling an already terminal value returns that same
 * value, which makes retries and duplicate callbacks harmless.</p>
 */
public final class ResourceEscrow {
    /** All terminal outcomes that have an explicit consumption policy. */
    public enum Outcome {
        SUCCESS,
        GENUINE_SPELL_FAULT,
        POLICY_REJECTED,
        RATE_LIMITED,
        UNLOADED_TARGET,
        SHUTDOWN,
        OWNER_LIFECYCLE,
        ENGINE_FAILURE;

        /** Success and genuine spell faults consume committed resources. */
        public boolean consumesResources() {
            return this == SUCCESS || this == GENUINE_SPELL_FAULT;
        }

        /** Alias for settlement adapters that describe the decision as consumption. */
        public boolean consume() {
            return consumesResources();
        }
    }

    /** Immutable state of the reservation. */
    public enum State {
        RESERVED,
        CONSUMED,
        REFUNDED
    }

    private final CastQuote quote;
    private final double reservedMana;
    private final ReagentLoadout reservedReagents;
    private final boolean scrollReserved;
    private final State state;
    private final Outcome outcome;

    private ResourceEscrow(CastQuote quote, double reservedMana,
            ReagentLoadout reservedReagents, boolean scrollReserved,
            State state, Outcome outcome) {
        this.quote = quote;
        this.reservedMana = reservedMana;
        this.reservedReagents = reservedReagents;
        this.scrollReserved = scrollReserved;
        this.state = state;
        this.outcome = outcome;
    }

    /** Reserves the final mana quote, selected reagents, and a scroll when applicable. */
    public static ResourceEscrow reserve(CastQuote quote) {
        Objects.requireNonNull(quote, "quote");
        return reserve(quote, quote.method() == CastingMethod.SCROLL);
    }

    /**
     * Reserves a quote with an explicit physical-scroll flag.
     *
     * <p>The explicit overload is useful when an adapter has already validated
     * the media stack. A scroll may be represented as false for a non-portable
     * server invocation, but no non-scroll method may reserve a scroll.</p>
     */
    public static ResourceEscrow reserve(CastQuote quote, boolean scrollReserved) {
        Objects.requireNonNull(quote, "quote");
        if (scrollReserved && quote.method() != CastingMethod.SCROLL) {
            throw new IllegalArgumentException("only a scroll casting method can reserve a scroll");
        }
        return new ResourceEscrow(quote, quote.finalCost().mana(), quote.loadout(),
                scrollReserved, State.RESERVED, null);
    }

    public CastQuote quote() {
        return quote;
    }

    public State state() {
        return state;
    }

    public Optional<Outcome> outcome() {
        return Optional.ofNullable(outcome);
    }

    public boolean isReserved() {
        return state == State.RESERVED;
    }

    public boolean isConsumed() {
        return state == State.CONSUMED;
    }

    public boolean isRefunded() {
        return state == State.REFUNDED;
    }

    public boolean isTerminal() {
        return state != State.RESERVED;
    }

    /** Amount that the adapter must hold while execution is in flight. */
    public double reservedMana() {
        return reservedMana;
    }

    /** Exact selected reagent units that belong to this reservation. */
    public ReagentLoadout reservedReagents() {
        return reservedReagents;
    }

    public boolean scrollReserved() {
        return scrollReserved;
    }

    /** Mana consumed by a terminal consuming outcome, or zero after a refund. */
    public double manaConsumed() {
        return isConsumed() ? reservedMana : 0.0;
    }

    /** Reagents consumed by a terminal consuming outcome, or empty after a refund. */
    public ReagentLoadout reagentsConsumed() {
        return isConsumed() ? reservedReagents : ReagentLoadout.empty();
    }

    /** Whether the physical scroll must be removed at terminal settlement. */
    public boolean scrollConsumed() {
        return isConsumed() && scrollReserved;
    }

    /** Mana returned by a terminal refundable outcome, or zero after consumption. */
    public double manaRefunded() {
        return isRefunded() ? reservedMana : 0.0;
    }

    /** Reagents returned by a terminal refundable outcome, or empty after consumption. */
    public ReagentLoadout reagentsRefunded() {
        return isRefunded() ? reservedReagents : ReagentLoadout.empty();
    }

    /** Whether the physical scroll is retained by a terminal refundable outcome. */
    public boolean scrollRefunded() {
        return isRefunded() && scrollReserved;
    }

    /**
     * Settles this reservation. Terminal values are immutable and idempotent:
     * the first outcome wins, including if a later callback supplies a
     * conflicting outcome.
     */
    public ResourceEscrow settle(Outcome terminalOutcome) {
        Objects.requireNonNull(terminalOutcome, "terminalOutcome");
        if (isTerminal()) {
            return this;
        }
        State terminalState = terminalOutcome.consumesResources()
                ? State.CONSUMED : State.REFUNDED;
        return new ResourceEscrow(quote, reservedMana, reservedReagents, scrollReserved,
                terminalState, terminalOutcome);
    }

    /** Convenience terminal operation for a successful execution. */
    public ResourceEscrow consume() {
        return settle(Outcome.SUCCESS);
    }

    /** Convenience terminal operation for an explicit refundable outcome. */
    public ResourceEscrow refund(Outcome terminalOutcome) {
        if (Objects.requireNonNull(terminalOutcome, "terminalOutcome").consumesResources()) {
            throw new IllegalArgumentException(terminalOutcome + " is consuming, not refundable");
        }
        return settle(terminalOutcome);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof ResourceEscrow other)) return false;
        return Double.compare(reservedMana, other.reservedMana) == 0
                && scrollReserved == other.scrollReserved
                && state == other.state
                && outcome == other.outcome
                && quote.equals(other.quote)
                && reservedReagents.equals(other.reservedReagents);
    }

    @Override
    public int hashCode() {
        return Objects.hash(quote, reservedMana, reservedReagents, scrollReserved, state, outcome);
    }

    @Override
    public String toString() {
        return "ResourceEscrow[state=" + state + ", outcome=" + outcome
                + ", reservedMana=" + reservedMana + ", reservedReagents="
                + reservedReagents + ", scrollReserved=" + scrollReserved + "]";
    }
}
