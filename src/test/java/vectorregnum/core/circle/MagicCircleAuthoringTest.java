package vectorregnum.core.circle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MagicCircleAuthoringTest {
    @Test
    void executionIsClockwiseOnEachRingAndThenMovesInward() {
        MagicCircle circle = new MagicCircle(1, "order", "Order", 3, 8, List.of(
                sigil(2, 0, "EXECUTE"),
                sigil(0, 6, "SHAPE_AURA"),
                sigil(1, 1, "EXPAND", CircleValue.number(2)),
                sigil(0, 0, "ORIGIN_SELF"),
                sigil(0, 3, "ELEMENT_FIRE")));

        assertEquals(List.of("ORIGIN_SELF", "ELEMENT_FIRE", "SHAPE_AURA", "EXPAND", "EXECUTE"),
                circle.executionOrder().stream().map(PlacedSigil::type).toList());
    }

    @Test
    void modelIsImmutableAndRejectsAmbiguousGeometry() {
        List<PlacedSigil> supplied = new ArrayList<>(List.of(sigil(0, 0, "ORIGIN_SELF")));
        MagicCircle circle = new MagicCircle(1, "safe", "Safe", 1, 2, supplied);
        supplied.clear();
        assertEquals(1, circle.sigils().size());
        assertThrows(UnsupportedOperationException.class, () -> circle.sigils().clear());

        assertThrows(IllegalArgumentException.class, () -> new MagicCircle(1, "duplicate", "Duplicate", 1, 2,
                List.of(sigil(0, 0, "ORIGIN_SELF"), sigil(0, 0, "EXECUTE"))));
        assertThrows(IllegalArgumentException.class, () -> new MagicCircle(1, "outside", "Outside", 1, 2,
                List.of(sigil(1, 0, "EXECUTE"))));
        assertThrows(IllegalArgumentException.class,
                () -> new PlacedSigil(new CircleCoordinate(0, 0), "not-valid", List.of()));
    }

    @Test
    void compilePreviewPublishesBytecodeOnlyForValidCircle() {
        MagicCircle circle = validCircle();
        CircleCompilation preview = CircleAuthoringCompiler.compile(circle);

        assertFalse(preview.hasErrors());
        assertTrue(preview.spell().isPresent());
        assertEquals(5, preview.spell().orElseThrow().instructionCount());
        assertEquals(List.of(0, 1, 2, 3, 4), preview.spell().orElseThrow().sourceIndices());
        assertEquals("ORIGIN_SELF", preview.compatibilitySource().getFirst().type());
        assertEquals("EXECUTE", preview.compatibilitySource().getLast().type());
    }

    @Test
    void previewDiagnosticsPointBackToPhysicalSigils() {
        CircleCoordinate bad = new CircleCoordinate(0, 1);
        MagicCircle circle = new MagicCircle(1, "bad", "Bad", 1, 4, List.of(
                sigil(0, 0, "ORIGIN_SELF"),
                new PlacedSigil(bad, "AMPLIFY", List.of(CircleValue.text("lots"))),
                sigil(0, 2, "SHAPE_AURA")));

        CircleCompilation preview = CircleAuthoringCompiler.compile(circle);
        assertTrue(preview.hasErrors());
        assertTrue(preview.spell().isEmpty());
        assertTrue(preview.diagnostics().stream().anyMatch(d ->
                d.code().equals("PARAMETER_TYPE") && d.location().orElseThrow().equals(bad)
                        && d.sourceIndex() == 1));
        assertTrue(preview.diagnostics().stream().anyMatch(d ->
                d.code().equals("MISSING_TERMINAL_EXECUTE")));
    }

    @Test
    void previewReportsGeometricDependencyErrorsBeforeCasting() {
        CircleCoordinate projectile = new CircleCoordinate(0, 0);
        MagicCircle circle = new MagicCircle(1, "dependencies", "Dependencies", 1, 4, List.of(
                new PlacedSigil(projectile, "SHAPE_PROJECTILE"),
                sigil(0, 1, "ORIGIN_SELF"),
                sigil(0, 2, "EXECUTE")));

        CircleCompilation preview = CircleAuthoringCompiler.compile(circle);
        assertTrue(preview.diagnostics().stream().anyMatch(d ->
                d.code().equals("ORIGIN_REQUIRED") && d.coordinate().equals(projectile)));
        assertTrue(preview.diagnostics().stream().anyMatch(d -> d.code().equals("MISSING_COMPONENT")));
    }

    @Test
    void editorSupportsPlaceParameterizeRemoveUndoAndDiagnostics() {
        CircleEditorSession editor = new CircleEditorSession(
                MagicCircle.empty("editor", "Editor", 2, 4));
        CircleCoordinate origin = new CircleCoordinate(0, 0);
        CircleCoordinate scale = new CircleCoordinate(0, 1);

        assertTrue(editor.place(origin, "ORIGIN_SELF").changed());
        assertFalse(editor.place(origin, "ELEMENT_FIRE").changed());
        assertEquals("POSITION_OCCUPIED", editor.lastDiagnostics().getFirst().code());
        editor.clearDiagnostics();
        assertTrue(editor.lastDiagnostics().isEmpty());

        assertTrue(editor.place(scale, "EXPAND").changed());
        assertTrue(editor.parameterize(scale, List.of(CircleValue.number(3))).changed());
        assertEquals("3", ((CircleValue.NumberValue) editor.current().sigils().get(1)
                .parameters().getFirst()).canonicalText());
        assertTrue(editor.remove(scale).changed());
        assertEquals(1, editor.current().sigils().size());
        assertTrue(editor.undo().changed());
        assertEquals(2, editor.current().sigils().size());

        assertFalse(editor.place(new CircleCoordinate(5, 0), "EXECUTE").changed());
        assertEquals("POSITION_OUTSIDE_CIRCLE", editor.lastDiagnostics().getFirst().code());
        assertTrue(editor.compilePreview().hasErrors());
        assertEquals(editor.compilePreview().diagnostics(), editor.lastDiagnostics());
    }

    @Test
    void editorUndoHistoryIsBounded() {
        CircleEditorSession editor = new CircleEditorSession(
                MagicCircle.empty("bounded", "Bounded", 2, 64));
        for (int slot = 0; slot < 64; slot++) {
            assertTrue(editor.place(new CircleCoordinate(0, slot), "ORIGIN_SELF").changed());
            assertTrue(editor.remove(new CircleCoordinate(0, slot)).changed());
        }
        assertEquals(100, editor.undoDepth());
    }

    static MagicCircle validCircle() {
        return new MagicCircle(1, "fire-aura", "Fire Aura", 2, 8, List.of(
                sigil(0, 0, "ORIGIN_SELF"),
                sigil(0, 1, "ELEMENT_FIRE"),
                sigil(0, 2, "SHAPE_AURA"),
                sigil(0, 3, "EXPAND", CircleValue.number(2)),
                sigil(1, 0, "EXECUTE")));
    }

    private static PlacedSigil sigil(int ring, int slot, String type, CircleValue... parameters) {
        return new PlacedSigil(new CircleCoordinate(ring, slot), type, List.of(parameters));
    }
}
