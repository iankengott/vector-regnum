package vectorregnum.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable, validated elemental efficiency matrix shared by gameplay frontends. */
public final class ElementalAffinityMatrix {
    public static final int FULL_PERCENT = 100;
    public static final int FAVORED_PERCENT = 75;
    public static final int NEUTRAL_PERCENT = 50;
    public static final int OPPOSED_PERCENT = 25;
    private static final String RESOURCE = "/data/vector_regnum/elemental_affinities.json";

    private final Map<Element, Map<Element, Integer>> percentages;

    private ElementalAffinityMatrix(Map<Element, Map<Element, Integer>> percentages) {
        this.percentages = immutableCopy(percentages);
        validate(this.percentages);
    }

    /** Loads and validates the checked-in canonical data resource. */
    public static ElementalAffinityMatrix canonical() {
        return CanonicalHolder.INSTANCE;
    }

    private static ElementalAffinityMatrix loadCanonical() {
        try (InputStream stream = ElementalAffinityMatrix.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("missing " + RESOURCE);
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return fromJson(json);
        } catch (IOException exception) {
            throw new IllegalStateException("could not read " + RESOURCE, exception);
        }
    }

    private static final class CanonicalHolder {
        private static final ElementalAffinityMatrix INSTANCE = loadCanonical();
    }

