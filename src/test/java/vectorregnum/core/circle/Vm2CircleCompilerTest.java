package vectorregnum.core.circle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import vectorregnum.core.vm2.Opcode;
import vectorregnum.core.vm2.Vector3;

class Vm2CircleCompilerTest {
    private static final Vm2CircleCompiler.Context CONTEXT =
            new Vm2CircleCompiler.Context("caster", new Vector3(1, 2, 3), new Vector3(1, 0, 0));

    @Test
    void clockwiseCircleLowersDirectlyToTypedDelayedImpulseProgram() {
        MagicCircle circle = new MagicCircle(1, "vm-step", "VM Step", 2, 8, List.of(
                sigil(0, 0, "VM_DURATION", number("1")),
                sigil(0, 1, "VM_DELAY", number("2")),
                sigil(0, 2, "VM_PUSH_SELF"),
                sigil(0, 3, "VM_PUSH_LOOK"),
                sigil(0, 4, "VM_PUSH_NUMBER", number("1.4")),
                sigil(0, 5, "VM_MULTIPLY"),
                sigil(1, 0, "VM_IMPULSE", number("20"), number("0")),
                sigil(1, 1, "EXECUTE")));
        Vm2CircleCompilation compiled = Vm2CircleCompiler.compile(circle, CONTEXT);
        assertFalse(compiled.hasErrors());
        assertEquals(Opcode.DELAY, compiled.compiledProgram().orElseThrow().instructions().get(1).opcode());
        assertEquals(Opcode.IMPULSE, compiled.compiledProgram().orElseThrow().instructions().get(6).opcode());
        assertTrue(compiled.compiledProgram().orElseThrow().manaCost().physicalWork() > 0);
    }

    @Test
    void typedParameterAndControlFaultsPointToPhysicalSlot() {
        MagicCircle circle = new MagicCircle(1, "vm-bad", "VM Bad", 1, 4, List.of(
                sigil(0, 0, "VM_PUSH_NUMBER", new CircleValue.TextValue("wrong")),
                sigil(0, 1, "VM_JUMP", number("99")),
                sigil(0, 2, "EXECUTE")));
        Vm2CircleCompilation compiled = Vm2CircleCompiler.compile(circle, CONTEXT);
        assertTrue(compiled.hasErrors());
        assertTrue(compiled.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code().equals("PARAMETER_TYPE")
                        && diagnostic.coordinate().equals(new CircleCoordinate(0, 0))));
    }

    private static CircleValue number(String value) {
        return new CircleValue.NumberValue(value);
    }

    private static PlacedSigil sigil(
            int ring, int slot, String type, CircleValue... parameters) {
        return new PlacedSigil(new CircleCoordinate(ring, slot), type, List.of(parameters));
    }
}
