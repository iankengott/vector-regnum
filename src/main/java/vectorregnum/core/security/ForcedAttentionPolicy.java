package vectorregnum.core.security;

/** Named facade for the bounded Averted Gaze-style mechanic. */
public final class ForcedAttentionPolicy {
    private ForcedAttentionPolicy() { }

    public static MechanicDecision evaluate(MechanicRequest request,
            double angleDegrees, double strength) {
        return MechanicSecurityPolicy.evaluateAttention(request, angleDegrees, strength);
    }
}
