package vectorregnum.fabric.guide;

import java.util.List;
import java.util.Objects;

/** A top-level navigation group in the visual Field Manual. */
public record GuideChapter(String id, String title, String icon, List<GuidePage> pages) {
    public GuideChapter {
        id = GuidePage.requireId(id);
        title = GuidePage.requireText(title, "title");
        icon = GuidePage.requireText(icon, "icon");
        pages = List.copyOf(Objects.requireNonNull(pages, "pages"));
        if (pages.isEmpty()) throw new IllegalArgumentException("guide chapter cannot be empty");
        if (pages.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("guide pages cannot contain null");
        }
    }
}
