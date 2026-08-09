package vectorregnum.compiler;

import java.util.ArrayList;
import java.util.List;

public class Compiler {
    /**
     * Translates an ordered list of Sigils (read clockwise from a magic circle)
     * into a list of executable VirtualMachine instructions.
     */
    public static CompiledSpell compile(List<Sigil> sigils) {
        Logger.log("--- Starting Compilation Phase ---");
        List<Instruction> instructions = new ArrayList<>();
        
        // sigilIndex tracks the position of the sigil in the source circle and is
        // used as each instruction's originalIndex. It increments once per sigil
        // (see end of loop) so every "ISSUE AT INDEX N" message always points at the
        // real sigil position -- even when a sigil compiles to no/an invalid opcode.
        int sigilIndex = 0;
        for (Sigil sigil : sigils) {
            Logger.log("Compiling Sigil: " + sigil.type);

            if (sigil.type.startsWith("ELEMENT_")) {
                String element = sigil.type.substring(8).toLowerCase();
                instructions.add(new Instruction(Opcode.APPLY_ELEMENT, sigilIndex, element));
            } else if (sigil.type.startsWith("SHAPE_")) {
                String shape = sigil.type.substring(6).toLowerCase();
                instructions.add(new Instruction(Opcode.RESOLVE_SHAPE, sigilIndex, shape));
            } else {
                switch (sigil.type) {
                    case "ORIGIN_SELF":
                        instructions.add(new Instruction(Opcode.SET_ORIGIN, sigilIndex));
                        break;
                    case "EXPAND":
                        instructions.add(new Instruction(Opcode.EXPAND_AREA, sigilIndex, sigil.params));
                        break;
                    case "AMPLIFY":
                        instructions.add(new Instruction(Opcode.AMPLIFY, sigilIndex, sigil.params));
                        break;
                    case "VECTOR_FORWARD":
                        // In reality, this would bind dynamically to the caster's look direction.
                        // For demonstration, we'll pass a placeholder.
                        instructions.add(new Instruction(Opcode.SET_VECTOR, sigilIndex, net.minecraft.util.math.Vec3d.ZERO));
                        break;
                    case "EXECUTE":
                        instructions.add(new Instruction(Opcode.EXECUTE_EFFECT, sigilIndex));
                        break;
                    default:
                        // An unknown sigil is a hard compile error. Emit an INVALID
                        // instruction so the VM breaks the spell at this exact position
                        // instead of silently dropping it and mutating the whole cast.
                        Logger.error("Compiler Error: Unknown sigil type -> " + sigil.type);
                        instructions.add(new Instruction(Opcode.INVALID, sigilIndex, sigil.type));
                        break;
                }
            }
            sigilIndex++;
        }
        
        CompiledSpell spell = new CompiledSpell(instructions);
        Logger.log("--- Compilation Complete (" + instructions.size() + " instructions) ---");
        Logger.log("Total Mana Cost: " + spell.totalManaCost + " | Complexity: " + spell.totalComplexity);
        return spell;
    }
}