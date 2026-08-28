package vectorregnum.core.circle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpellArtifactTest {
    private final MagicCircle circle = MagicCircleAuthoringTest.validCircle();

    @Test
    void scrollIsConsumedOnlyAfterOneSuccessfulActivation() {
        SpellArtifact scroll = SpellArtifact.scroll("scroll-1", circle);
        SpellArtifact.Transition first = scroll.recordSuccessfulActivation();
        assertTrue(first.accepted());
        assertEquals(SpellArtifact.State.CONSUMED, first.artifact().state());
        assertEquals(1, first.artifact().successfulActivations());

        SpellArtifact.Transition second = first.artifact().recordSuccessfulActivation();
        assertFalse(second.accepted());
        assertEquals(first.artifact(), second.artifact());
    }

    @Test
    void bookRemainsReusable() {
        SpellArtifact book = SpellArtifact.book("book-1", circle);
        for (int activation = 0; activation < 12; activation++) {
            book = book.recordSuccessfulActivation().artifact();
        }
        assertEquals(SpellArtifact.State.READY, book.state());
        assertEquals(12, book.successfulActivations());
    }

    @Test
    void tabletMustBeInstalledAndThenBecomesPermanent() {
        SpellArtifact tablet = SpellArtifact.tablet("tablet-1", circle);
        assertFalse(tablet.recordSuccessfulActivation().accepted());
        SpellArtifact.WorldAnchor anchor = new SpellArtifact.WorldAnchor("minecraft:overworld", 1, 64, -3);
        SpellArtifact installed = tablet.install(anchor).artifact();
        assertEquals(anchor, installed.installedAt().orElseThrow());
        assertFalse(installed.canBeRemovedFromWorld());
        assertTrue(installed.recordSuccessfulActivation().accepted());
        assertFalse(installed.install(new SpellArtifact.WorldAnchor("minecraft:overworld", 2, 64, -3))
                .accepted());
    }

    @Test
    void engravingIsInstalledButExpendable() {
        SpellArtifact engraving = SpellArtifact.engraving("engraving-1", circle);
        assertFalse(engraving.recordSuccessfulActivation().accepted());
        SpellArtifact installed = engraving.install(
                new SpellArtifact.WorldAnchor("minecraft:overworld", 4, 64, 8)).artifact();
        assertTrue(installed.canBeRemovedFromWorld());
        assertTrue(installed.recordSuccessfulActivation().accepted());
        assertEquals(SpellArtifact.State.INSTALLED, installed.state());
    }

    @Test
    void invalidCrossMediumStatesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SpellArtifact(1, "bad", SpellMedium.BOOK,
                circle, SpellArtifact.State.CONSUMED, null, 1));
        assertThrows(IllegalArgumentException.class, () -> new SpellArtifact(1, "bad", SpellMedium.TABLET,
                circle, SpellArtifact.State.INSTALLED, null, 0));
    }

    @Test
    void artifactPersistenceRoundTripsInstalledTabletAndDetectsCorruption() {
        SpellArtifact installed = SpellArtifact.tablet("tablet-save", circle)
                .install(new SpellArtifact.WorldAnchor("minecraft:the_nether", -12, 70, 44)).artifact()
                .recordSuccessfulActivation().artifact();
        String encoded = SpellArtifactPersistence.encode(installed);
        assertEquals(installed, SpellArtifactPersistence.decode(encoded));
        assertEquals(encoded, SpellArtifactPersistence.encode(SpellArtifactPersistence.decode(encoded)));
        assertThrows(CirclePersistence.PersistenceException.class,
                () -> SpellArtifactPersistence.decode(encoded.replace("TABLET", "SCROLL")));
    }
}
