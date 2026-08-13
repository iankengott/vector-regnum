package vectorregnum.fabric.guide;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** One searchable, progression-aware Field Manual page. */
public record GuidePage(String id, String title, String body, Set<String> requiredUnlocks,
        List<GuideElement> elements) {
    public GuidePage {
        id = requireId(id);
        title = requireText(title, "title");
        body = requireText(body, "body");
        requiredUnlocks = Set.copyOf(Objects.requireNonNull(requiredUnlocks, "requiredUnlocks"));
        elements = List.copyOf(Objects.requireNonNull(elements, "elements"));
        if (requiredUnlocks.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("unlock ids cannot be blank");
        }
        if (elements.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("guide elements cannot contain null");
        }
    }

    public boolean unlockedBy(Set<String> unlocks) {
        return unlocks.containsAll(requiredUnlocks);
    }

    public boolean matches(String normalizedQuery) {
        if (normalizedQuery.isBlank()) return true;
        String searchable = title + " " + body + " " + elements.stream()
                .map(element -> element.label() + " " + element.altText() + " "
                        + String.join(" ", element.metadata().values()))
                .reduce("", (left, right) -> left + " " + right);
        return searchable.toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    static String requireId(String value) {
        requireText(value, "id");
        if (!value.matches("[a-z0-9][a-z0-9_./-]{0,95}")) {
            throw new IllegalArgumentException("invalid guide id: " + value);
        }
        return value;
    }

    static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " cannot be blank");
        return value;
    }
}
