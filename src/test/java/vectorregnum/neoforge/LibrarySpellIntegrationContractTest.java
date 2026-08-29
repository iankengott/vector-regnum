package vectorregnum.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import vectorregnum.core.vm2.Opcode;
import vectorregnum.core.vm2.Vector3;
import vectorregnum.neoforge.progression.ProgressionSpellLibrary;
import vectorregnum.neoforge.progression.LibraryOpcode;
import vectorregnum.core.semantic.LoweringContext;
import vectorregnum.core.semantic.SemanticOpcode;
import vectorregnum.core.semantic.SemanticVmLowerer;
import java.util.EnumSet;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;

class LibrarySpellIntegrationContractTest {
    @Test
    void everyCuratedSpellHasAPlayableNeoForgeImplementation() {
        assertEquals(ProgressionSpellLibrary.BY_ID.keySet(),
                LibrarySpellService.implementedSpellIds());
        assertEquals(20, LibrarySpellService.implementedSpellIds().size());
    }

    @Test
    void liveVectorStepProgramReallyDelaysAndEmitsTypedPhysics() {
        var program = NeoForgeVmService.impulseProgram(
                UUID.randomUUID().toString(), new Vector3(1.0, 0.25, 0.0), 20, 1);
        assertTrue(program.instructions().stream().anyMatch(instruction ->
                instruction.opcode() == Opcode.DELAY && instruction.argument() == 20));
        assertTrue(program.instructions().stream().anyMatch(instruction ->
                instruction.opcode() == Opcode.IMPULSE));
        assertTrue(program.manaCost().physicalWork() > 0.0);
        assertTrue(program.manaCost().duration() > 0.0);
    }

    @Test
    void everyLibraryOpcodeMapsLosslesslyAndEverySpellUsesTheGenericBackend() {
        assertEquals(EnumSet.allOf(LibraryOpcode.class).stream().map(Enum::name).toList(),
                EnumSet.allOf(LibraryOpcode.class).stream()
                        .map(LibrarySemanticAdapter::semanticOpcode).map(Enum::name).toList());
        assertTrue(SemanticVmLowerer.missingOpcodes().isEmpty());
        assertEquals(EnumSet.allOf(SemanticOpcode.class), SemanticSpellExecutor.supportedOpcodes());
        for (var spell : ProgressionSpellLibrary.ALL) {
            var semantic = LibrarySemanticAdapter.adapt(spell);
            var program = SemanticVmLowerer.lowerChecked(semantic,
                    new LoweringContext(spell.id(), 0, Map.of()));
            assertTrue(program.instructions().stream().anyMatch(instruction ->
                    instruction.opcode() == Opcode.SEMANTIC), spell.id());
        }
    }

    @Test
    void boundedRepeatReplaysThePrecedingActionIncludingHarvestAfterWait() {
        var harvest = LibrarySemanticAdapter.adapt(
                ProgressionSpellLibrary.BY_ID.get("harvest_cycle"));
        var multiplicities = SemanticSpellExecutor.actionMultiplicities(harvest.instructions());
        int breakIndex = java.util.stream.IntStream.range(0, harvest.instructions().size())
                .filter(index -> harvest.instructions().get(index).opcode()
                        == SemanticOpcode.BREAK_BLOCKS).findFirst().orElseThrow();
        assertEquals(8, multiplicities.get(breakIndex));
        assertTrue(harvest.instructions().stream().anyMatch(step ->
                step.opcode() == SemanticOpcode.WAIT_TICKS));
    }

    @Test
    void libraryCastingLeavesAdmissionAndManaMutationInTheVmService() throws Exception {
        Path source = locateSource();
        String text = Files.readString(source);
        int castStart = text.indexOf("private static boolean cast(");
        int listStart = text.indexOf("public static void list(", castStart);
        String castBody = text.substring(castStart, listStart);
        assertTrue(castBody.contains("NeoForgeVmService.startSemantic"));
        assertTrue(!castBody.contains("ManaData.ensureAvailable"),
                "library code must not draw source charges before VM admission");
    }

    private static Path locateSource() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            Path source = candidate.resolve(
                    "src/main/java/vectorregnum/neoforge/LibrarySpellService.java");
            if (Files.isRegularFile(source)) return source;
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("could not locate LibrarySpellService.java");
    }
}
