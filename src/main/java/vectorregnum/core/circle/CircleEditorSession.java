package vectorregnum.core.circle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/** Server-authoritative editor state with bounded undo and compile preview. */
public final class CircleEditorSession {
    private static final int MAX_UNDO = 100;
    private final Deque<MagicCircle> undo = new ArrayDeque<>();
    private MagicCircle current;
    private List<CircleDiagnostic> lastDiagnostics = List.of();

    public CircleEditorSession(MagicCircle initial) {
        current = Objects.requireNonNull(initial, "initial");
    }

    public MagicCircle current() {
        return current;
    }

    public int undoDepth() {
        return undo.size();
    }

    public List<CircleDiagnostic> lastDiagnostics() {
        return lastDiagnostics;
    }

    public void clearDiagnostics() {
        lastDiagnostics = List.of();
    }

    public EditResult place(CircleCoordinate coordinate, String type) {
        Objects.requireNonNull(coordinate, "coordinate");
        try {
            coordinate.requireInside(current.ringCount(), current.slotsPerRing());
        } catch (IllegalArgumentException e) {
            return failure("POSITION_OUTSIDE_CIRCLE", e.getMessage(), coordinate);
        }
        if (find(coordinate) != null) {
            return failure("POSITION_OCCUPIED", "That circle position already contains a sigil", coordinate);
        }
        try {
            List<PlacedSigil> changed = new ArrayList<>(current.sigils());
            changed.add(new PlacedSigil(coordinate, type));
            apply(changed);
            return success();
        } catch (IllegalArgumentException e) {
            return failure("INVALID_SIGIL", e.getMessage(), coordinate);
        }
    }

    public EditResult remove(CircleCoordinate coordinate) {
        PlacedSigil existing = find(coordinate);
        if (existing == null) {
            return failure("POSITION_EMPTY", "That circle position is already empty", coordinate);
        }
        List<PlacedSigil> changed = new ArrayList<>(current.sigils());
        changed.remove(existing);
        apply(changed);
        return success();
    }

    /** Moves one authored sigil, including its typed parameters, as one undoable edit. */
    public EditResult move(CircleCoordinate source, CircleCoordinate destination) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        try {
            source.requireInside(current.ringCount(), current.slotsPerRing());
        } catch (IllegalArgumentException e) {
            return failure("POSITION_OUTSIDE_CIRCLE", e.getMessage(), source);
        }
        try {
            destination.requireInside(current.ringCount(), current.slotsPerRing());
        } catch (IllegalArgumentException e) {
            return failure("POSITION_OUTSIDE_CIRCLE", e.getMessage(), destination);
        }
        if (source.equals(destination)) {
            return failure("MOVE_SAME_POSITION", "Drag a sigil to a different empty slot", source);
        }
        PlacedSigil existing = find(source);
        if (existing == null) {
            return failure("POSITION_EMPTY", "The dragged circle position is empty", source);
        }
        if (find(destination) != null) {
            return failure("POSITION_OCCUPIED", "Drop sigils only into empty circle positions",
                    destination);
        }
        List<PlacedSigil> changed = new ArrayList<>(current.sigils());
        changed.set(changed.indexOf(existing), new PlacedSigil(destination,
                existing.type(), existing.parameters()));
        apply(changed);
        return success();
    }

    public EditResult parameterize(CircleCoordinate coordinate, List<CircleValue> parameters) {
        Objects.requireNonNull(parameters, "parameters");
        PlacedSigil existing = find(coordinate);
        if (existing == null) {
            return failure("POSITION_EMPTY", "Place a sigil before assigning parameters", coordinate);
        }
        try {
            List<PlacedSigil> changed = new ArrayList<>(current.sigils());
            changed.set(changed.indexOf(existing), existing.withParameters(parameters));
            apply(changed);
            return success();
        } catch (IllegalArgumentException e) {
            return failure("INVALID_PARAMETERS", e.getMessage(), coordinate);
        }
    }

    public EditResult undo() {
        if (undo.isEmpty()) {
            return new EditResult(false, current, List.of(new CircleDiagnostic(
                    CircleDiagnostic.Severity.INFO, "NOTHING_TO_UNDO", "There are no edits to undo", null, -1)));
        }
        current = undo.removeLast();
        return success();
    }

    public CircleCompilation compilePreview() {
        CircleCompilation preview = CircleAuthoringCompiler.compile(current);
        lastDiagnostics = preview.diagnostics();
        return preview;
    }

    private void apply(List<PlacedSigil> changed) {
        if (undo.size() == MAX_UNDO) {
            undo.removeFirst();
        }
        undo.addLast(current);
        current = new MagicCircle(current.schemaVersion(), current.id(), current.name(),
                current.ringCount(), current.slotsPerRing(), changed);
    }

    private PlacedSigil find(CircleCoordinate coordinate) {
        return current.sigils().stream()
                .filter(sigil -> sigil.coordinate().equals(coordinate))
                .findFirst().orElse(null);
    }

    private EditResult success() {
        lastDiagnostics = List.of();
        return new EditResult(true, current, lastDiagnostics);
    }

    private EditResult failure(String code, String message, CircleCoordinate coordinate) {
        lastDiagnostics = List.of(new CircleDiagnostic(
                CircleDiagnostic.Severity.ERROR, code, message, coordinate, -1));
        return new EditResult(false, current, lastDiagnostics);
    }

    public record EditResult(boolean changed, MagicCircle circle, List<CircleDiagnostic> diagnostics) {
        public EditResult {
            Objects.requireNonNull(circle, "circle");
            diagnostics = List.copyOf(diagnostics);
        }
    }
}
