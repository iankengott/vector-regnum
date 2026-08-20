package vectorregnum.neoforge.guide;

import java.util.Map;
import java.util.Objects;

/** A render-neutral visual block within one Field Manual page. */
public record GuideElement(Type type, String label, String altText, Map<String, String> metadata) {
    public GuideElement {
        Objects.requireNonNull(type, "type");
        label = requireText(label, "label");
        altText = requireText(altText, "altText");
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
        if (metadata.entrySet().stream().anyMatch(entry -> entry.getKey().isBlank()
                || entry.getValue() == null || entry.getValue().isBlank())) {
            throw new IllegalArgumentException("guide metadata keys and values cannot be blank");
        }
        String requiredKey = switch (type) {
            case IMAGE -> "asset";
            case DIAGRAM -> "diagram";
            case RECIPE -> "recipe";
            case LINK -> "target";
            case PONDER -> "scene";
            case EXAMPLE -> "example";
        };
        if (!metadata.containsKey(requiredKey)) {
            throw new IllegalArgumentException(type + " guide element needs metadata." + requiredKey);
        }
    }

    public enum Type { IMAGE, DIAGRAM, RECIPE, LINK, PONDER, EXAMPLE }

    public String metadata(String key) {
        String value = metadata.get(key);
        if (value == null) throw new IllegalArgumentException("missing guide metadata: " + key);
        return value;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " cannot be blank");
        return value;
    }
}
