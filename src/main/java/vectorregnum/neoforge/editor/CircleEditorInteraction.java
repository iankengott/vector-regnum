package vectorregnum.neoforge.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import vectorregnum.core.circle.CircleCoordinate;
import vectorregnum.core.circle.CircleValue;

/**
 * Loader-neutral interaction rules shared by the native screen and focused
 * tests. Keeping click intent here avoids optimistic placement requests for
 * occupied slots and makes parameter entry predictable.
 */
public final class CircleEditorInteraction {
    private CircleEditorInteraction() {
    }

    public static CircleEditorRequest primaryClick(CircleEditorScreenModel model,
            CircleCoordinate coordinate, String paletteSigil) {
        CircleEditorScreenModel.Slot slot = slot(model, coordinate);
        if (paletteSigil == null || slot.occupied()) {
            return new CircleEditorRequest.Select(coordinate);
        }
        return new CircleEditorRequest.Place(coordinate, paletteSigil);
    }

    public static CircleEditorRequest secondaryClick(CircleEditorScreenModel model,
            CircleCoordinate coordinate) {
        return slot(model, coordinate).occupied()
                ? new CircleEditorRequest.Remove(coordinate)
                : new CircleEditorRequest.Select(coordinate);
    }

    /**
     * Resolves a completed drag without mutating local state. Palette drags
     * place a new sigil; circle drags atomically move an existing sigil and
     * its parameters. Occupied or identical destinations are rejected before
     * a packet is sent, while the server repeats all validation authoritatively.
     */
    public static Optional<CircleEditorRequest> dragPlacement(CircleEditorScreenModel model,
            CircleCoordinate source, CircleCoordinate destination, String paletteSigil) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(destination, "destination");
        if (slot(model, destination).occupied()) {
            return Optional.empty();
        }
        if (source != null) {
            if (source.equals(destination) || !slot(model, source).occupied()) {
                return Optional.empty();
            }
            return Optional.of(new CircleEditorRequest.Move(source, destination));
        }
        if (paletteSigil == null || paletteSigil.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new CircleEditorRequest.Place(destination, paletteSigil));
    }

    /**
     * Splits comma/whitespace-separated parameters while preserving quoted
     * text. Quotes may follow a type prefix, for example
     * {@code text:"named target"}.
     */
    public static List<String> parseParameterInput(String input) {
        Objects.requireNonNull(input, "input");
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        char quote = 0;
        boolean escaped = false;
        for (int index = 0; index < input.length(); index++) {
            char current = input.charAt(index);
            if (escaped) {
                value.append(current);
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else if (quote != 0) {
                if (current == quote) {
                    quote = 0;
                } else {
                    value.append(current);
                }
            } else if (current == '\'' || current == '"') {
                quote = current;
            } else if (current == ',' || Character.isWhitespace(current)) {
                add(values, value);
            } else {
                value.append(current);
            }
        }
        if (escaped) {
            throw new IllegalArgumentException("parameter input cannot end with an escape");
        }
        if (quote != 0) {
            throw new IllegalArgumentException("parameter quote is not closed");
        }
        add(values, value);
        return List.copyOf(values);
    }

    /** Formats a stored value so feeding it back through the text field is lossless. */
    public static String formatParameter(CircleValue value) {
        Objects.requireNonNull(value, "value");
        return switch (value) {
            case CircleValue.NumberValue number -> number.canonicalText();
            case CircleValue.BooleanValue bool -> Boolean.toString(bool.value());
            case CircleValue.TextValue text -> formatText(text.value());
        };
    }

    /** Encodes already-tokenized values for the packet/command text boundary. */
    public static String encodeParameterInput(List<String> values) {
        return List.copyOf(Objects.requireNonNull(values, "values")).stream()
                .map(CircleEditorInteraction::quoteToken)
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }

    private static CircleEditorScreenModel.Slot slot(CircleEditorScreenModel model,
            CircleCoordinate coordinate) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(coordinate, "coordinate");
        return model.slots().stream()
                .filter(candidate -> candidate.coordinate().equals(coordinate))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("slot is outside the editor circle"));
    }

    private static void add(List<String> values, StringBuilder value) {
        if (!value.isEmpty()) {
            values.add(value.toString());
            value.setLength(0);
        }
    }

    private static String formatText(String text) {
        boolean quote = text.chars().anyMatch(character -> Character.isWhitespace(character)
                || character == ',' || character == '"' || character == '\\');
        if (!quote) {
            return "text:" + text;
        }
        return "text:\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String quoteToken(String value) {
        Objects.requireNonNull(value, "values cannot contain null");
        boolean quote = value.isEmpty() || value.chars().anyMatch(character ->
                Character.isWhitespace(character) || character == ','
                        || character == '"' || character == '\\');
        if (!quote) {
            return value;
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
