package vectorregnum.neoforge.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import vectorregnum.core.circle.CircleCoordinate;
import vectorregnum.core.circle.CircleValue;
import vectorregnum.core.circle.MagicCircle;
import vectorregnum.core.circle.SpellMedium;

class CircleEditorControllerTest {
    @Test
    void graphicalRequestsBuildCompileUndoAndBindCircle() {
        List<SpellMedium> bound = new ArrayList<>();
        CircleEditorController controller = new CircleEditorController(
                MagicCircle.empty("graphical", "Graphical", 2, 8),
                (circle, medium) -> {
                    bound.add(medium);
                    return CircleEditorController.BindingResult.accepted("Bound " + medium);
                });

        place(controller, 0, 0, "ORIGIN_SELF");
        place(controller, 0, 1, "ELEMENT_FIRE");
        place(controller, 0, 2, "SHAPE_AURA");
        place(controller, 0, 3, "EXPAND");
        var parameterized = request(controller, new CircleEditorRequest.UpdateParameters(
                coordinate(0, 3), List.of("2.50")));
        assertEquals("2.5", ((CircleValue.NumberValue) parameterized.circle().sigils().getLast()
                .parameters().getFirst()).canonicalText());
        place(controller, 1, 0, "EXECUTE");

        var compiled = request(controller, new CircleEditorRequest.Compile());
        assertTrue(compiled.bindable());
        assertTrue(compiled.diagnostics().isEmpty());

        var boundModel = request(controller, new CircleEditorRequest.Bind(SpellMedium.BOOK));
        assertEquals(List.of(SpellMedium.BOOK), bound);
        assertEquals("Bound BOOK", boundModel.statusMessage());

        request(controller, new CircleEditorRequest.Remove(coordinate(1, 0)));
        assertFalse(request(controller, new CircleEditorRequest.Compile()).bindable());
        var restored = request(controller, new CircleEditorRequest.Undo());
        assertTrue(restored.bindable());
        assertEquals("EXECUTE", restored.circle().executionOrder().getLast().type());
    }

    @Test
    void typedErrorsAndCompilerDiagnosticsStayAttachedToPhysicalSlots() {
        CircleEditorController controller = controller("typed-errors", 1, 4);
        place(controller, 0, 0, "VM_PUSH_BOOLEAN");

        var badType = request(controller, new CircleEditorRequest.UpdateParameters(
                coordinate(0, 0), List.of("yes")));
        assertEquals("INVALID_PARAMETERS", badType.diagnostics().getFirst().code());
        assertEquals(coordinate(0, 0), badType.diagnostics().getFirst().coordinate());
        assertTrue(badType.slots().getFirst().hasError());

        request(controller, new CircleEditorRequest.UpdateParameters(
                coordinate(0, 0), List.of("true")));
        place(controller, 0, 1, "EXECUTE");
        var compiled = request(controller, new CircleEditorRequest.Compile());
        assertTrue(compiled.bindable());

        var occupied = request(controller, new CircleEditorRequest.Place(
                coordinate(0, 1), "VM_POP"));
        assertEquals("POSITION_OCCUPIED", occupied.diagnostics().getFirst().code());
    }

    @Test
    void snapshotIncludesEverySlotSelectionSearchAndResponsiveLayout() {
        CircleEditorController controller = controller("snapshot", 3, 8);
        request(controller, new CircleEditorRequest.Select(coordinate(1, 4)));
        var model = request(controller, new CircleEditorRequest.SearchPalette("physics"));

        assertEquals(24, model.slots().size());
        assertTrue(model.slots().stream().anyMatch(slot -> slot.selected()
                && slot.coordinate().equals(coordinate(1, 4))));
        assertEquals(6, model.palette().size());
        assertTrue(model.layout().compact());
        assertEquals(24, model.layout().minimumTouchTarget());
    }

    @Test
    void bindingNeverCallsGatewayWhileCompilationHasErrors() {
        List<SpellMedium> bound = new ArrayList<>();
        CircleEditorController controller = new CircleEditorController(
                MagicCircle.empty("invalid", "Invalid", 1, 4),
                (circle, medium) -> {
                    bound.add(medium);
                    return CircleEditorController.BindingResult.accepted("unexpected");
                });

        var model = request(controller, new CircleEditorRequest.Bind(SpellMedium.SCROLL));
        assertTrue(bound.isEmpty());
        assertEquals("BIND_BLOCKED", model.diagnostics().getFirst().code());
    }

    private static CircleEditorController controller(String id, int rings, int slots) {
        return new CircleEditorController(MagicCircle.empty(id, id, rings, slots),
                (circle, medium) -> CircleEditorController.BindingResult.accepted("bound"));
    }

    private static void place(CircleEditorController controller, int ring, int slot, String sigil) {
        request(controller, new CircleEditorRequest.Place(coordinate(ring, slot), sigil));
    }

    private static CircleEditorScreenModel request(
            CircleEditorController controller, CircleEditorRequest request) {
        return controller.handle(request, 800, 500);
    }

    private static CircleCoordinate coordinate(int ring, int slot) {
        return new CircleCoordinate(ring, slot);
    }
}
