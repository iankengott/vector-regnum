package vectorregnum.fabric.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import vectorregnum.core.circle.CircleCoordinate;
import vectorregnum.core.circle.CircleValue;
import vectorregnum.core.circle.MagicCircle;

class CircleEditorInteractionTest {
    @Test
    void primaryClickPlacesOnlyIntoAnEmptySlot() {
        CircleEditorController controller = controller();
        CircleCoordinate empty = new CircleCoordinate(0, 0);
        CircleEditorRequest request = CircleEditorInteraction.primaryClick(
                controller.snapshot(800, 500), empty, "VM_PUSH_NUMBER");
        assertInstanceOf(CircleEditorRequest.Place.class, request);

        controller.handle(request, 800, 500);
        CircleEditorRequest occupied = CircleEditorInteraction.primaryClick(
                controller.snapshot(800, 500), empty, "EXECUTE");
        assertInstanceOf(CircleEditorRequest.Select.class, occupied);
    }

    @Test
    void secondaryClickRemovesOccupiedSlotAndOnlySelectsEmptySlot() {
        CircleEditorController controller = controller();
        CircleCoordinate coordinate = new CircleCoordinate(0, 0);
        controller.handle(new CircleEditorRequest.Place(coordinate, "VM_PUSH_NUMBER"), 800, 500);
        assertInstanceOf(CircleEditorRequest.Remove.class, CircleEditorInteraction.secondaryClick(
                controller.snapshot(800, 500), coordinate));

        CircleCoordinate empty = new CircleCoordinate(0, 1);
        assertInstanceOf(CircleEditorRequest.Select.class, CircleEditorInteraction.secondaryClick(
                controller.snapshot(800, 500), empty));
    }

    @Test
    void paletteAndExistingSigilsDragOnlyIntoEmptySlots() {
        CircleEditorController controller = controller();
        CircleCoordinate source = new CircleCoordinate(0, 0);
        CircleCoordinate destination = new CircleCoordinate(0, 1);
        CircleCoordinate paletteDestination = new CircleCoordinate(0, 2);
        controller.handle(new CircleEditorRequest.Place(source, "VM_PUSH_NUMBER"), 800, 500);
        controller.handle(new CircleEditorRequest.UpdateParameters(source, List.of("4.25")),
                800, 500);

        CircleEditorRequest moved = CircleEditorInteraction.dragPlacement(
                controller.snapshot(800, 500), source, destination, null).orElseThrow();
        assertInstanceOf(CircleEditorRequest.Move.class, moved);
        CircleEditorScreenModel movedModel = controller.handle(moved, 800, 500);
        assertEquals("4.25", ((CircleValue.NumberValue) movedModel.slots().stream()
                .filter(slot -> slot.coordinate().equals(destination)).findFirst().orElseThrow()
                .parameters().getFirst()).canonicalText());

        CircleEditorRequest placed = CircleEditorInteraction.dragPlacement(movedModel,
                null, paletteDestination, "EXECUTE").orElseThrow();
        assertInstanceOf(CircleEditorRequest.Place.class, placed);
        controller.handle(placed, 800, 500);
        assertTrue(CircleEditorInteraction.dragPlacement(controller.snapshot(800, 500),
                destination, paletteDestination, null).isEmpty());
    }

    @Test
    void parameterTokenizerPreservesQuotedTextAndEscapes() {
        assertEquals(List.of("12", "text:named target", "true", "two words"),
                CircleEditorInteraction.parseParameterInput(
                        "12, text:\"named target\" true 'two words'"));
        assertEquals(List.of("text:a b"),
                CircleEditorInteraction.parseParameterInput("text:a\\ b"));
        String displayed = CircleEditorInteraction.formatParameter(
                new CircleValue.TextValue("named \"target\""));
        assertEquals(List.of("text:named \"target\""),
                CircleEditorInteraction.parseParameterInput(displayed));
        List<String> packetValues = List.of("12", "text:named target", "text:a\\b");
        assertEquals(packetValues, CircleEditorInteraction.parseParameterInput(
                CircleEditorInteraction.encodeParameterInput(packetValues)));
    }

    @Test
    void parameterTokenizerRejectsIncompleteSyntax() {
        assertThrows(IllegalArgumentException.class,
                () -> CircleEditorInteraction.parseParameterInput("text:\"unfinished"));
        assertThrows(IllegalArgumentException.class,
                () -> CircleEditorInteraction.parseParameterInput("text:unfinished\\"));
    }

    private static CircleEditorController controller() {
        return new CircleEditorController(MagicCircle.empty("interaction", "Interaction", 1, 4),
                (circle, medium) -> CircleEditorController.BindingResult.accepted("bound"));
    }
}
