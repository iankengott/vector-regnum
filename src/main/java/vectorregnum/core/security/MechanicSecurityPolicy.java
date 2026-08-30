package vectorregnum.core.security;

/** Pure shared policy for recovered mechanics. No loader or world dependency. */
public final class MechanicSecurityPolicy {
    private MechanicSecurityPolicy() { }

    public static MechanicDecision evaluate(MechanicRequest request) {
        if (request.range() > MechanicLimits.MAX_RANGE) {
            return MechanicDecision.reject(MechanicDecision.Code.RANGE_EXCEEDED,
                    "mechanic range exceeds the 32-block bound");
        }
        if (request.durationTicks() > MechanicLimits.MAX_DURATION_TICKS) {
            return MechanicDecision.reject(MechanicDecision.Code.DURATION_EXCEEDED,
                    "mechanic duration exceeds the 1,200-tick bound");
        }
        if (request.targetCount() > MechanicLimits.MAX_TARGETS) {
            return MechanicDecision.reject(MechanicDecision.Code.TARGET_LIMIT_EXCEEDED,
                    "mechanic target count exceeds the 16-target bound");
        }
        if (!request.sameDimension()) {
            return MechanicDecision.reject(MechanicDecision.Code.DIMENSION_MISMATCH,
                    "mechanic targets must share the caster dimension");
        }
        if (!request.sourceLoaded()) {
            return MechanicDecision.reject(MechanicDecision.Code.SOURCE_UNLOADED,
                    "mechanic source chunk is unloaded");
        }
        if (!request.targetLoaded()) {
            return MechanicDecision.reject(MechanicDecision.Code.TARGET_UNLOADED,
                    "mechanic target chunk is unloaded");
        }
        if (!request.permissionAllowed()) {
            return MechanicDecision.reject(MechanicDecision.Code.PERMISSION_DENIED,
                    "claims or gameplay permissions deny this mechanic");
        }
        if (request.capability().gameplay() && !request.pvpAllowed()) {
            return MechanicDecision.reject(MechanicDecision.Code.PVP_DENIED,
                    "server PvP policy denies this gameplay mechanic");
        }
        if (request.capability().gameplay() && !request.friendlyFireAllowed()) {
            return MechanicDecision.reject(MechanicDecision.Code.FRIENDLY_FIRE_DENIED,
                    "team friendly-fire policy denies this gameplay mechanic");
        }
        if (request.capability() == MechanicCapability.WILD_MAGIC && !request.deterministic()) {
            return MechanicDecision.reject(MechanicDecision.Code.NON_DETERMINISTIC,
                    "Wild Magic must carry a deterministic seed");
        }
        return MechanicDecision.allow();
    }

    public static MechanicDecision evaluateDisruption(MechanicRequest base,
            boolean activeSpell, boolean stanceReady, boolean weaponReady,
            int windowTicks) {
        MechanicDecision common = evaluate(base);
        if (!common.allowed()) return common;
        if (base.capability() != MechanicCapability.SPELL_DISRUPTION) {
            return MechanicDecision.reject(MechanicDecision.Code.PERMISSION_DENIED,
                    "request is not a spell-disruption capability");
        }
        if (!activeSpell) return MechanicDecision.reject(MechanicDecision.Code.NO_ACTIVE_SPELL,
                "target has no active spell");
        if (!stanceReady) return MechanicDecision.reject(MechanicDecision.Code.STANCE_REQUIRED,
                "a disruption stance is required");
        if (!weaponReady) return MechanicDecision.reject(MechanicDecision.Code.WEAPON_REQUIRED,
                "a disruption weapon enchantment is required");
        if (windowTicks < 0 || windowTicks > MechanicLimits.MAX_DISRUPTION_WINDOW_TICKS) {
            return MechanicDecision.reject(MechanicDecision.Code.WINDOW_CLOSED,
                    "the disruption timing window is closed");
        }
        return MechanicDecision.allow();
    }

    public static MechanicDecision evaluateAttention(MechanicRequest base,
            double angleDegrees, double strength) {
        MechanicDecision common = evaluate(base);
        if (!common.allowed()) return common;
        if (base.capability() != MechanicCapability.FORCED_ATTENTION) {
            return MechanicDecision.reject(MechanicDecision.Code.PERMISSION_DENIED,
                    "request is not a forced-attention capability");
        }
        if (!Double.isFinite(angleDegrees) || angleDegrees <= 0.0
                || angleDegrees > MechanicLimits.MAX_ATTENTION_ANGLE_DEGREES) {
            return MechanicDecision.reject(MechanicDecision.Code.INVALID_ANGLE,
                    "attention cone must be within 0..45 degrees");
        }
        if (!Double.isFinite(strength) || strength <= 0.0 || strength > 1.0) {
            return MechanicDecision.reject(MechanicDecision.Code.INVALID_STRENGTH,
                    "attention strength must be within 0..1");
        }
        if (base.durationTicks() > MechanicLimits.MAX_ATTENTION_DURATION_TICKS) {
            return MechanicDecision.reject(MechanicDecision.Code.DURATION_EXCEEDED,
                    "forced attention duration exceeds the 40-tick bound");
        }
        return MechanicDecision.allow();
    }
}
