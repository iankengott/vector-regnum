package vectorregnum.api.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class IntegrationRegistryTest {
    private static final UUID PLAYER = UUID.randomUUID();

    @Test
    void naturalProvidersUseSourceOrderAndFirstCanonicalAnswer() {
        IntegrationRegistry registry = new IntegrationRegistry();
        List<String> calls = new ArrayList<>();
        registry.registerNaturalElementProvider("zeta:origin", id -> {
            calls.add("zeta");
            return "fire";
        });
        registry.registerNaturalElementProvider("alpha:origin", id -> {
            calls.add("alpha");
            return "frost";
        });

        assertEquals(java.util.Optional.of("ice"), registry.naturalElement(PLAYER));
        assertEquals(List.of("alpha"), calls);
    }

    @Test
    void duplicateAndOverflowRegistrationsFailAndCloseIsIdempotent() {
        IntegrationRegistry registry = new IntegrationRegistry();
        List<IntegrationRegistry.RegistrationHandle> handles = new ArrayList<>();
        for (int index = 0; index < IntegrationRegistry.MAX_NATURAL_ELEMENT_PROVIDERS; index++) {
            handles.add(registry.registerNaturalElementProvider("test:" + index, id -> "water"));
        }
        assertThrows(IllegalStateException.class,
                () -> registry.registerNaturalElementProvider("test:overflow", id -> "water"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.registerNaturalElementProvider("test:0", id -> "water"));
        assertEquals(IntegrationRegistry.MAX_NATURAL_ELEMENT_PROVIDERS,
                registry.naturalElementProviderCount());

        IntegrationRegistry.RegistrationHandle first = handles.get(0);
        assertFalse(first.isClosed());
        first.close();
        first.close();
        assertTrue(first.isClosed());
        assertEquals(IntegrationRegistry.MAX_NATURAL_ELEMENT_PROVIDERS - 1,
                registry.naturalElementProviderCount());
        registry.registerNaturalElementProvider("test:0", id -> "ice");
    }

    @Test
    void castProvidersAreSortedAndExceptionsDoNotAbortComposition() {
        IntegrationRegistry registry = new IntegrationRegistry();
        List<String> calls = new ArrayList<>();
        CastContext context = new CastContext(PLAYER, "vector_regnum:test", "fire", "bare",
                new CastParameters(10.0, 20.0, 4.0, 2.0), 42L);
        registry.registerCastModifierProvider("zeta:modifier", ignored -> {
            calls.add("zeta");
            return new CastModifier(2.0, 2.0, 2.0, 2.0);
        });
        registry.registerCastModifierProvider("beta:failing", ignored -> {
            calls.add("beta");
            throw new IllegalStateException("optional provider failed");
        });
        registry.registerCastModifierProvider("alpha:modifier", ignored -> {
            calls.add("alpha");
            return new CastModifier(0.5, 0.5, 0.5, 0.5);
        });

        CastModifier aggregate = registry.castModifier(context);
        assertEquals(List.of("alpha", "beta", "zeta"), calls);
        assertEquals(1.0, aggregate.manaFactor());
        assertEquals(1.0, aggregate.castingTimeFactor());
        assertEquals(1.0, aggregate.upkeepFactor());
        assertEquals(1.0, aggregate.instabilityFactor());
    }

    @Test
    void callbacksRunOutsideRegistryLockAndListenerFailuresAreIsolated() {
        IntegrationRegistry registry = new IntegrationRegistry();
        AtomicBoolean callbackCompleted = new AtomicBoolean();
        AtomicInteger delivered = new AtomicInteger();
        IntegrationRegistry.RegistrationHandle[] self = new IntegrationRegistry.RegistrationHandle[1];
        self[0] = registry.registerStoryListener("test:self", event -> {
            self[0].close();
            registry.registerStoryListener("test:replacement", ignored -> delivered.incrementAndGet());
            callbackCompleted.set(true);
        });
        registry.registerStoryListener("test:failing", event -> {
            throw new IllegalStateException("listener failed");
        });
        registry.registerStoryListener("test:later", event -> delivered.incrementAndGet());

        StoryEvent event = new StoryEvent(UUID.randomUUID(), 1L, "vector_regnum:cast", 12L,
                PLAYER, "minecraft:overworld", 1, 2, 3,
                "vector_regnum:test", "fire", "started");
        registry.publishStoryEvent(event);

        assertTrue(callbackCompleted.get());
        assertEquals(1, delivered.get());
        assertEquals(3, registry.storyListenerCount());
    }

    @Test
    void invalidSourceIdsAreRejected() {
        IntegrationRegistry registry = new IntegrationRegistry();
        assertThrows(IllegalArgumentException.class,
                () -> registry.registerStoryListener("not-namespaced", event -> { }));
        String tooLong = "test:" + "a".repeat(IntegrationRegistry.MAX_SOURCE_ID_LENGTH);
        assertThrows(IllegalArgumentException.class,
                () -> registry.registerStoryListener(tooLong, event -> { }));
    }
}
