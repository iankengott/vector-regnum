package vectorregnum.core.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AutomationSchedulerTest {
    private static final AutomationEndpoint ENDPOINT =
            new AutomationEndpoint("minecraft:overworld", 1, 2, 3);

    @Test
    void queueEnforcesPerOwnerGlobalAndPerTickBounds() {
        AutomationScheduler scheduler = new AutomationScheduler(3, 2, 1);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertEquals(AutomationScheduler.SubmitResult.ACCEPTED, scheduler.submit(invocation(first, 1)));
        assertEquals(AutomationScheduler.SubmitResult.ACCEPTED, scheduler.submit(invocation(first, 2)));
        assertEquals(AutomationScheduler.SubmitResult.OWNER_LIMIT, scheduler.submit(invocation(first, 3)));
        assertEquals(AutomationScheduler.SubmitResult.ACCEPTED, scheduler.submit(invocation(second, 4)));
        assertEquals(AutomationScheduler.SubmitResult.GLOBAL_LIMIT, scheduler.submit(invocation(second, 5)));

        List<Long> delivered = new ArrayList<>();
        assertEquals(1, scheduler.drain(value -> delivered.add(value.data().worldTick())));
        assertEquals(List.of(1L), delivered);
        assertEquals(2, scheduler.pendingCount());
        assertEquals(1, scheduler.pendingFor(first));
    }

    @Test
    void sequentialSubmissionsDrainInStableOrder() {
        AutomationScheduler scheduler = new AutomationScheduler(8, 8, 8);
        UUID owner = UUID.randomUUID();
        scheduler.submit(invocation(owner, 9));
        scheduler.submit(invocation(owner, 3));
        scheduler.submit(invocation(owner, 7));
        List<Long> delivered = new ArrayList<>();
        scheduler.drain(value -> delivered.add(value.data().worldTick()));
        assertEquals(List.of(9L, 3L, 7L), delivered);
    }

    @Test
    void manyProducerSubmissionHandsImmutableMessagesToSingleConsumer() throws Exception {
        AutomationScheduler scheduler = new AutomationScheduler(64, 32, 64);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Thread a = producer(scheduler, first, 0, ready, go);
        Thread b = producer(scheduler, second, 100, ready, go);
        a.start();
        b.start();
        ready.await();
        go.countDown();
        a.join();
        b.join();

        List<AutomationInvocation> delivered = new ArrayList<>();
        assertEquals(32, scheduler.drain(delivered::add));
        assertEquals(32, delivered.size());
        assertEquals(0, scheduler.pendingCount());
        assertEquals(16, delivered.stream().filter(value -> value.owner().equals(first)).count());
        assertEquals(16, delivered.stream().filter(value -> value.owner().equals(second)).count());
    }

    @Test
    void secondConsumerThreadCannotClaimMinecraftOwnership() throws Exception {
        AutomationScheduler scheduler = new AutomationScheduler(4, 4, 4);
        scheduler.drain(ignored -> { });
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread intruder = new Thread(() -> {
            try {
                scheduler.drain(ignored -> { });
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        });
        intruder.start();
        intruder.join();
        assertEquals(IllegalStateException.class, failure.get().getClass());
    }

    private static Thread producer(AutomationScheduler scheduler, UUID owner, long start,
            CountDownLatch ready, CountDownLatch go) {
        return new Thread(() -> {
            ready.countDown();
            try {
                go.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(exception);
            }
            for (int i = 0; i < 16; i++) {
                assertEquals(AutomationScheduler.SubmitResult.ACCEPTED,
                        scheduler.submit(invocation(owner, start + i)));
            }
        });
    }

    private static AutomationInvocation invocation(UUID owner, long tick) {
        return new AutomationInvocation(owner, ENDPOINT,
                AutomationInvocation.TriggerCause.DATA_BRIDGE,
                new AutomationDataFrame(0, tick, Map.of("test.value", tick)));
    }
}
