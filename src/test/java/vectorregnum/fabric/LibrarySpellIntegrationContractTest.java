package vectorregnum.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import vectorregnum.core.vm2.Opcode;
import vectorregnum.core.vm2.Vector3;
import vectorregnum.fabric.progression.ProgressionSpellLibrary;

class LibrarySpellIntegrationContractTest {
    @Test
    void everyCuratedSpellHasAPlayableFabricImplementation() {
        assertEquals(ProgressionSpellLibrary.BY_ID.keySet(),
                LibrarySpellService.implementedSpellIds());
        assertEquals(15, LibrarySpellService.implementedSpellIds().size());
    }

    @Test
    void liveVectorStepProgramReallyDelaysAndEmitsTypedPhysics() {
        var program = FabricVmService.impulseProgram(
                UUID.randomUUID().toString(), new Vector3(1.0, 0.25, 0.0), 20, 1);
        assertTrue(program.instructions().stream().anyMatch(instruction ->
                instruction.opcode() == Opcode.DELAY && instruction.argument() == 20));
        assertTrue(program.instructions().stream().anyMatch(instruction ->
                instruction.opcode() == Opcode.IMPULSE));
        assertTrue(program.manaCost().physicalWork() > 0.0);
        assertTrue(program.manaCost().duration() > 0.0);
    }
}
