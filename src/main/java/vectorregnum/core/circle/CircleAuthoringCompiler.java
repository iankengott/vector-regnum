package vectorregnum.core.circle;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import vectorregnum.core.Element;
import vectorregnum.core.Shape;
import vectorregnum.core.Sigil;
import vectorregnum.core.SpellCompiler;

/** Converts geometric authoring data into the compatibility compiler's ordered source. */
public final class CircleAuthoringCompiler {
    private static final Set<String> NO_PARAMETER = Set.of("ORIGIN_SELF", "VECTOR_FORWARD", "EXECUTE");
    private static final Set<String> ONE_NUMBER = Set.of("EXPAND", "AMPLIFY");

    private CircleAuthoringCompiler() {
    }

    public static CircleCompilation compile(MagicCircle circle) {
        List<PlacedSigil> ordered = circle.executionOrder();
        List<CircleDiagnostic> diagnostics = new ArrayList<>();
        List<Sigil> source = new ArrayList<>();

        for (int index = 0; index < ordered.size(); index++) {
            PlacedSigil placed = ordered.get(index);
            validateSigil(placed, index, diagnostics);
            Object[] parameters = placed.parameters().stream()
                    .map(CircleAuthoringCompiler::toCompatibilityValue)
                    .toArray();
            source.add(new Sigil(placed.type(), parameters));
        }
        validateSequence(ordered, diagnostics);

        if (ordered.isEmpty()) {
            diagnostics.add(new CircleDiagnostic(CircleDiagnostic.Severity.ERROR,
                    "EMPTY_CIRCLE", "Place at least one sigil before compiling", null, -1));
        } else if (!ordered.getLast().type().equals("EXECUTE")) {
            PlacedSigil last = ordered.getLast();
            diagnostics.add(new CircleDiagnostic(CircleDiagnostic.Severity.ERROR,
                    "MISSING_TERMINAL_EXECUTE",
                    "The innermost final sigil must be EXECUTE", last.coordinate(), ordered.size() - 1));
        }
        for (int i = 0; i < Math.max(0, ordered.size() - 1); i++) {
            if (ordered.get(i).type().equals("EXECUTE")) {
                diagnostics.add(new CircleDiagnostic(CircleDiagnostic.Severity.ERROR,
                        "EARLY_EXECUTE", "EXECUTE must be the final sigil", ordered.get(i).coordinate(), i));
            }
        }

        boolean errors = diagnostics.stream()
                .anyMatch(d -> d.severity() == CircleDiagnostic.Severity.ERROR);
        return new CircleCompilation(ordered, source, errors ? null : SpellCompiler.compile(source), diagnostics);
    }

    private static void validateSigil(
            PlacedSigil sigil, int index, List<CircleDiagnostic> diagnostics) {
        String type = sigil.type();
        int expected = -1;
        boolean known = true;
        if (NO_PARAMETER.contains(type)) {
            expected = 0;
        } else if (ONE_NUMBER.contains(type)) {
            expected = 1;
        } else if (type.startsWith("ELEMENT_")) {
            expected = 0;
            known = Element.fromId(type.substring("ELEMENT_".length())).isPresent();
        } else if (type.startsWith("SHAPE_")) {
            expected = 0;
            known = Shape.fromId(type.substring("SHAPE_".length())).isPresent();
        } else {
            known = false;
        }

        if (!known) {
            diagnostics.add(error("UNKNOWN_SIGIL", "Unknown sigil " + type, sigil, index));
            return;
        }
        if (sigil.parameters().size() != expected) {
            diagnostics.add(error("PARAMETER_COUNT",
                    type + " expects " + expected + " parameter(s), got " + sigil.parameters().size(),
                    sigil, index));
            return;
        }
        if (expected == 1 && !(sigil.parameters().getFirst() instanceof CircleValue.NumberValue number)) {
            diagnostics.add(error("PARAMETER_TYPE", type + " requires a number", sigil, index));
        } else if (expected == 1) {
            CircleValue.NumberValue number = (CircleValue.NumberValue) sigil.parameters().getFirst();
            double value = number.value().doubleValue();
            if (!Double.isFinite(value) || value <= 0.0) {
                diagnostics.add(error("INVALID_NUMBER", type + " requires a finite number greater than zero",
                        sigil, index));
            }
        }
    }

    private static void validateSequence(
            List<PlacedSigil> ordered, List<CircleDiagnostic> diagnostics) {
        boolean origin = false;
        boolean direction = false;
        boolean element = false;
        boolean shape = false;
        for (int index = 0; index < ordered.size(); index++) {
            PlacedSigil sigil = ordered.get(index);
            String type = sigil.type();
            if (type.equals("ORIGIN_SELF")) {
                if (origin) {
                    diagnostics.add(error("ORIGIN_ALREADY_SET", "Origin is already set", sigil, index));
                } else {
                    origin = true;
                }
            } else if (type.equals("VECTOR_FORWARD")) {
                if (!origin) {
                    diagnostics.add(error("ORIGIN_REQUIRED", "Place an origin before direction", sigil, index));
                } else if (direction) {
                    diagnostics.add(error("VECTOR_ALREADY_SET", "Direction is already set", sigil, index));
                } else {
                    direction = true;
                }
            } else if (type.startsWith("ELEMENT_") && Element.fromId(
                    type.substring("ELEMENT_".length())).isPresent()) {
                if (!origin) {
                    diagnostics.add(error("ORIGIN_REQUIRED", "Place an origin before an element", sigil, index));
                } else if (element) {
                    diagnostics.add(error("ELEMENT_ALREADY_SET", "An element is already applied", sigil, index));
                } else {
                    element = true;
                }
            } else if (type.startsWith("SHAPE_") && Shape.fromId(
                    type.substring("SHAPE_".length())).isPresent()) {
                if (!origin) {
                    diagnostics.add(error("ORIGIN_REQUIRED", "Place an origin before a shape", sigil, index));
                } else if (shape) {
                    diagnostics.add(error("SHAPE_ALREADY_SET", "A shape is already resolved", sigil, index));
                } else if (type.equals("SHAPE_PROJECTILE") && !direction) {
                    diagnostics.add(error("DIRECTION_REQUIRED",
                            "A projectile needs a direction before its shape", sigil, index));
                } else {
                    shape = true;
                }
            } else if (type.equals("EXPAND") && !shape) {
                diagnostics.add(error("SHAPE_REQUIRED", "Place a shape before EXPAND", sigil, index));
            } else if (type.equals("AMPLIFY") && !shape && !element) {
                diagnostics.add(error("NOTHING_TO_AMPLIFY",
                        "Place an element or shape before AMPLIFY", sigil, index));
            } else if (type.equals("EXECUTE") && (!origin || !shape)) {
                diagnostics.add(error("MISSING_COMPONENT",
                        "EXECUTE requires both an origin and a shape", sigil, index));
            }
        }
    }

    private static CircleDiagnostic error(String code, String message, PlacedSigil sigil, int index) {
        return new CircleDiagnostic(CircleDiagnostic.Severity.ERROR, code, message,
                sigil.coordinate(), index);
    }

    private static Object toCompatibilityValue(CircleValue value) {
        return switch (value) {
            case CircleValue.NumberValue number -> number.value();
            case CircleValue.TextValue text -> text.value();
            case CircleValue.BooleanValue bool -> Boolean.toString(bool.value());
        };
    }
}
