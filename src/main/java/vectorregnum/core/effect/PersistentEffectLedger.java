package vectorregnum.core.effect;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable bounded state machine for persistent-effect upkeep and cleanup. */
public final class PersistentEffectLedger {
    public static final int MAX_WORLD_EFFECTS = 1_024;
    public static final PersistentEffectLedger EMPTY = new PersistentEffectLedger(Map.of());
    private static final double EPSILON = 1.0e-9;

    public enum Decision {
        MISSING,
        WAITING_UNLOADED,
        ACTIVE,
        UPKEEP_PAID,
        NATURAL_CONCLUSION,
        CONCLUSION_PENDING_CLEANUP,
        COLLAPSE_UNPAID,
        COLLAPSE_HARD_CAP,
        COLLAPSE_PENDING_EMISSION,
        COLLAPSE_PENDING_CLEANUP,
        CLEANUP_PENDING_REMOVAL
    }

    public record Change(PersistentEffectLedger ledger, boolean changed) {
    }

    public record Reconciliation(PersistentEffectLedger ledger, Decision decision,
            double upkeepDebited, PersistentEffectContract contract) {
        public Reconciliation {
            Objects.requireNonNull(ledger, "ledger");
            Objects.requireNonNull(decision, "decision");
        }
    }

    private final Map<UUID, PersistentEffectContract> entries;

