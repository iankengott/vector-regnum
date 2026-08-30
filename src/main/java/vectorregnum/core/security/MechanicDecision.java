package vectorregnum.core.security;

import java.util.Objects;

/** Explicit fail-closed authorization result. */
public record MechanicDecision(boolean allowed, Code code, String reason) {
    public MechanicDecision {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(reason, "reason");
        if (reason.isBlank()) throw new IllegalArgumentException("reason cannot be blank");
        if (allowed && code != Code.ALLOWED) {
            throw new IllegalArgumentException("allowed decisions must use ALLOWED");
        }
        if (!allowed && code == Code.ALLOWED) {
            throw new IllegalArgumentException("rejected decisions cannot use ALLOWED");
        }
    }

    public static MechanicDecision allow() {
        return new MechanicDecision(true, Code.ALLOWED, "allowed");
    }

    public static MechanicDecision reject(Code code, String reason) {
        if (code == Code.ALLOWED) throw new IllegalArgumentException("rejection code required");
        return new MechanicDecision(false, code, reason);
    }

    public enum Code {
        ALLOWED,
        RANGE_EXCEEDED,
        DURATION_EXCEEDED,
        TARGET_LIMIT_EXCEEDED,
        DIMENSION_MISMATCH,
        SOURCE_UNLOADED,
        TARGET_UNLOADED,
        PERMISSION_DENIED,
        PVP_DENIED,
        FRIENDLY_FIRE_DENIED,
        NON_DETERMINISTIC,
        STANCE_REQUIRED,
        WEAPON_REQUIRED,
        NO_ACTIVE_SPELL,
        WINDOW_CLOSED,
        INVALID_ANGLE,
        INVALID_STRENGTH
    }
}
