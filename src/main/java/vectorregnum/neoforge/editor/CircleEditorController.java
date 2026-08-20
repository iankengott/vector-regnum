package vectorregnum.neoforge.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import vectorregnum.core.circle.CircleAuthoringCompiler;
import vectorregnum.core.circle.CircleCoordinate;
import vectorregnum.core.circle.CircleDiagnostic;
import vectorregnum.core.circle.CircleEditorSession;
import vectorregnum.core.circle.MagicCircle;
import vectorregnum.core.circle.PlacedSigil;
import vectorregnum.core.circle.SpellMedium;
import vectorregnum.core.circle.Vm2CircleCompiler;
import vectorregnum.core.vm2.Vector3;

/**
 * Render-neutral authoritative editor controller. A Fabric packet handler can
 * own one per player and return snapshots to an otherwise untrusted Screen.
 */
public final class CircleEditorController {
    private static final int MINIMUM_WIDTH = 480;
    private static final int MINIMUM_HEIGHT = 270;

    private final CircleEditorSession editor;
    private final SigilPalette palette;
    private final CompilationGateway compiler;
    private final BindingGateway binder;
    private CircleCoordinate selected;
    private String paletteQuery = "";
    private List<CircleDiagnostic> diagnostics = List.of();
    private boolean bindable;
    private String statusMessage = "Ready";

    public CircleEditorController(MagicCircle initial, SigilPalette palette,
            CompilationGateway compiler, BindingGateway binder) {
        editor = new CircleEditorSession(Objects.requireNonNull(initial, "initial"));
        this.palette = Objects.requireNonNull(palette, "palette");
        this.compiler = Objects.requireNonNull(compiler, "compiler");
        this.binder = Objects.requireNonNull(binder, "binder");
        refreshCompilation();
    }

    public CircleEditorController(MagicCircle initial, BindingGateway binder) {
        this(initial, SigilPalette.defaults(), CompilationGateway.CONTEXT_FREE, binder);
    }

    public CircleEditorScreenModel handle(CircleEditorRequest request,
            int viewportWidth, int viewportHeight) {
        Objects.requireNonNull(request, "request");
        switch (request) {
            case CircleEditorRequest.Select select -> select(select.coordinate());
            case CircleEditorRequest.SearchPalette search -> paletteQuery = search.query();
            case CircleEditorRequest.Place place -> place(place.coordinate(), place.sigilId());
            case CircleEditorRequest.Move move -> move(move.source(), move.destination());
            case CircleEditorRequest.Remove remove -> remove(remove.coordinate());
            case CircleEditorRequest.UpdateParameters update ->
                    updateParameters(update.coordinate(), update.values());
            case CircleEditorRequest.Undo ignored -> undo();
            case CircleEditorRequest.Compile ignored -> refreshCompilation();
            case CircleEditorRequest.CaptureFaceAnchor ignored ->
                    statusMessage = "Requesting a server-validated block face";
            case CircleEditorRequest.ClearAnchor ignored -> statusMessage = "Clearing world anchor";
            case CircleEditorRequest.Bind bind -> bind(bind.medium());
        }
        return snapshot(viewportWidth, viewportHeight);
    }

    public CircleEditorScreenModel snapshot(int viewportWidth, int viewportHeight) {
        if (viewportWidth < MINIMUM_WIDTH || viewportHeight < MINIMUM_HEIGHT) {
            throw new IllegalArgumentException("editor viewport must be at least 480x270");
        }
        MagicCircle circle = editor.current();
        List<CircleEditorScreenModel.Slot> slots = new ArrayList<>(
                circle.ringCount() * circle.slotsPerRing());
        for (int ring = 0; ring < circle.ringCount(); ring++) {
            for (int slot = 0; slot < circle.slotsPerRing(); slot++) {
                CircleCoordinate coordinate = new CircleCoordinate(ring, slot);
                PlacedSigil placed = find(coordinate);
                List<CircleDiagnostic> atSlot = diagnostics.stream()
                        .filter(diagnostic -> diagnostic.location().map(coordinate::equals).orElse(false))
                        .toList();
                slots.add(new CircleEditorScreenModel.Slot(coordinate,
                        placed == null ? null : placed.type(),
                        placed == null ? List.of() : placed.parameters(),
                        coordinate.equals(selected), atSlot));
            }
        }
        SigilPalette.Entry selectedEntry = selected == null ? null : find(selected) == null
                ? null : palette.entry(find(selected).type()).orElse(null);
        boolean compact = viewportWidth < 900;
        int paletteWidth = compact ? Math.max(150, viewportWidth / 4) : 240;
        int inspectorWidth = compact ? Math.max(150, viewportWidth / 4) : 260;
        int canvasWidth = viewportWidth - paletteWidth - inspectorWidth;
        var layout = new CircleEditorScreenModel.Layout(canvasWidth, paletteWidth,
                inspectorWidth, 24, compact);
        return new CircleEditorScreenModel(circle, slots, palette.search(paletteQuery),
                selected, selectedEntry, diagnostics, editor.undoDepth() > 0,
                bindable, statusMessage, layout);
    }

