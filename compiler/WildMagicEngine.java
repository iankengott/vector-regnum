package vectorregnum.compiler;

public class WildMagicEngine {
    
    /**
     * Determines the chaotic consequence of a broken spell sequence based on the current state
     * and how far the compilation/execution got before failing.
     */
    public static void detonate(SpellState state) {
        System.out.println("\n[WILD MAGIC TRIGGERED]");
        System.out.println("Failure reason: " + state.breakReason);
        System.out.println("Failed at Instruction Index: " + state.breakIndex);
        
        // Context-aware mutations based on how far the spell got
        
        if (state.origin == null) {
            // Early failure: Spell hadn't even grounded itself in reality yet.
            System.out.println("-> EFFECT: Internal Mana Detonation.");
            System.out.println("-> Caster suffers raw magic damage and heavy Mana Burn debuff.");
            // TODO: Apply direct damage to caster and potion effect
        } 
        else if (state.shape == null) {
            // Mid failure: Spell had an origin and possibly an element, but no structure.
            String elementOutput = (state.element != null) ? state.element.toUpperCase() : "RAW MANA";
            System.out.println("-> EFFECT: Unstructured Element Burst.");
            System.out.println("-> A massive, untargeted burst of " + elementOutput + " explodes at " + state.origin);
            // TODO: Spawn explosion or elemental cloud at state.origin
        } 
        else {
            // Late failure: The spell was structurally sound but failed during manipulation or execution.
            System.out.println("-> EFFECT: Violent Miscast.");
            System.out.println("-> The " + state.shape + " fires uncontrollably in a random direction with amplified instability.");
            // TODO: Randomize direction vector, spawn projectile with "unstable" visual effects
        }
        
        System.out.println("Caster " + state.caster.getName() + " is temporarily unable to channel mana.\n");
    }
}
