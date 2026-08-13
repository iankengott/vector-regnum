package vectorregnum.core.circle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import vectorregnum.core.vm2.Opcode;
import vectorregnum.core.vm2.Vector3;
import vectorregnum.core.semantic.CreationForm;
import vectorregnum.core.semantic.CreationMaterial;
import vectorregnum.core.vm2.SpellVm;
import vectorregnum.core.vm2.WorldAccess;
import vectorregnum.core.vm2.WorldEffect;

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

    @Test
    void staticStackFaultPointsToThePhysicalSigilBeforeExecution() {
        MagicCircle circle = new MagicCircle(1, "vm-stack-bad", "VM Stack Bad", 1, 4, List.of(
                sigil(0, 0, "VM_PUSH_BOOLEAN", new CircleValue.BooleanValue(true)),
                sigil(0, 1, "VM_PUSH_NUMBER", number("2")),
                sigil(0, 2, "VM_ADD"),
                sigil(0, 3, "EXECUTE")));
        Vm2CircleCompilation compiled = Vm2CircleCompiler.compile(circle, CONTEXT);
        assertTrue(compiled.hasErrors());
        assertTrue(compiled.compiledProgram().isEmpty());
        assertTrue(compiled.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code().equals("STATIC_TYPE_MISMATCH")
                        && diagnostic.coordinate().equals(new CircleCoordinate(0, 2))
                        && diagnostic.sourceIndex() == 2));
    }

    @Test
    void creationSigilLowersTypedBoundedSemanticPayloadAtExactSource() {
        MagicCircle circle = new MagicCircle(1, "create", "Create", 1, 4, List.of(
                sigil(0, 0, "VM_CREATE_FORM", new CircleValue.TextValue("ice"),
                        new CircleValue.TextValue("barrier"), number("8"), number("40"),
                        new CircleValue.BooleanValue(false)), sigil(0, 1, "EXECUTE")));
        Vm2CircleCompilation compiled = Vm2CircleCompiler.compile(circle, CONTEXT);
        assertFalse(compiled.hasErrors(), () -> compiled.diagnostics().toString());
        var semantic = compiled.compiledProgram().orElseThrow().instructions().getFirst().semantic();
        assertEquals(CreationMaterial.ICE, semantic.creationSpec().material());
        assertEquals(CreationForm.BARRIER, semantic.creationSpec().form());
        assertEquals(0, semantic.source().sourceIndex());
        SpellVm vm = new SpellVm(compiled.compiledProgram().orElseThrow(), WorldAccess.EMPTY);
        while (!vm.isTerminal()) vm.tick();
        assertTrue(vm.fault().isEmpty());
        assertTrue(vm.allEffects().getFirst() instanceof WorldEffect.SemanticStep);

        MagicCircle invalid = new MagicCircle(1, "bad-create", "Bad Create", 1, 4, List.of(
                sigil(0, 0, "VM_CREATE_FORM", new CircleValue.TextValue("fire"),
                        new CircleValue.TextValue("barrier"), number("1"), number("20"),
                        new CircleValue.BooleanValue(false)), sigil(0, 1, "EXECUTE")));
        assertEquals("INVALID_CREATION_FORM",
                Vm2CircleCompiler.compile(invalid, CONTEXT).diagnostics().getFirst().code());
    }

    private static CircleValue number(String value) {
        return new CircleValue.NumberValue(value);
    }

    private static PlacedSigil sigil(
            int ring, int slot, String type, CircleValue... parameters) {
        return new PlacedSigil(new CircleCoordinate(ring, slot), type, List.of(parameters));
    }
}
