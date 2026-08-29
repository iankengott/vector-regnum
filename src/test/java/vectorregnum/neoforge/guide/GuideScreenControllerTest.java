package vectorregnum.neoforge.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GuideScreenControllerTest {
    @Test
    void defaultManualLoadsVisualMetadataAndValidLinks() throws IOException {
        GuideBook book = GuideDataLoader.loadDefault(getClass().getClassLoader());
        assertEquals(11, book.version());
        assertTrue(book.chapters().size() >= 4);
        assertTrue(book.page("mana_sources").orElseThrow().elements().stream()
                .anyMatch(element -> element.type() == GuideElement.Type.RECIPE
                        && element.metadata("recipe").equals("vector_regnum:mana_crystal_node")));
        assertTrue(book.page("faults").orElseThrow().elements().stream()
                .anyMatch(element -> element.type() == GuideElement.Type.PONDER));
        assertTrue(book.page("mana_infrastructure").orElseThrow().elements().stream()
                .anyMatch(element -> element.type() == GuideElement.Type.RECIPE
                        && element.metadata("recipe").equals("vector_regnum:resonant_vault")));
        GuidePage media = book.page("spell_media").orElseThrow();
        assertTrue(media.elements().stream().anyMatch(element -> element.type() == GuideElement.Type.RECIPE
                && element.metadata("recipe").equals("vector_regnum:engraved_spell_circle")));
        for (String method : java.util.List.of("BARE", "RITUAL", "ENGRAVING",
                "SPELLBOOK", "SCROLL", "INSTALLED_CIRCLE")) {
            assertTrue(media.body().contains(method), method);
        }
        assertTrue(media.body().contains("/vectorregnum circle bind engraving"));
        GuidePage escrow = book.page("resource_escrow").orElseThrow();
        assertTrue(escrow.body().contains("amethyst_shard=mana"));
        assertTrue(escrow.body().contains("sugar=casting_time"));
        assertTrue(escrow.body().contains("glowstone_dust=upkeep"));
        assertTrue(escrow.body().contains("fermented_spider_eye=instability"));
        for (String command : java.util.List.of(
                "/vectorregnum reagents stage mana|casting_time|upkeep|instability <count>",
                "/vectorregnum reagents stage offering <count>",
                "/vectorregnum reagents clear",
                "/vectorregnum circle quote <method>",
                "/vectorregnum circle ritual",
                "/vectorregnum circle bind engraving")) {
            assertTrue(escrow.body().contains(command), command);
        }
        assertTrue(escrow.body().contains("quartz=ritual_offering"));
        assertTrue(escrow.body().contains("requested and applied"));
        GuidePage persistent = book.page("persistent_magic").orElseThrow();
        assertTrue(persistent.body().contains("/vectorregnum effect status"));
        assertTrue(persistent.body().contains("Restart recovery"));
        assertTrue(persistent.elements().stream()
                .anyMatch(element -> element.type() == GuideElement.Type.PONDER));
        GuidePage expansion = book.page("five_spell_expansion").orElseThrow();
        for (String spell : java.util.List.of(
                "Fireball", "Storm Arc", "Tidal Prison", "Stone Aegis", "Teleport")) {
            assertTrue(expansion.body().contains(spell), spell);
        }
    }

    @Test
    void navigationSearchProgressionBookmarksAndScaleAreUsable() throws IOException {
        GuideScreenController controller = new GuideScreenController(
                GuideDataLoader.loadDefault(getClass().getClassLoader()), Set.of());
        assertEquals("welcome", controller.currentPage().id());
        assertFalse(controller.open("movement_school"));

        controller.setSearchQuery("inverse-square");
        GuideScreenModel compact = controller.snapshot(640, 360);
        assertEquals(0, compact.layout().navigationWidth());
        assertTrue(compact.searchResults().stream().anyMatch(result ->
                result.pageId().equals("mana_sources")));

        controller.updateUnlocks(Set.of("movement_weaving", "mana_storage", "crystal_harvest"));
        assertTrue(controller.open("movement_school"));
        controller.toggleBookmark();
        controller.setScale(99);
        GuideScreenModel wide = controller.snapshot(1280, 720);
        assertEquals(GuideScreenController.MAX_SCALE, wide.layout().scale());
        assertTrue(wide.bookmarked());
        assertTrue(wide.navigation().stream().flatMap(chapter -> chapter.pages().stream())
                .anyMatch(page -> page.id().equals("movement_school") && !page.locked()));
        assertTrue(controller.back());
        assertEquals("welcome", controller.currentPage().id());

        GuideScreenModel tiny = controller.snapshot(240, 160);
        assertEquals(0, tiny.layout().navigationWidth());
        assertTrue(tiny.layout().contentWidth() > 0);
    }

    @Test
    void contextualPageLinksUseTheSameHistoryAndProgressionRules() throws IOException {
        GuideScreenController controller = new GuideScreenController(
                GuideDataLoader.loadDefault(getClass().getClassLoader()), Set.of());
        GuideElement link = controller.currentPage().elements().stream()
                .filter(element -> element.type() == GuideElement.Type.LINK).findFirst().orElseThrow();
        assertTrue(controller.follow(link));
        assertEquals("mana_sources", controller.currentPage().id());
        assertTrue(controller.back());
        assertEquals("welcome", controller.currentPage().id());

        assertTrue(controller.openContext("vector_regnum:mana_crystal_node"));
        assertEquals("mana_sources", controller.currentPage().id());
        assertFalse(controller.openContext("vector_regnum:spell_scroll"));
        controller.updateUnlocks(Set.of("crystal_harvest"));
        assertTrue(controller.openContext("vector_regnum:spell_scroll"));
        assertEquals("spell_media", controller.currentPage().id());
    }
}
