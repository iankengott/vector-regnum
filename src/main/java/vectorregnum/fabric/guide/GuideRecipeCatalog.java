package vectorregnum.fabric.guide;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Loads the real checked-in crafting JSON referenced by Field Manual pages. */
public final class GuideRecipeCatalog {
    private final Map<String, GuideRecipe> recipes;

    private GuideRecipeCatalog(Map<String, GuideRecipe> recipes) {
        this.recipes = Map.copyOf(recipes);
    }

    public static GuideRecipeCatalog empty() {
        return new GuideRecipeCatalog(Map.of());
    }

    public static GuideRecipeCatalog load(GuideBook book, ClassLoader loader) throws IOException {
        Objects.requireNonNull(book, "book");
        Objects.requireNonNull(loader, "loader");
        Set<String> recipeIds = new HashSet<>();
        for (GuideChapter chapter : book.chapters()) {
            for (GuidePage page : chapter.pages()) {
                for (GuideElement element : page.elements()) {
                    if (element.type() == GuideElement.Type.RECIPE) {
                        recipeIds.add(element.metadata("recipe"));
                    }
                }
            }
        }
        Map<String, GuideRecipe> loaded = new LinkedHashMap<>();
        for (String recipeId : recipeIds.stream().sorted().toList()) {
            loaded.put(recipeId, loadRecipe(recipeId, loader));
        }
        return new GuideRecipeCatalog(loaded);
    }

    public Optional<GuideRecipe> recipe(String id) {
        return Optional.ofNullable(recipes.get(id));
    }

    public int size() {
        return recipes.size();
    }

    private static GuideRecipe loadRecipe(String recipeId, ClassLoader loader) throws IOException {
        GuideRecipe.requireIdentifier(recipeId, "recipe id");
        String[] parts = recipeId.split(":", 2);
        String resource = "data/" + parts[0] + "/recipe/" + parts[1] + ".json";
        try (InputStream stream = loader.getResourceAsStream(resource)) {
            if (stream == null) throw new IOException("missing guide recipe resource: " + resource);
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return parse(JsonParser.parseReader(reader).getAsJsonObject());
            } catch (RuntimeException exception) {
                throw new IOException("invalid guide recipe " + recipeId + ": "
                        + exception.getMessage(), exception);
            }
        }
    }

    static GuideRecipe parse(JsonObject root) {
        String type = text(root, "type");
        GuideRecipe.Kind kind;
        List<GuideIngredient> slots = emptySlots();
        if (type.equals("minecraft:crafting_shaped")) {
            kind = GuideRecipe.Kind.SHAPED;
            JsonArray pattern = array(root, "pattern");
            if (pattern.isEmpty() || pattern.size() > 3) {
                throw new IllegalArgumentException("shaped pattern height must be 1..3");
            }
            JsonObject key = object(root, "key");
            int width = -1;
            for (int row = 0; row < pattern.size(); row++) {
                String line = pattern.get(row).getAsString();
                if (line.isEmpty() || line.length() > 3 || (width >= 0 && line.length() != width)) {
                    throw new IllegalArgumentException("shaped pattern rows must share width 1..3");
                }
                width = line.length();
                for (int column = 0; column < line.length(); column++) {
                    char symbol = line.charAt(column);
                    if (symbol != ' ') {
                        String keyName = Character.toString(symbol);
                        if (!key.has(keyName)) {
                            throw new IllegalArgumentException("pattern uses missing key " + keyName);
                        }
                        slots.set(row * 3 + column, ingredient(key.get(keyName)));
                    }
                }
            }
        } else if (type.equals("minecraft:crafting_shapeless")) {
            kind = GuideRecipe.Kind.SHAPELESS;
            JsonArray ingredients = array(root, "ingredients");
            if (ingredients.isEmpty() || ingredients.size() > 9) {
                throw new IllegalArgumentException("shapeless ingredient count must be 1..9");
            }
            for (int index = 0; index < ingredients.size(); index++) {
                slots.set(index, ingredient(ingredients.get(index)));
            }
        } else {
            throw new IllegalArgumentException("unsupported guide recipe type: " + type);
        }

        JsonObject result = object(root, "result");
        int count = result.has("count") ? result.get("count").getAsInt() : 1;
        return new GuideRecipe(kind, slots, text(result, "id"), count);
    }

    private static GuideIngredient ingredient(JsonElement value) {
        List<String> choices = new ArrayList<>();
        if (value.isJsonArray()) {
            if (value.getAsJsonArray().isEmpty()) {
                throw new IllegalArgumentException("ingredient alternative list cannot be empty");
            }
            for (JsonElement alternative : value.getAsJsonArray()) {
                choices.add(ingredientChoice(alternative));
            }
        } else {
            choices.add(ingredientChoice(value));
        }
        return new GuideIngredient(choices);
    }

    private static String ingredientChoice(JsonElement value) {
        JsonObject object = value.getAsJsonObject();
        if (object.has("item") == object.has("tag")) {
            throw new IllegalArgumentException("ingredient needs exactly one item or tag");
        }
        return object.has("item")
                ? GuideRecipe.requireIdentifier(text(object, "item"), "ingredient")
                : "#" + GuideRecipe.requireIdentifier(text(object, "tag"), "ingredient tag");
    }

    private static List<GuideIngredient> emptySlots() {
        List<GuideIngredient> slots = new ArrayList<>(9);
        for (int index = 0; index < 9; index++) slots.add(GuideIngredient.empty());
        return slots;
    }

    private static String text(JsonObject object, String key) {
        if (!object.has(key)) throw new IllegalArgumentException("missing " + key);
        return object.get(key).getAsString();
    }

    private static JsonArray array(JsonObject object, String key) {
        if (!object.has(key)) throw new IllegalArgumentException("missing " + key);
        return object.getAsJsonArray(key);
    }

    private static JsonObject object(JsonObject object, String key) {
        if (!object.has(key)) throw new IllegalArgumentException("missing " + key);
        return object.getAsJsonObject(key);
    }
}
