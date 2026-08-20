package vectorregnum.neoforge.guide;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * One recipe slot whose choices may be concrete items or item tags. Keeping
 * tags intact lets the client cycle the live registry instead of freezing a
 * datapack-defined ingredient to whichever item happened to load first.
 */
public record GuideIngredient(List<String> choices) {
    public static final int MAX_DISPLAY_CHOICES = 64;

    public GuideIngredient {
        choices = List.copyOf(Objects.requireNonNull(choices, "choices"));
        if (choices.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("guide ingredient choices cannot contain null");
        }
        for (String choice : choices) {
            String identifier = choice.startsWith("#") ? choice.substring(1) : choice;
            GuideRecipe.requireIdentifier(identifier, "ingredient choice");
        }
    }

    public static GuideIngredient empty() {
        return new GuideIngredient(List.of());
    }

    public boolean isEmpty() {
        return choices.isEmpty();
    }

    public boolean containsTag() {
        return choices.stream().anyMatch(choice -> choice.startsWith("#"));
    }

    /** Returns a stable, de-duplicated and bounded list of concrete item ids. */
    public List<String> displayChoices(Function<String, List<String>> tagResolver) {
        Objects.requireNonNull(tagResolver, "tagResolver");
        LinkedHashSet<String> resolved = new LinkedHashSet<>();
        for (String choice : choices) {
            List<String> additions = choice.startsWith("#")
                    ? Objects.requireNonNull(tagResolver.apply(choice.substring(1)), "resolved tag")
                    : List.of(choice);
            for (String addition : additions) {
                GuideRecipe.requireIdentifier(addition, "resolved ingredient");
                resolved.add(addition);
                if (resolved.size() == MAX_DISPLAY_CHOICES) {
                    return List.copyOf(resolved);
                }
            }
        }
        return List.copyOf(resolved);
    }

    public String displayChoice(long cycle, Function<String, List<String>> tagResolver) {
        List<String> resolved = displayChoices(tagResolver);
        if (resolved.isEmpty()) return "";
        return resolved.get(Math.floorMod(cycle, resolved.size()));
    }

    public String sourceDescription() {
        if (choices.isEmpty()) return "Empty slot";
        List<String> labels = new ArrayList<>(choices.size());
        for (String choice : choices) {
            labels.add(choice.startsWith("#") ? "tag " + choice : choice);
        }
        return String.join(" or ", labels);
    }
}
