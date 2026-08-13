package vectorregnum.fabric.editor;

import java.util.List;
import java.util.Objects;
import vectorregnum.core.circle.CircleCoordinate;
import vectorregnum.core.circle.SpellMedium;

/** Closed request protocol suitable for encoding into client-to-server packets. */
public sealed interface CircleEditorRequest permits CircleEditorRequest.Select,
        CircleEditorRequest.SearchPalette, CircleEditorRequest.Place,
        CircleEditorRequest.Move, CircleEditorRequest.Remove,
        CircleEditorRequest.UpdateParameters, CircleEditorRequest.Undo,
        CircleEditorRequest.Compile, CircleEditorRequest.CaptureFaceAnchor,
        CircleEditorRequest.ClearAnchor, CircleEditorRequest.Bind {

    record Select(CircleCoordinate coordinate) implements CircleEditorRequest {
        public Select { Objects.requireNonNull(coordinate, "coordinate"); }
    }

    record SearchPalette(String query) implements CircleEditorRequest {
        public SearchPalette { Objects.requireNonNull(query, "query"); }
    }

    record Place(CircleCoordinate coordinate, String sigilId) implements CircleEditorRequest {
        public Place {
            Objects.requireNonNull(coordinate, "coordinate");
            Objects.requireNonNull(sigilId, "sigilId");
        }
    }

    record Move(CircleCoordinate source, CircleCoordinate destination)
            implements CircleEditorRequest {
        public Move {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(destination, "destination");
        }
    }

    record Remove(CircleCoordinate coordinate) implements CircleEditorRequest {
        public Remove { Objects.requireNonNull(coordinate, "coordinate"); }
    }

    record UpdateParameters(CircleCoordinate coordinate, List<String> values)
            implements CircleEditorRequest {
        public UpdateParameters {
            Objects.requireNonNull(coordinate, "coordinate");
            values = List.copyOf(Objects.requireNonNull(values, "values"));
        }
    }

    record Undo() implements CircleEditorRequest { }
    record Compile() implements CircleEditorRequest { }
    /** Requests a fresh server raycast; it carries no client-authored coordinates. */
    record CaptureFaceAnchor() implements CircleEditorRequest { }
    record ClearAnchor() implements CircleEditorRequest { }

    record Bind(SpellMedium medium) implements CircleEditorRequest {
        public Bind { Objects.requireNonNull(medium, "medium"); }
    }
}
