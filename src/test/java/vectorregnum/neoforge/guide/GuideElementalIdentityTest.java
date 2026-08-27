package vectorregnum.neoforge.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class GuideElementalIdentityTest {
    @Test
    void manualV9TeachesPermanentIdentityAndMutableAttunement() throws IOException {
        GuideBook book = GuideDataLoader.loadDefault(getClass().getClassLoader());
        assertEquals(9, book.version());
        GuidePage page = book.page("elemental_identity").orElseThrow();
        assertTrue(page.body().contains("permanent natural element"));
        assertTrue(page.body().contains("mutable"));
        for (String tuningItem : java.util.List.of("prismarine crystals", "blaze powder",
                "feather", "clay ball", "copper ingot", "clock", "ender pearl",
                "glowstone dust", "ink sac", "wheat seeds", "snowball", "echo shard",
                "ender eye", "amethyst shard")) {
            assertTrue(page.body().toLowerCase(java.util.Locale.ROOT).contains(tuningItem),
                    tuningItem);
        }
        GuideElement example = page.elements().stream()
                .filter(element -> element.type() == GuideElement.Type.EXAMPLE)
                .findFirst().orElseThrow();
        assertEquals("/vectorregnum mana attune ice", example.metadata("command"));
    }
}
