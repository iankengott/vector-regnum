package vectorregnum.compiler;

import java.util.List;

public class VirtualMachine {

    public static void execute(CompiledSpell spell, SpellState state) {
        Logger.log("--- Starting Virtual Machine Execution ---");
        
        // In the future, this is where we would check if the caster has enough mana 
        // to pay spell.totalManaCost before we even begin execution.
        
        for (Instruction instruction : spell.instructions) {
            if (state.isBroken) {
                // If the spell is already broken, we stop structured execution 
                // and jump to the chaotic mutation phase.
                Logger.log("Spell is broken. Halting execution loop.");
                break;
            }
            
            Logger.log("Executing Opcode: " + instruction.opcode + " [Index: " + instruction.originalIndex + "]");
            
            try {
                validateTypes(instruction);
                processInstruction(instruction, state);
            } catch (TypeMismatchException e) {
                Logger.error("Type Mismatch Exception caught!");
                state.breakSpell("Type mismatch: " + e.getMessage(), e.instructionIndex);
            } catch (StateDependencyException e) {
                Logger.error("State Dependency Exception caught!");
                state.breakSpell("State dependency failure: " + e.getMessage(), e.instructionIndex);
            } catch (Exception e) {
                // Any other logical inconsistency causes the spell to break.
                Logger.error("General Execution Error caught!");
                state.breakSpell("Execution error: " + e.getMessage(), instruction.originalIndex);
            }
        }
        
        applyToWorld(state);
    }

    private static void validateTypes(Instruction instruction) throws TypeMismatchException {
        Class<?>[] expected = instruction.opcode.expectedTypes;
        if (instruction.operands.length != expected.length) {
            throw new TypeMismatchException("Expected " + expected.length + " operands but got " + instruction.operands.length, instruction.originalIndex);
        }
        for (int i = 0; i < expected.length; i++) {
            if (instruction.operands[i] == null) {
                throw new TypeMismatchException("Operand " + i + " cannot be null", instruction.originalIndex);
            }
            if (!expected[i].isAssignableFrom(instruction.operands[i].getClass())) {
                throw new TypeMismatchException("Operand " + i + " expected " + expected[i].getSimpleName() + " but got " + instruction.operands[i].getClass().getSimpleName(), instruction.originalIndex);
            }
        }
    }

    private static void processInstruction(Instruction instruction, SpellState state) throws StateDependencyException {
        switch (instruction.opcode) {
            case SET_ORIGIN:
                if (state.origin != null) throw new StateDependencyException("Origin already set.", instruction.originalIndex);
                state.origin = state.caster.getPos();
                break;
                
            case SET_VECTOR:
                if (state.origin == null) throw new StateDependencyException("Must set origin before setting vector.", instruction.originalIndex);
                state.direction = (net.minecraft.util.math.Vec3d) instruction.operands[0];
                break;
                
            case EXPAND_AREA:
                if (state.shape == null) throw new StateDependencyException("Cannot expand area before resolving shape.", instruction.originalIndex);
                state.radius += (Double) instruction.operands[0];
                break;
                
            case APPLY_ELEMENT:
                if (state.origin == null) throw new StateDependencyException("Must set origin before applying element.", instruction.originalIndex);
                if (state.element != null) throw new StateDependencyException("Element already applied.", instruction.originalIndex);
                state.element = (String) instruction.operands[0];
                break;
                
            case RESOLVE_SHAPE:
                if (state.origin == null) throw new StateDependencyException("Must set origin before resolving shape.", instruction.originalIndex);
                if (state.direction == null && "projectile".equals(instruction.operands[0])) {
                    throw new StateDependencyException("Projectile shape requires a vector.", instruction.originalIndex);
                }
                state.shape = (String) instruction.operands[0];
                break;
                
            case AMPLIFY:
                if (state.shape == null && state.element == null) throw new StateDependencyException("Nothing to amplify yet.", instruction.originalIndex);
                state.magnitude *= (Double) instruction.operands[0];
                break;
                
            case EXECUTE_EFFECT:
                // Indicates the spell logic is complete. Final validation occurs here.
                if (state.origin == null || state.shape == null) {
                    throw new StateDependencyException("Spell lacks origin or shape before execution.", instruction.originalIndex);
                }
                break;
                
            case DIVIDE:
                 throw new StateDependencyException("DIVIDE logic not yet implemented in VM.", instruction.originalIndex);

            case INVALID:
                 // Produced by the compiler for an unknown/typo'd sigil.
                 throw new StateDependencyException("Unknown sigil '" + instruction.operands[0] + "' could not be compiled.", instruction.originalIndex);

            default:
                throw new UnsupportedOperationException("Unknown opcode: " + instruction.opcode);
        }
    }

    private static void applyToWorld(SpellState state) {
        if (state.isBroken) {
            WildMagicEngine.detonate(state);
            return;
        }

        // Apply structured spell based on state
        // This is where we will hook into Minecraft's world to spawn entities, particles, damage, etc.
        System.out.println("Executing spell: " + state.element + " " + state.shape + " at " + state.origin
                + " | radius " + state.radius + " | magnitude " + state.magnitude);
    }
}
