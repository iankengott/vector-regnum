package vectorregnum.neoforge.editor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import vectorregnum.core.circle.CircleValue;

/** Immutable, searchable metadata for a discoverable graphical sigil palette. */
public final class SigilPalette {
    private final List<Entry> entries;
    private final Map<String, Entry> byId;

    public SigilPalette(List<Entry> entries) {
        this.entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        Map<String, Entry> indexed = new LinkedHashMap<>();
        for (Entry entry : this.entries) {
            Objects.requireNonNull(entry, "entries cannot contain null");
            if (indexed.putIfAbsent(entry.id(), entry) != null) {
                throw new IllegalArgumentException("duplicate palette sigil " + entry.id());
            }
        }
        byId = Map.copyOf(indexed);
    }

    public List<Entry> entries() {
        return entries;
    }

    public Optional<Entry> entry(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<Entry> search(String query) {
        String normalized = Objects.requireNonNull(query, "query").strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return entries;
        }
        return entries.stream().filter(entry -> entry.searchableText().contains(normalized)).toList();
    }

    public static SigilPalette defaults() {
        List<Entry> entries = new ArrayList<>();
        add(entries, "ORIGIN_SELF", Category.ORIGIN, "Self origin", "Begin at the caster");
        add(entries, "VECTOR_FORWARD", Category.DIRECTION, "Forward vector", "Use the caster's look direction");
        for (String element : List.of("FIRE", "FROST", "ARCANE", "VOID")) {
            add(entries, "ELEMENT_" + element, Category.ELEMENT, title(element),
                    "Apply " + element.toLowerCase(Locale.ROOT) + " resonance");
        }
        add(entries, "SHAPE_PROJECTILE", Category.SHAPE, "Projectile", "Resolve as a directed projectile");
        add(entries, "SHAPE_AURA", Category.SHAPE, "Aura", "Resolve around the origin");
        add(entries, "EXPAND", Category.MODIFIER, "Expand", "Increase the resolved shape size", number("amount"));
        add(entries, "AMPLIFY", Category.MODIFIER, "Amplify", "Increase elemental or shape intensity", number("amount"));

        for (String id : List.of("VM_PUSH_SELF", "VM_PUSH_ORIGIN", "VM_PUSH_LOOK")) {
            add(entries, id, Category.VALUE, title(id), "Push a contextual typed value");
        }
        add(entries, "VM_PUSH_NUMBER", Category.VALUE, "Number", "Push a number", number("value"));
        add(entries, "VM_PUSH_BOOLEAN", Category.VALUE, "Boolean", "Push true or false", bool("value"));
        add(entries, "VM_PUSH_VECTOR", Category.VALUE, "Vector", "Push an x/y/z vector",
                number("x"), number("y"), number("z"));
        add(entries, "VM_PUSH_POINT", Category.VALUE, "Point", "Push an x/y/z point",
                number("x"), number("y"), number("z"));
        add(entries, "VM_PUSH_ENTITY", Category.VALUE, "Entity", "Push an entity UUID", text("uuid"));
        entries.add(new Entry("VM_PUSH_POINT_LIST", Category.VALUE, "Point list",
                "Push one or more x/y/z points", List.of(number("x"), number("y"), number("z")), true));

        for (String id : List.of("VM_POP", "VM_DUP")) {
            add(entries, id, Category.MEMORY, title(id), "Manipulate stack memory");
        }
        for (String id : List.of("VM_ADD", "VM_SUBTRACT", "VM_MULTIPLY", "VM_DIVIDE",
                "VM_EQUALS", "VM_LESS_THAN", "VM_GREATER_THAN", "VM_NOT", "VM_AND", "VM_OR")) {
            add(entries, id, Category.LOGIC, title(id), "Apply a typed arithmetic or logic operation");
        }
        add(entries, "VM_JUMP", Category.CONTROL, "Jump", "Jump to an instruction", integer("target"));
        add(entries, "VM_JUMP_IF_FALSE", Category.CONTROL, "Conditional jump",
                "Jump when the top boolean is false", integer("target"));
        add(entries, "VM_LOOP", Category.CONTROL, "Bounded loop", "Repeat from target up to a limit",
                integer("target"), integer("limit"));
        add(entries, "VM_DELAY", Category.CONTROL, "Delay", "Yield for ticks", integer("ticks"));
        add(entries, "VM_DURATION", Category.CONTROL, "Duration", "Set effect duration", integer("ticks"));

        for (String id : List.of("VM_SELECT_RADIUS", "VM_SELECT_HOSTILE", "VM_RAYCAST_ENTITIES")) {
            add(entries, id, Category.PERCEPTION, title(id), "Select a bounded entity list",
                    number("range"), integer("limit"));
        }
        for (String id : List.of("VM_IMPULSE", "VM_ACCELERATION", "VM_DAMPING",
                "VM_FOLLOW_PATH", "VM_MOVE_TOWARD", "VM_KEEP_DISTANCE")) {
            add(entries, id, Category.PHYSICS, title(id), "Emit a bounded physics command",
                    number("work"), number("rarity"));
        }
        add(entries, "VM_CREATE_FORM", Category.CREATION, "Create form",
                "Create a bounded server-authoritative material form",
                text("material"), text("form"), number("volume"), integer("duration"), bool("permanent"));
        add(entries, "EXECUTE", Category.TERMINAL, "Execute", "Finish the circle and cast");
        return new SigilPalette(entries);
    }

