package vectorregnum.neoforge.guide;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Guards the render choices that made the native manual hard to read. */
class FieldManualVisualPolicyTest {
    private static final Path SCREEN_SOURCE = locateRepositoryRoot().resolve(
            "src/main/java/vectorregnum/neoforge/guide/FieldManualScreen.java");

    @Test
    void darkInkNeverUsesMinecraftTextShadow() throws IOException {
        String source = Files.readString(SCREEN_SOURCE);

        assertFalse(source.contains("INK, true, scale"),
                "scaled dark headings must not render a duplicate shadow on parchment");
        assertTrue(source.contains("x + 20, y + 4, INK, false"),
                "navigation chapter headings must explicitly disable text shadow");
    }

    @Test
    void guideImagesScaleTheEntireSourceTexture() throws IOException {
        String source = Files.readString(SCREEN_SOURCE);

        assertFalse(source.contains(
                        "context.blit(texture, x, y, 0, 0, width, height, sourceWidth, sourceHeight)"),
                "display dimensions must not crop the sampled source region");
        assertTrue(source.contains(
                        "context.blit(texture, x, y, width, height, 0.0F, 0.0F,"),
                "guide images must use the full-source scaled blit overload");
        assertTrue(source.contains(
                        "sourceWidth, sourceHeight, sourceWidth, sourceHeight)"),
                "the full texture must be sampled when scaling an illustration");
    }

    @Test
    void compactManualFitsIllustrationsInsideTheInitialViewport() throws IOException {
        String source = Files.readString(SCREEN_SOURCE);

        assertTrue(source.contains("MAX_COMPACT_IMAGE_SIZE = 80"),
                "compact screens need a bounded illustration size so the first card is visible");
        assertTrue(source.contains("compactToolbar() ? MAX_COMPACT_IMAGE_SIZE : Integer.MAX_VALUE"),
                "the illustration cap must apply only to compact layouts");
        assertTrue(source.contains("fitImage(width, height, maximum)"),
                "image width and height must shrink together instead of distorting or clipping");
    }

    private static Path locateRepositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("settings.gradle"))) return candidate;
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("could not locate repository root");
    }
}