    /** Parses a strict matrix document without exposing loader-specific types. */
    public static ElementalAffinityMatrix fromJson(String json) {
        Objects.requireNonNull(json, "json");
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray listed = requiredArray(root, "elements");
            List<Element> expected = List.of(Element.values());
            if (listed.size() != expected.size()) {
                throw invalid("elements must list exactly " + expected.size() + " values");
            }
            for (int index = 0; index < expected.size(); index++) {
                String id = listed.get(index).getAsString();
                if (!expected.get(index).id().equals(id)) {
                    throw invalid("elements must use canonical order at index " + index);
                }
            }

            JsonObject rows = root.has("affinities")
                    ? root.getAsJsonObject("affinities") : root.getAsJsonObject("matrix");
            if (rows == null) throw invalid("missing affinities object");
            Map<Element, Map<Element, Integer>> values = new EnumMap<>(Element.class);
            for (Element rowElement : Element.values()) {
                JsonElement rowValue = rows.get(rowElement.id());
                if (rowValue == null || !rowValue.isJsonObject()) {
                    throw invalid("missing row for " + rowElement.id());
                }
                JsonObject row = rowValue.getAsJsonObject();
                Map<Element, Integer> cells = new EnumMap<>(Element.class);
                for (Element columnElement : Element.values()) {
                    JsonElement cell = row.get(columnElement.id());
                    if (cell == null || !cell.isJsonPrimitive() || !cell.getAsJsonPrimitive().isNumber()) {
                        throw invalid("missing numeric cell " + rowElement.id() + "/"
                                + columnElement.id());
                    }
                    double raw = cell.getAsDouble();
                    if (!Double.isFinite(raw) || raw != Math.rint(raw)
                            || raw < Integer.MIN_VALUE || raw > Integer.MAX_VALUE) {
                        throw invalid("invalid percentage at " + rowElement.id() + "/"
                                + columnElement.id());
                    }
                    cells.put(columnElement, (int) raw);
                }
                if (row.entrySet().size() != expected.size()) {
                    throw invalid("row " + rowElement.id() + " must contain exactly 14 cells");
                }
                values.put(rowElement, cells);
            }
            if (rows.entrySet().size() != expected.size()) {
                throw invalid("affinities must contain exactly 14 rows");
            }
            return new ElementalAffinityMatrix(values);
        } catch (IllegalStateException | UnsupportedOperationException | ClassCastException exception) {
            throw invalid("malformed affinity JSON: " + exception.getMessage());
        }
    }

    public int percentage(Element attunement, Element spell) {
        return row(attunement).get(requireElement(spell));
    }

    /** Efficiency as a multiplier in the closed interval [0.25, 1.0]. */
    public double efficiency(Element attunement, Element spell) {
        return percentage(attunement, spell) / 100.0;
    }

    /** Stability uses the same bounded affinity multiplier as resource draw. */
    public double stabilityEfficiency(Element attunement, Element spell) {
        return efficiency(attunement, spell);
    }

    public double adjustedCost(double base, Element attunement, Element spell) {
        return divideByEfficiency(base, attunement, spell);
    }

    public double adjustedCost(Element attunement, Element spell, double base) {
        return adjustedCost(base, attunement, spell);
    }

    public double upkeepCost(double base, Element attunement, Element spell) {
        return divideByEfficiency(base, attunement, spell);
    }

    public double upkeepCost(Element attunement, Element spell, double base) {
        return upkeepCost(base, attunement, spell);
    }

    public Map<Element, Map<Element, Integer>> percentages() {
        return percentages;
    }

    private double divideByEfficiency(double base, Element attunement, Element spell) {
        if (!Double.isFinite(base) || base < 0.0) {
            throw new IllegalArgumentException("base cost must be finite and non-negative");
        }
        double adjusted = base / efficiency(attunement, spell);
        if (!Double.isFinite(adjusted) || adjusted < 0.0) {
            throw new IllegalArgumentException("adjusted cost overflow");
        }
        return adjusted;
    }

    private Map<Element, Integer> row(Element element) {
        return percentages.get(requireElement(element));
    }

    private static Element requireElement(Element element) {
        return Objects.requireNonNull(element, "element");
    }

    private static JsonArray requiredArray(JsonObject root, String name) {
        JsonElement value = root.get(name);
        if (value == null || !value.isJsonArray()) throw invalid("missing array " + name);
        return value.getAsJsonArray();
    }

    private static Map<Element, Map<Element, Integer>> immutableCopy(
            Map<Element, Map<Element, Integer>> source) {
        Map<Element, Map<Element, Integer>> rows = new EnumMap<>(Element.class);
        source.forEach((row, cells) -> rows.put(Objects.requireNonNull(row),
                Map.copyOf(new EnumMap<>(cells))));
        return Map.copyOf(rows);
    }

    private static void validate(Map<Element, Map<Element, Integer>> values) {
        if (!values.keySet().equals(EnumSet.allOf(Element.class))) {
            throw invalid("matrix must contain every canonical element exactly once");
        }
        for (Element row : Element.values()) {
            Map<Element, Integer> cells = values.get(row);
            if (cells == null || !cells.keySet().equals(values.keySet())) {
                throw invalid("matrix must be a complete 14 by 14 square");
            }
            for (Element column : Element.values()) {
                int value = cells.get(column);
                if (value != FULL_PERCENT && value != FAVORED_PERCENT
                        && value != NEUTRAL_PERCENT && value != OPPOSED_PERCENT) {
                    throw invalid("unsupported affinity percentage " + value);
                }
                if (value != values.get(column).get(row)) {
                    throw invalid("matrix is not symmetric at " + row.id() + "/" + column.id());
                }
                if (row == column && value != FULL_PERCENT) {
                    throw invalid("diagonal affinity must be 100");
                }
            }
        }
        for (Element left : Element.ordinary()) {
            for (Element right : Element.ordinary()) {
                if (values.get(left).get(right) < OPPOSED_PERCENT) {
                    throw invalid("ordinary affinity cannot fall below 25");
                }
            }
            if (values.get(Element.VOID).get(left) != OPPOSED_PERCENT) {
                throw invalid("Void must be opposed to ordinary elements");
            }
            if (values.get(Element.ARCANE).get(left) != NEUTRAL_PERCENT) {
                throw invalid("Arcane must be 50 to ordinary elements");
            }
        }
        if (values.get(Element.VOID).get(Element.ARCANE) != NEUTRAL_PERCENT) {
            throw invalid("Void must be 50 to Arcane");
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