    private static void add(List<Entry> entries, String id, Category category,
            String label, String description, ParameterSpec... parameters) {
        entries.add(new Entry(id, category, label, description, List.of(parameters), false));
    }

    private static ParameterSpec number(String name) {
        return new ParameterSpec(name, ParameterKind.NUMBER, "Decimal number");
    }

    private static ParameterSpec integer(String name) {
        return new ParameterSpec(name, ParameterKind.NON_NEGATIVE_INTEGER, "Whole number, zero or greater");
    }

    private static ParameterSpec bool(String name) {
        return new ParameterSpec(name, ParameterKind.BOOLEAN, "true or false");
    }

    private static ParameterSpec text(String name) {
        return new ParameterSpec(name, ParameterKind.TEXT, "Text value");
    }

    private static String title(String id) {
        String value = id.startsWith("VM_") ? id.substring(3) : id;
        String[] words = value.toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    public enum Category {
        ORIGIN, DIRECTION, ELEMENT, SHAPE, MODIFIER, VALUE, MEMORY,
        LOGIC, CONTROL, PERCEPTION, PHYSICS, TERMINAL
        , CREATION
    }

    public enum ParameterKind {
        NUMBER,
        NON_NEGATIVE_INTEGER,
        BOOLEAN,
        TEXT;

        CircleValue parse(String raw) {
            String value = Objects.requireNonNull(raw, "parameter value").strip();
            if (value.isEmpty()) throw new IllegalArgumentException("parameter cannot be blank");
            return switch (this) {
                case NUMBER -> new CircleValue.NumberValue(value);
                case NON_NEGATIVE_INTEGER -> {
                    BigDecimal number = new BigDecimal(value).stripTrailingZeros();
                    if (number.scale() > 0 || number.signum() < 0) {
                        throw new IllegalArgumentException("expected a whole number, zero or greater");
                    }
                    yield new CircleValue.NumberValue(number);
                }
                case BOOLEAN -> {
                    if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                        throw new IllegalArgumentException("expected true or false");
                    }
                    yield new CircleValue.BooleanValue(Boolean.parseBoolean(value));
                }
                case TEXT -> new CircleValue.TextValue(value.startsWith("text:")
                        ? value.substring("text:".length()) : value);
            };
        }
    }

    public record ParameterSpec(String name, ParameterKind kind, String hint) {
        public ParameterSpec {
            if (name == null || name.isBlank() || kind == null || hint == null || hint.isBlank()) {
                throw new IllegalArgumentException("invalid palette parameter metadata");
            }
        }
    }

    public record Entry(String id, Category category, String label, String description,
                        List<ParameterSpec> parameters, boolean repeatingParameters) {
        public Entry {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(description, "description");
            parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
            if (!id.matches("[A-Z][A-Z0-9_]{0,63}") || label.isBlank() || description.isBlank()
                    || parameters.stream().anyMatch(Objects::isNull)
                    || (repeatingParameters && parameters.isEmpty())) {
                throw new IllegalArgumentException("invalid palette entry " + id);
            }
        }

        public List<CircleValue> parseParameters(List<String> rawValues) {
            List<String> values = List.copyOf(Objects.requireNonNull(rawValues, "rawValues"));
            int groupSize = parameters.size();
            if ((!repeatingParameters && values.size() != groupSize)
                    || (repeatingParameters && (values.size() < groupSize || values.size() % groupSize != 0))) {
                String expected = repeatingParameters ? "one or more groups of " + groupSize : Integer.toString(groupSize);
                throw new IllegalArgumentException(id + " expects " + expected + " parameter(s), got " + values.size());
            }
            List<CircleValue> parsed = new ArrayList<>(values.size());
            for (int index = 0; index < values.size(); index++) {
                ParameterSpec spec = parameters.get(index % groupSize);
                try {
                    parsed.add(spec.kind().parse(values.get(index)));
                } catch (RuntimeException exception) {
                    throw new IllegalArgumentException(spec.name() + ": " + exception.getMessage(), exception);
                }
            }
            return List.copyOf(parsed);
        }

        private String searchableText() {
            return (id + " " + category + " " + label + " " + description + " "
                    + parameters.stream().map(ParameterSpec::name).reduce("", (a, b) -> a + " " + b))
                    .toLowerCase(Locale.ROOT);
        }
    }
}
