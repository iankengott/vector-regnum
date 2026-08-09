package vectorregnum.compiler;

import net.minecraft.util.math.Vec3d;

public enum Opcode {
    // Structural Opcodes
    SET_ORIGIN(5, 1, new Class<?>[]{}), // No operands, sets to caster origin
    EXPAND_AREA(15, 3, new Class<?>[]{Double.class}), // Requires a double for radius increase
    SET_VECTOR(10, 2, new Class<?>[]{Vec3d.class}), // Requires a Vec3d
    
    // Elemental Opcodes
    APPLY_ELEMENT(20, 4, new Class<?>[]{String.class}), // Requires string element name
    
    // Execution Opcodes
    RESOLVE_SHAPE(25, 5, new Class<?>[]{String.class}), // Requires string shape name
    EXECUTE_EFFECT(5, 1, new Class<?>[]{}), // Final execution step
    
    // Modifiers
    AMPLIFY(30, 4, new Class<?>[]{Double.class}), // Requires double multiplier (wired to the AMPLIFY sigil)
    DIVIDE(50, 10, new Class<?>[]{Integer.class}), // PLANNED: multi-casting; no sigil yet, VM stub throws

    // Error handling
    INVALID(0, 0, new Class<?>[]{String.class}); // Emitted for unknown sigils; breaks the spell in the VM

    public final int baseManaCost;
    public final int complexityWeight;
    public final Class<?>[] expectedTypes;

    Opcode(int baseManaCost, int complexityWeight, Class<?>[] expectedTypes) {
        this.baseManaCost = baseManaCost;
        this.complexityWeight = complexityWeight;
        this.expectedTypes = expectedTypes;
    }
}
