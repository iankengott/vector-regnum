package vectorregnum.neoforge.presentation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every Quasar emitter id the Veil backend can request must resolve to a
 * bounded authored motif, so active-Veil rendering never silently depends on a
 * missing asset and never falls back to vanilla particles.
 */
class QuasarEmitterVocabularyTest {
    private static final Path QUASAR_ROOT = locateQuasarRoot();
    private static final List<String> ELEMENTS = List.of("arcane", "fire", "ice", "void",
            "water", "air", "earth", "lightning", "time", "space", "light", "dark",
            "nature", "sound");
    private static final Set<String> REQUIRED_EMITTERS = buildRequiredEmitters();

    @Test
    void everyRequestableEmitterExistsAndNoUnboundedExtrasArePresent() throws IOException {
        for (String emitter : REQUIRED_EMITTERS) {
            assertTrue(Files.isRegularFile(QUASAR_ROOT.resolve(
                            "emitters/presentation/" + emitter + ".json")),
                    "missing required Quasar emitter: presentation/" + emitter);
        }
        try (Stream<Path> files = Files.walk(QUASAR_ROOT.resolve("emitters/presentation"))) {
            Set<String> actual = files.filter(entry -> entry.toString().endsWith(".json"))
                    .map(QUASAR_ROOT.resolve("emitters/presentation")::relativize)
                    .map(Path::toString)
                    .map(id -> id.substring(0, id.length() - ".json".length()))
                    .collect(java.util.stream.Collectors.toSet());
            assertEquals(59, actual.size());
            assertEquals(REQUIRED_EMITTERS, actual);
        }
    }

    @Test
    void everyEmitterReferencesResolvableAssetsAndStaysBounded() throws IOException {
        Pattern maxParticles = Pattern.compile("\"max_particles\"\\s*:\\s*(\\d+)");
        try (Stream<Path> files = Files.walk(QUASAR_ROOT.resolve("emitters"))) {
            for (Path file : files.filter(entry -> entry.toString().endsWith(".json")).toList()) {
                String source = Files.readString(file);
                JsonObject json = JsonParser.parseString(source).getAsJsonObject();
                assertTrue(json.has("emitter_settings"),
                        file + " must declare emitter_settings");
                JsonObject settings = json.getAsJsonObject("emitter_settings");
                assertTrue(settings.has("shape"), file + " must declare a shape");
                String shape = settings.get("shape").getAsString();
                assertTrue(shape.equals("veil:sphere") || shape.equals("veil:torus")
                                || shape.equals("veil:cylinder")
                                || shape.startsWith("vector_regnum:presentation/"),
                        file + " uses an uncurated shape: " + shape);
                assertReferenceExists(file, shape, "modules/emitter/shape/");
                assertReferenceExists(file, settings.get("particle_settings").getAsString(),
                        "modules/emitter/particle/");
                assertReferenceExists(file, json.get("particle_data").getAsString(),
                        "modules/particle_data/");
                Matcher cap = maxParticles.matcher(source);
                assertTrue(cap.find() && Integer.parseInt(cap.group(1)) <= 64,
                        file + " exceeds the 64-particle motif cap");
            }
        }
    }

    private static void assertReferenceExists(Path emitter, String id, String resourceFolder) {
        if (!id.startsWith("vector_regnum:")) return;
        Path resource = QUASAR_ROOT.resolve(resourceFolder
                + id.substring("vector_regnum:".length()) + ".json");
        assertTrue(Files.isRegularFile(resource),
                emitter + " references missing Quasar asset: " + id);
    }

    private static Set<String> buildRequiredEmitters() {
        java.util.HashSet<String> ids = new java.util.HashSet<>();
        for (String element : ELEMENTS) {
            ids.add(element);
            ids.add("ring/" + element);
            ids.add("beam/" + element);
            ids.add("burst/" + element);
        }
        ids.add("smoke");
        ids.add("spark");
        ids.add("light_motif");
        return java.util.Collections.unmodifiableSet(ids);
    }

    private static Path locateQuasarRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            Path resolved = candidate.resolve(
                    "src/main/resources/assets/vector_regnum/quasar");
            if (Files.isDirectory(resolved)) return resolved;
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("could not locate the quasar resource root");
    }
}
