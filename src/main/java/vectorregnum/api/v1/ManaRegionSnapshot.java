package vectorregnum.api.v1;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Immutable bounded result of a read-only loaded mana-region query.
 *
 * <p>The adapter owns the loaded-chunk check and entry traversal. This value
 * carries only the resulting canonical-element summary and explicit query
 * status; it grants no world or reservoir access.</p>
 */
public record ManaRegionSnapshot(String dimensionId, int centerX, int centerY, int centerZ,
        int radius, Map<String, Double> manaByElement, int entriesExamined,
        boolean unloaded, boolean truncated) {
    public static final int MAX_QUERY_RADIUS = 64;
    public static final int MAX_QUERY_ENTRIES = 256;

    public ManaRegionSnapshot {
        ApiValidation.identifier(dimensionId, "dimensionId");
        if (radius < 0 || radius > MAX_QUERY_RADIUS) {
            throw new IllegalArgumentException("radius must be between 0 and " + MAX_QUERY_RADIUS);
        }
        Objects.requireNonNull(manaByElement, "manaByElement");
        if (manaByElement.size() > MAX_QUERY_ENTRIES) {
            throw new IllegalArgumentException("at most " + MAX_QUERY_ENTRIES
                    + " canonical element entries are allowed");
        }
        if (entriesExamined < 0 || entriesExamined > MAX_QUERY_ENTRIES) {
            throw new IllegalArgumentException("entriesExamined must be between 0 and "
                    + MAX_QUERY_ENTRIES);
        }
        TreeMap<String, Double> sorted = new TreeMap<>();
        for (Map.Entry<String, Double> entry : manaByElement.entrySet()) {
            String element = ApiValidation.element(entry.getKey(), "element");
            Double amount = Objects.requireNonNull(entry.getValue(), "mana amount");
            ApiValidation.nonNegative(amount, "mana amount");
            if (sorted.put(element, amount) != null) {
                throw new IllegalArgumentException("manaByElement contains duplicate canonical element "
                        + element);
            }
        }
        if (entriesExamined < sorted.size()) {
            throw new IllegalArgumentException("entriesExamined cannot be less than the summary size");
        }
        manaByElement = Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }
}
