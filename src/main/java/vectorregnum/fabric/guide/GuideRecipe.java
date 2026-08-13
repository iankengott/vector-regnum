package vectorregnum.fabric.guide;

import java.util.List;
import java.util.Objects;

/** Render-neutral, bounded crafting recipe used by the visual Field Manual. */
public record GuideRecipe(Kind kind, List<GuideIngredient> ingredients, String result, int resultCount) {
    public GuideRecipe {
        Objects.requireNonNull(kind, "kind");
        ingredients = List.copyOf(Objects.requireNonNull(ingredients, "ingredients"));
        if (ingredients.size() != 9) {
            throw new IllegalArgumentException("guide recipes need exactly nine display slots");
        }
        if (ingredients.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("guide recipe ingredients cannot be null");
        }
        result = requireIdentifier(result, "result");
        if (resultCount < 1 || resultCount > 99) {
            throw new IllegalArgumentException("guide recipe result count must be 1..99");
        }
    }

    public GuideIngredient ingredient(int row, int column) {
        if (row < 0 || row >= 3 || column < 0 || column >= 3) {
            throw new IndexOutOfBoundsException("guide recipe slot is outside the 3x3 grid");
        }
        return ingredients.get(row * 3 + column);
    }

    static String requireIdentifier(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || !value.contains(":") || value.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(name + " must be a namespaced identifier");
        }
        return value;
    }

    public enum Kind { SHAPED, SHAPELESS }
}
