package vectorregnum.core.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vectorregnum.core.presentation.PresentationCompiler;
import vectorregnum.core.presentation.PresentationCueKind;
import vectorregnum.core.presentation.PresentationInstruction;
import vectorregnum.core.vm2.SourceLocation;

class Priority26SemanticTest {
    private static final SourceLocation SOURCE = SourceLocation.at(0, "PRIORITY_26");

    @Test
    void renderOnlyAndAttentionOpcodesHaveBoundedTypedSchemas() {
        SemanticInstruction render = new SemanticInstruction(SemanticOpcode.RENDER,
                Map.of("style", new SemanticValue.TextValue("outline")), SOURCE);
        SemanticInstruction attention = new SemanticInstruction(SemanticOpcode.FORCE_ATTENTION,
                Map.of("range", new SemanticValue.NumberValue(8),
                        "angle", new SemanticValue.NumberValue(30),
                        "strength", new SemanticValue.NumberValue(.5),
                        "ticks", new SemanticValue.NumberValue(20)), SOURCE);
        assertEquals(SemanticOpcode.RENDER, render.opcode());
        assertEquals(20, SemanticSchema.integer(attention.operands(), "ticks"));
        assertThrows(IllegalArgumentException.class, () -> new SemanticInstruction(
                SemanticOpcode.FORCE_ATTENTION,
                Map.of("range", new SemanticValue.NumberValue(33),
                        "angle", new SemanticValue.NumberValue(30),
                        "strength", new SemanticValue.NumberValue(.5),
                        "ticks", new SemanticValue.NumberValue(20)), SOURCE));
    }

    @Test
    void presentationMarksRenderAsCosmeticButAttentionAsTruth() {
        SemanticProgram program = new SemanticProgram(List.of(
                new SemanticInstruction(SemanticOpcode.RENDER,
                        Map.of("style", new SemanticValue.TextValue("telegraph")), SOURCE),
                new SemanticInstruction(SemanticOpcode.FORCE_ATTENTION,
                        Map.of("range", new SemanticValue.NumberValue(8),
                                "angle", new SemanticValue.NumberValue(30),
                                "strength", new SemanticValue.NumberValue(.5),
                                "ticks", new SemanticValue.NumberValue(20)), SOURCE),
                SemanticInstruction.simple(SemanticOpcode.EXECUTE, SOURCE)));
        var vm = SemanticVmLowerer.lowerChecked(program,
                new LoweringContext("priority26", 1L, Map.of()));
        var compiled = PresentationCompiler.compile("priority26", 1L, vm);
        PresentationInstruction render = compiled.instructions().stream()
                .filter(cue -> cue.rendererId().endsWith("cosmetic/render_only"))
                .findFirst().orElseThrow();
        assertFalse(render.truthLayer());
        assertTrue(compiled.instructions().stream().anyMatch(cue ->
                cue.rendererId().endsWith("truth/attention_warning")
                        && cue.cueKind() == PresentationCueKind.RUNES
                        && cue.truthLayer()));
    }
}