    public PersistentEffectLedger(Map<UUID, PersistentEffectContract> entries) {
        Objects.requireNonNull(entries, "entries");
        if (entries.size() > MAX_WORLD_EFFECTS) {
            throw new IllegalArgumentException("persistent-effect world cap exceeded");
        }
        LinkedHashMap<UUID, PersistentEffectContract> copy = new LinkedHashMap<>();
        entries.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            UUID id = Objects.requireNonNull(entry.getKey(), "effect id");
            PersistentEffectContract contract = Objects.requireNonNull(entry.getValue(), "effect contract");
            if (!id.equals(contract.effectId())) {
                throw new IllegalArgumentException("persistent-effect key does not match its contract id");
            }
            copy.put(id, contract);
        });
        this.entries = Map.copyOf(copy);
    }

    public Map<UUID, PersistentEffectContract> entries() {
        return entries;
    }

    public PersistentEffectContract get(UUID id) {
        return entries.get(Objects.requireNonNull(id, "id"));
    }

    public List<PersistentEffectContract> forOwner(UUID owner) {
        Objects.requireNonNull(owner, "owner");
        return entries.values().stream().filter(entry -> entry.ownerId().equals(owner))
                .sorted(java.util.Comparator.comparing(PersistentEffectContract::effectId)).toList();
    }

    public Change register(PersistentEffectContract contract) {
        Objects.requireNonNull(contract, "contract");
        PersistentEffectContract existing = entries.get(contract.effectId());
        if (contract.equals(existing)) return new Change(this, false);
        if (existing != null) {
            throw new IllegalArgumentException("persistent-effect id already belongs to another contract");
        }
        if (entries.size() >= MAX_WORLD_EFFECTS) {
            throw new IllegalStateException("persistent-effect world cap reached");
        }
        return replaceInternal(contract, true);
    }

    public Change replace(long expectedRevision, PersistentEffectContract contract) {
        Objects.requireNonNull(contract, "contract");
        PersistentEffectContract existing = entries.get(contract.effectId());
        if (existing == null || existing.revision() != expectedRevision) {
            return new Change(this, false);
        }
        if (existing.equals(contract)) return new Change(this, false);
        return replaceInternal(contract, false);
    }

    public Change removeCleaned(UUID id) {
        PersistentEffectContract existing = entries.get(Objects.requireNonNull(id, "id"));
        if (existing == null) return new Change(this, false);
        if (existing.state() != PersistentEffectContract.State.CLEANED) {
            throw new IllegalStateException("only cleaned persistent effects may leave the ledger");
        }
        LinkedHashMap<UUID, PersistentEffectContract> changed = new LinkedHashMap<>(entries);
        changed.remove(id);
        return new Change(changed.isEmpty() ? EMPTY : new PersistentEffectLedger(changed), true);
    }

    public Reconciliation reconcile(UUID id, long now, boolean loaded) {
        if (now < 0L) throw new IllegalArgumentException("server tick cannot be negative");
        PersistentEffectContract contract = entries.get(Objects.requireNonNull(id, "id"));
        if (contract == null) return new Reconciliation(this, Decision.MISSING, 0.0, null);
        if (!loaded) {
            return new Reconciliation(this, Decision.WAITING_UNLOADED, 0.0, contract);
        }
        if (contract.state() == PersistentEffectContract.State.COLLAPSED) {
            return new Reconciliation(this, Decision.COLLAPSE_PENDING_EMISSION, 0.0, contract);
        }
        if (contract.state() == PersistentEffectContract.State.COLLAPSE_EMITTED) {
            return new Reconciliation(this, Decision.COLLAPSE_PENDING_CLEANUP, 0.0, contract);
        }
        if (contract.state() == PersistentEffectContract.State.CONCLUDING) {
            if (now >= contract.hardDeadlineTick()) {
                return transition(contract,
                        contract.withState(PersistentEffectContract.State.COLLAPSED),
                        Decision.COLLAPSE_HARD_CAP, 0.0);
            }
            return new Reconciliation(this, Decision.CONCLUSION_PENDING_CLEANUP, 0.0, contract);
        }
        if (contract.state() == PersistentEffectContract.State.CLEANED) {
            return new Reconciliation(this, Decision.CLEANUP_PENDING_REMOVAL, 0.0, contract);
        }
        if (contract.hasNaturalDeadline() && now >= contract.naturalDeadlineTick()) {
            return transition(contract, contract.withState(PersistentEffectContract.State.CONCLUDING),
                    Decision.NATURAL_CONCLUSION, 0.0);
        }
        if (now >= contract.hardDeadlineTick()) {
            return transition(contract, contract.withState(PersistentEffectContract.State.COLLAPSED),
                    Decision.COLLAPSE_HARD_CAP, 0.0);
        }
        if (now < contract.nextUpkeepTick() || contract.upkeepPerInterval() <= EPSILON) {
            return new Reconciliation(this, Decision.ACTIVE, 0.0, contract);
        }

        long overdue = now - contract.nextUpkeepTick();
        long installments = overdue / contract.upkeepIntervalTicks() + 1L;
        double debit = contract.upkeepPerInterval() * installments;
        if (!Double.isFinite(debit) || debit > contract.prepaidUpkeep() + EPSILON) {
            return transition(contract, contract.withState(PersistentEffectContract.State.COLLAPSED),
                    Decision.COLLAPSE_UNPAID, 0.0);
        }
        long next;
        try {
            next = Math.addExact(contract.nextUpkeepTick(),
                    Math.multiplyExact(installments, (long) contract.upkeepIntervalTicks()));
        } catch (ArithmeticException exception) {
            return transition(contract, contract.withState(PersistentEffectContract.State.COLLAPSED),
                    Decision.COLLAPSE_HARD_CAP, 0.0);
        }
        next = Math.min(next, contract.hardDeadlineTick());
        PersistentEffectContract paid = contract.withUpkeep(
                Math.max(0.0, contract.prepaidUpkeep() - debit), next);
        return transition(contract, paid, Decision.UPKEEP_PAID, debit);
    }

    public Change completeCleanup(UUID id) {
        PersistentEffectContract existing = entries.get(Objects.requireNonNull(id, "id"));
        if (existing == null || existing.state() == PersistentEffectContract.State.CLEANED) {
            return new Change(this, false);
        }
        return replace(existing.revision(), existing.withState(PersistentEffectContract.State.CLEANED));
    }

    public Change completeCollapseEmission(UUID id) {
        PersistentEffectContract existing = entries.get(Objects.requireNonNull(id, "id"));
        if (existing == null || existing.state() == PersistentEffectContract.State.COLLAPSE_EMITTED) {
            return new Change(this, false);
        }
        if (existing.state() != PersistentEffectContract.State.COLLAPSED) {
            throw new IllegalStateException("only collapsed persistent effects may record emission");
        }
        return replace(existing.revision(),
                existing.withState(PersistentEffectContract.State.COLLAPSE_EMITTED));
    }

    private Reconciliation transition(PersistentEffectContract before,
            PersistentEffectContract after, Decision decision, double debited) {
        Change change = replace(before.revision(), after);
        return new Reconciliation(change.ledger(), decision, debited,
                change.ledger().get(after.effectId()));
    }

    private Change replaceInternal(PersistentEffectContract contract, boolean addition) {
        LinkedHashMap<UUID, PersistentEffectContract> changed = new LinkedHashMap<>(entries);
        changed.put(contract.effectId(), contract);
        PersistentEffectLedger replacement = new PersistentEffectLedger(changed);
        return new Change(replacement, addition || !replacement.equals(this));
    }

    @Override
    public boolean equals(Object object) {
        return this == object || object instanceof PersistentEffectLedger other
                && entries.equals(other.entries);
    }

    @Override
    public int hashCode() {
        return entries.hashCode();
    }
}
