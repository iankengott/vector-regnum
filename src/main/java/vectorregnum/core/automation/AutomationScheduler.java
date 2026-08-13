package vectorregnum.core.automation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Bounded many-producer/single-consumer handoff for programmable automation.
 *
 * <p>Any thread may submit immutable invocations. Exactly one server thread may
 * drain them; the first drain claims ownership and a drain from another thread
 * fails closed. The consumer is therefore the sole owner of Minecraft access,
 * VM creation, relay mutation, and mana mutation. Per-owner pending limits stop
 * one automation network from monopolizing the global queue.</p>
 */
public final class AutomationScheduler {
    public static final int DEFAULT_GLOBAL_PENDING = 1_024;
    public static final int DEFAULT_OWNER_PENDING = 32;
    public static final int DEFAULT_PER_TICK = 16;

    public enum SubmitResult { ACCEPTED, OWNER_LIMIT, GLOBAL_LIMIT }

    private final int globalLimit;
    private final int ownerLimit;
    private final int perTickLimit;
    private final ConcurrentLinkedQueue<Envelope> pending = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<UUID, AtomicInteger> ownerPending = new ConcurrentHashMap<>();
    private final AtomicInteger pendingCount = new AtomicInteger();
    private final AtomicLong nextSequence = new AtomicLong();
    private volatile Thread consumerThread;

    public AutomationScheduler() {
        this(DEFAULT_GLOBAL_PENDING, DEFAULT_OWNER_PENDING, DEFAULT_PER_TICK);
    }

    public AutomationScheduler(int globalLimit, int ownerLimit, int perTickLimit) {
        if (globalLimit < 1 || ownerLimit < 1 || perTickLimit < 1 || ownerLimit > globalLimit) {
            throw new IllegalArgumentException("invalid automation scheduler limits");
        }
        this.globalLimit = globalLimit;
        this.ownerLimit = ownerLimit;
        this.perTickLimit = perTickLimit;
    }

    public synchronized SubmitResult submit(AutomationInvocation invocation) {
        if (invocation == null) throw new NullPointerException("invocation");
        AtomicInteger ownerCount = ownerPending.computeIfAbsent(invocation.owner(),
                ignored -> new AtomicInteger());
        if (!reserve(ownerCount, ownerLimit)) return SubmitResult.OWNER_LIMIT;
        if (!reserve(pendingCount, globalLimit)) {
            release(invocation.owner(), ownerCount);
            return SubmitResult.GLOBAL_LIMIT;
        }
        pending.add(new Envelope(nextSequence.getAndIncrement(), invocation));
        return SubmitResult.ACCEPTED;
    }

    /** Drains a deterministic sequence-ordered batch on the one owning thread. */
    public int drain(Consumer<AutomationInvocation> consumer) {
        if (consumer == null) throw new NullPointerException("consumer");
        assertConsumerThread();
        List<Envelope> batch = takeBatch();
        batch.sort(Comparator.comparingLong(Envelope::sequence));
        for (Envelope envelope : batch) {
            consumer.accept(envelope.invocation());
        }
        return batch.size();
    }

    private synchronized List<Envelope> takeBatch() {
        List<Envelope> batch = new ArrayList<>(perTickLimit);
        for (int i = 0; i < perTickLimit; i++) {
            Envelope envelope = pending.poll();
            if (envelope == null) break;
            batch.add(envelope);
            pendingCount.decrementAndGet();
            AtomicInteger ownerCount = ownerPending.get(envelope.invocation().owner());
            if (ownerCount != null) release(envelope.invocation().owner(), ownerCount);
        }
        return batch;
    }

    public int pendingCount() {
        return pendingCount.get();
    }

    public int pendingFor(UUID owner) {
        AtomicInteger value = ownerPending.get(owner);
        return value == null ? 0 : value.get();
    }

    /** Server stop owns cancellation; queued values have no external cleanup callbacks. */
    public synchronized void clear() {
        assertConsumerThread();
        pending.clear();
        ownerPending.clear();
        pendingCount.set(0);
        consumerThread = null;
    }

    private void assertConsumerThread() {
        Thread current = Thread.currentThread();
        Thread claimed = consumerThread;
        if (claimed == null) {
            synchronized (this) {
                if (consumerThread == null) consumerThread = current;
                claimed = consumerThread;
            }
        }
        if (claimed != current) {
            throw new IllegalStateException("automation queue may only be drained by its server owner thread");
        }
    }

    private static boolean reserve(AtomicInteger count, int limit) {
        while (true) {
            int current = count.get();
            if (current >= limit) return false;
            if (count.compareAndSet(current, current + 1)) return true;
        }
    }

    private void release(UUID owner, AtomicInteger count) {
        int remaining = count.decrementAndGet();
        if (remaining == 0) ownerPending.remove(owner, count);
    }

    private record Envelope(long sequence, AutomationInvocation invocation) {
    }
}
