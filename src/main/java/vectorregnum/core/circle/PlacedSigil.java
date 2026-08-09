package vectorregnum.core.circle;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** A sigil and its ordered parameters at one circle position. */
public record PlacedSigil(CircleCoordinate coordinate, String type, List<CircleValue> parameters) {
    private static final Pattern TYPE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    public PlacedSigil {
        Objects.requireNonNull(coordinate, "coordinate");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(parameters, "parameters");
        if (!TYPE.matcher(type).matches()) {
            throw new IllegalArgumentException("sigil type must match " + TYPE.pattern());
        }
        if (parameters.size() > 16) {
            throw new IllegalArgumentException("a sigil cannot have more than 16 parameters");
        }
        parameters = List.copyOf(parameters);
        if (parameters.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("sigil parameters cannot contain null");
        }
    }

    public PlacedSigil(CircleCoordinate coordinate, String type) {
        this(coordinate, type, List.of());
    }

    public PlacedSigil withParameters(List<CircleValue> newParameters) {
        return new PlacedSigil(coordinate, type, newParameters);
    }
}
