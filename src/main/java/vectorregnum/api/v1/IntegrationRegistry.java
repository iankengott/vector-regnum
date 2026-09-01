package vectorregnum.api.v1;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.TreeMap;

/**
 * Bounded, thread-safe registration point for optional companion hooks.
 *
 * <p>Registration is protected only while the source-ID map changes. Each
 * callback operation snapshots the sorted registrations and invokes callbacks
 * after releasing the lock. A callback may therefore close itself, register a
 * replacement after close, or call another registry operation without a lock
 * inversion. Failing callbacks are isolated from Vector-Regnum and siblings.</p>
 */
public final class IntegrationRegistry {
    public static final int MAX_NATURAL_ELEMENT_PROVIDERS = 8;
    public static final int MAX_CAST_MODIFIER_PROVIDERS = 8;
    public static final int MAX_STORY_LISTENERS = 8;
    public static final int MAX_SOURCE_ID_LENGTH = 128;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final TreeMap<String, NaturalElementProvider> naturalElementProviders = new TreeMap<>();
    private final TreeMap<String, CastModifierProvider> castModifierProviders = new TreeMap<>();
    private final TreeMap<String, StoryEventListener> storyListeners = new TreeMap<>();

    /** Creates an isolated registry; the public singleton lives on VectorRegnumApiV1. */
    public IntegrationRegistry() {
    }

    public RegistrationHandle registerNaturalElementProvider(String sourceId,
            NaturalElementProvider provider) {
        return register(sourceId, provider, naturalElementProviders,
                MAX_NATURAL_ELEMENT_PROVIDERS);
    }

    public RegistrationHandle registerCastModifierProvider(String sourceId,
            CastModifierProvider provider) {
        return register(sourceId, provider, castModifierProviders,
                MAX_CAST_MODIFIER_PROVIDERS);
    }

    public RegistrationHandle registerStoryListener(String sourceId, StoryEventListener listener) {
        return register(sourceId, listener, storyListeners, MAX_STORY_LISTENERS);
    }

    /** Runs natural-element providers in source-ID order and returns the first canonical answer. */
    public Optional<String> naturalElement(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        List<NaturalElementProvider> providers = snapshot(naturalElementProviders);
        for (NaturalElementProvider provider : providers) {
            try {
                String element = ApiValidation.naturalElementOrNull(provider.naturalElement(playerId));
                if (element != null) {
                    return Optional.of(element);
                }
            } catch (RuntimeException ignored) {
                // An optional companion cannot take down the authoritative path.
            }
        }
        return Optional.empty();
    }

    /** Composes all cast modifiers in source-ID order, isolating null and throwing providers. */
    public CastModifier castModifier(CastContext context) {
        Objects.requireNonNull(context, "context");
        List<CastModifierProvider> providers = snapshot(castModifierProviders);
        double mana = 1.0;
        double castingTime = 1.0;
        double upkeep = 1.0;
        double instability = 1.0;
        for (CastModifierProvider provider : providers) {
            try {
                CastModifier contribution = provider.modifier(context);
                if (contribution != null) {
                    mana *= contribution.manaFactor();
                    castingTime *= contribution.castingTimeFactor();
                    upkeep *= contribution.upkeepFactor();
                    instability *= contribution.instabilityFactor();
                }
            } catch (RuntimeException ignored) {
                // Continue with the identity/contributions already accepted.
            }
        }
        return new CastModifier(ApiValidation.clampFactor(mana),
                ApiValidation.clampFactor(castingTime), ApiValidation.clampFactor(upkeep),
                ApiValidation.clampFactor(instability));
    }

    /** Delivers an immutable event to sorted listeners, isolating each callback failure. */
    public void publishStoryEvent(StoryEvent event) {
        Objects.requireNonNull(event, "event");
        List<StoryEventListener> listeners = snapshot(storyListeners);
        for (StoryEventListener listener : listeners) {
            try {
                listener.onStoryEvent(event);
            } catch (RuntimeException ignored) {
                // One optional listener must not suppress the remaining listeners.
            }
        }
    }

    public int naturalElementProviderCount() {
        return count(naturalElementProviders);
    }

    public int castModifierProviderCount() {
        return count(castModifierProviders);
    }

    public int storyListenerCount() {
        return count(storyListeners);
    }

    private <T> RegistrationHandle register(String sourceId, T callback,
            TreeMap<String, T> registrations, int maximum) {
        String validatedSourceId = ApiValidation.sourceId(sourceId);
        Objects.requireNonNull(callback, "callback");
        lock.writeLock().lock();
        try {
            if (registrations.containsKey(validatedSourceId)) {
                throw new IllegalArgumentException("duplicate registration source ID: "
                        + validatedSourceId);
            }
            if (registrations.size() >= maximum) {
                throw new IllegalStateException("registration limit reached for source ID "
                        + validatedSourceId);
            }
            registrations.put(validatedSourceId, callback);
            return new Handle<>(this, validatedSourceId, callback, registrations);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private <T> void unregister(String sourceId, T callback, TreeMap<String, T> registrations) {
        lock.writeLock().lock();
        try {
            if (registrations.get(sourceId) == callback) {
                registrations.remove(sourceId);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private <T> List<T> snapshot(TreeMap<String, T> registrations) {
        lock.readLock().lock();
        try {
            return List.copyOf(registrations.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    private <T> int count(TreeMap<String, T> registrations) {
        lock.readLock().lock();
        try {
            return registrations.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @FunctionalInterface
    public interface NaturalElementProvider {
        String naturalElement(UUID playerId);
    }

    @FunctionalInterface
    public interface CastModifierProvider {
        CastModifier modifier(CastContext context);
    }

    @FunctionalInterface
    public interface StoryEventListener {
        void onStoryEvent(StoryEvent event);
    }

    /** A close-once registration token. */
    public interface RegistrationHandle extends AutoCloseable {
        @Override
        void close();

        boolean isClosed();

        String sourceId();
    }

    private static final class Handle<T> implements RegistrationHandle {
        private final IntegrationRegistry owner;
        private final String sourceId;
        private final T callback;
        private final TreeMap<String, T> registrations;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Handle(IntegrationRegistry owner, String sourceId, T callback,
                TreeMap<String, T> registrations) {
            this.owner = owner;
            this.sourceId = sourceId;
            this.callback = callback;
            this.registrations = registrations;
        }

        @Override
        public String sourceId() {
            return sourceId;
        }

        @Override
        public boolean isClosed() {
            return closed.get();
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                owner.unregister(sourceId, callback, registrations);
            }
        }
    }
}
