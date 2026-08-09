package vectorregnum.core;

import java.util.Objects;

/** Dynamic, server-authoritative inputs supplied for one cast. */
public record CastContext(String casterId, Vec3 origin, Vec3 lookDirection, long randomSeed) {
    public CastContext {
        Objects.requireNonNull(casterId, "casterId");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(lookDirection, "lookDirection");
        if (casterId.isBlank()) {
            throw new IllegalArgumentException("casterId cannot be blank");
        }
    }
}
