package vectorregnum.neoforge.guide;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Loads the Field Manual without depending on a guidebook mod or Minecraft classes. */
public final class GuideDataLoader {
    public static final String DEFAULT_RESOURCE = "assets/vector_regnum/guide/field_manual.json";

    private GuideDataLoader() { }

    public static GuideBook loadDefault(ClassLoader loader) throws IOException {
        Objects.requireNonNull(loader, "loader");
        try (InputStream stream = loader.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) throw new IOException("missing Field Manual resource: " + DEFAULT_RESOURCE);
            return load(stream);
        }
    }

    public static GuideBook load(InputStream stream) throws IOException {
        Objects.requireNonNull(stream, "stream");
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            try {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                return parseBook(root);
            } catch (RuntimeException exception) {
                throw new IOException("invalid Field Manual data: " + exception.getMessage(), exception);
            }
        }
    }

    private static GuideBook parseBook(JsonObject root) {
        List<GuideChapter> chapters = new ArrayList<>();
        for (JsonElement chapterElement : array(root, "chapters")) {
            JsonObject chapter = chapterElement.getAsJsonObject();
            List<GuidePage> pages = new ArrayList<>();
            for (JsonElement pageElement : array(chapter, "pages")) {
                JsonObject page = pageElement.getAsJsonObject();
                List<GuideElement> elements = new ArrayList<>();
                for (JsonElement visualElement : array(page, "elements")) {
                    JsonObject visual = visualElement.getAsJsonObject();
                    Map<String, String> metadata = new LinkedHashMap<>();
                    for (Map.Entry<String, JsonElement> entry : object(visual, "metadata").entrySet()) {
                        metadata.put(entry.getKey(), entry.getValue().getAsString());
                    }
                    elements.add(new GuideElement(
                            GuideElement.Type.valueOf(text(visual, "type").toUpperCase(Locale.ROOT)),
                            text(visual, "label"), text(visual, "alt"), metadata));
                }
                Set<String> unlocks = new LinkedHashSet<>();
                JsonArray required = page.has("required_unlocks")
                        ? page.getAsJsonArray("required_unlocks") : new JsonArray();
                required.forEach(value -> unlocks.add(value.getAsString()));
                pages.add(new GuidePage(text(page, "id"), text(page, "title"),
                        text(page, "body"), unlocks, elements));
            }
            chapters.add(new GuideChapter(text(chapter, "id"), text(chapter, "title"),
                    text(chapter, "icon"), pages));
        }
        return new GuideBook(text(root, "id"), text(root, "title"),
                root.get("version").getAsInt(), text(root, "theme"), chapters);
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
