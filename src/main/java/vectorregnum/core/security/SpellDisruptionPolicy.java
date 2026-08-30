package vectorregnum.core.security;

/** Named facade for callers implementing a reverse-unwriting attack. */
public final class SpellDisruptionPolicy {
    private SpellDisruptionPolicy() { }

    public static MechanicDecision evaluate(MechanicRequest request, boolean activeSpell,
            boolean stanceReady, boolean weaponReady, int windowTicks) {
        return MechanicSecurityPolicy.evaluateDisruption(request, activeSpell,
                stanceReady, weaponReady, windowTicks);
    }
}
