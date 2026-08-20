package vectorregnum.neoforge.editor;

import java.util.List;
import java.util.Objects;
import vectorregnum.core.circle.CircleCoordinate;
import vectorregnum.core.circle.CircleDiagnostic;
import vectorregnum.core.circle.CircleValue;
import vectorregnum.core.circle.MagicCircle;

/** Complete immutable state needed to render one graphical editor frame. */
public record CircleEditorScreenModel(
        MagicCircle circle,
        List<Slot> slots,
        List<SigilPalette.Entry> palette,
        CircleCoordinate selected,
        SigilPalette.Entry selectedSigil,
        List<CircleDiagnostic> diagnostics,
        boolean canUndo,
        boolean bindable,
        String statusMessage,
        Layout layout) {
    public CircleEditorScreenModel {
        Objects.requireNonNull(circle, "circle");
        slots = List.copyOf(Objects.requireNonNull(slots, "slots"));
        palette = List.copyOf(Objects.requireNonNull(palette, "palette"));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        Objects.requireNonNull(statusMessage, "statusMessage");
        Objects.requireNonNull(layout, "layout");
    }

    public record Slot(CircleCoordinate coordinate, String sigilId, List<CircleValue> parameters,
                       boolean selected, List<CircleDiagnostic> diagnostics) {
        public Slot {
            Objects.requireNonNull(coordinate, "coordinate");
            parameters = List.copyOf(parameters);
            diagnostics = List.copyOf(diagnostics);
        }

        public boolean occupied() { return sigilId != null; }
        public boolean hasError() {
            return diagnostics.stream().anyMatch(diagnostic ->
                    diagnostic.severity() == CircleDiagnostic.Severity.ERROR);
        }
    }

    /** Pixel sizes are suggestions; a Minecraft Screen remains responsible for drawing. */
    public record Layout(int canvasWidth, int paletteWidth, int inspectorWidth,
                         int minimumTouchTarget, boolean compact) {
        public Layout {
            if (canvasWidth < 1 || paletteWidth < 1 || inspectorWidth < 1 || minimumTouchTarget < 1) {
                throw new IllegalArgumentException("invalid editor layout");
            }
        }
    }
}