    private void select(CircleCoordinate coordinate) {
        try {
            coordinate.requireInside(editor.current().ringCount(), editor.current().slotsPerRing());
            selected = coordinate;
            statusMessage = find(coordinate) == null ? "Empty slot selected" : "Sigil selected";
        } catch (IllegalArgumentException exception) {
            fail("POSITION_OUTSIDE_CIRCLE", exception.getMessage(), coordinate);
        }
    }

    private void place(CircleCoordinate coordinate, String sigilId) {
        selected = coordinate;
        SigilPalette.Entry entry = palette.entry(sigilId).orElse(null);
        if (entry == null) {
            fail("UNKNOWN_PALETTE_SIGIL", "Unknown palette sigil " + sigilId, coordinate);
            return;
        }
        apply(editor.place(coordinate, sigilId), "Placed " + entry.label());
    }

    private void remove(CircleCoordinate coordinate) {
        selected = coordinate;
        apply(editor.remove(coordinate), "Removed sigil");
    }

    private void move(CircleCoordinate source, CircleCoordinate destination) {
        selected = destination;
        apply(editor.move(source, destination), "Moved sigil");
    }

    private void updateParameters(CircleCoordinate coordinate, List<String> values) {
        selected = coordinate;
        PlacedSigil placed = find(coordinate);
        if (placed == null) {
            fail("POSITION_EMPTY", "Place a sigil before editing parameters", coordinate);
            return;
        }
        SigilPalette.Entry entry = palette.entry(placed.type()).orElse(null);
        if (entry == null) {
            fail("UNKNOWN_PALETTE_SIGIL", "No parameter metadata for " + placed.type(), coordinate);
            return;
        }
        try {
            apply(editor.parameterize(coordinate, entry.parseParameters(values)), "Parameters updated");
        } catch (RuntimeException exception) {
            fail("INVALID_PARAMETERS", exception.getMessage(), coordinate);
        }
    }

    private void undo() {
        CircleEditorSession.EditResult result = editor.undo();
        apply(result, result.changed() ? "Undid last edit" : "Nothing to undo");
    }

    private void bind(SpellMedium medium) {
        refreshCompilation();
        if (!bindable) {
            fail("BIND_BLOCKED", "Resolve compiler diagnostics before binding", selected);
            return;
        }
        BindingResult result = binder.bind(editor.current(), medium);
        statusMessage = result.message();
        if (!result.accepted()) {
            fail("BIND_REJECTED", result.message(), selected);
        }
    }

    private void apply(CircleEditorSession.EditResult result, String successMessage) {
        if (!result.changed()) {
            diagnostics = result.diagnostics();
            bindable = false;
            statusMessage = result.diagnostics().isEmpty()
                    ? successMessage : result.diagnostics().getFirst().message();
            return;
        }
        statusMessage = successMessage;
        refreshCompilation();
    }

    private void refreshCompilation() {
        Compilation result = compiler.compile(editor.current());
        diagnostics = result.diagnostics();
        bindable = result.bindable();
        if (diagnostics.isEmpty()) {
            statusMessage = bindable ? "Circle compiled" : "Circle preview unavailable";
        } else {
            statusMessage = diagnostics.getFirst().message();
        }
    }

    private void fail(String code, String message, CircleCoordinate coordinate) {
        diagnostics = List.of(new CircleDiagnostic(CircleDiagnostic.Severity.ERROR,
                code, message == null || message.isBlank() ? code : message, coordinate, -1));
        bindable = false;
        statusMessage = diagnostics.getFirst().message();
    }

    private PlacedSigil find(CircleCoordinate coordinate) {
        return editor.current().sigils().stream()
                .filter(sigil -> sigil.coordinate().equals(coordinate)).findFirst().orElse(null);
    }

    @FunctionalInterface
    public interface CompilationGateway {
        CompilationGateway CONTEXT_FREE = circle -> {
            if (Vm2CircleCompiler.isVm2Circle(circle)) {
                var compiled = Vm2CircleCompiler.compile(circle, new Vm2CircleCompiler.Context(
                        "editor-preview", Vector3.ZERO, new Vector3(0, 0, 1)));
                return new Compilation(compiled.diagnostics(), !compiled.hasErrors());
            }
            var compiled = CircleAuthoringCompiler.compile(circle);
            return new Compilation(compiled.diagnostics(), !compiled.hasErrors());
        };

        Compilation compile(MagicCircle circle);
    }

    @FunctionalInterface
    public interface BindingGateway {
        BindingResult bind(MagicCircle circle, SpellMedium medium);
    }

    public record Compilation(List<CircleDiagnostic> diagnostics, boolean bindable) {
        public Compilation {
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
            if (bindable && diagnostics.stream().anyMatch(diagnostic ->
                    diagnostic.severity() == CircleDiagnostic.Severity.ERROR)) {
                throw new IllegalArgumentException("an erroneous circle cannot be bindable");
            }
        }
    }

    public record BindingResult(boolean accepted, String message) {
        public BindingResult {
            if (message == null || message.isBlank()) {
                throw new IllegalArgumentException("binding result needs a message");
            }
        }

        public static BindingResult accepted(String message) { return new BindingResult(true, message); }
        public static BindingResult rejected(String message) { return new BindingResult(false, message); }
    }
}
