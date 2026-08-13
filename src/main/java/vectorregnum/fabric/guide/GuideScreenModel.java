package vectorregnum.fabric.guide;

import java.util.List;
import java.util.Objects;

/** Complete immutable state a native client screen needs for one render frame. */
public record GuideScreenModel(String bookTitle, String theme, GuidePage page,
        List<ChapterEntry> navigation, List<SearchResult> searchResults,
        Layout layout, boolean bookmarked, boolean canGoBack) {
    public GuideScreenModel {
        Objects.requireNonNull(bookTitle, "bookTitle");
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(page, "page");
        navigation = List.copyOf(navigation);
        searchResults = List.copyOf(searchResults);
        Objects.requireNonNull(layout, "layout");
    }

    public record ChapterEntry(String id, String title, String icon, List<PageEntry> pages) {
        public ChapterEntry { pages = List.copyOf(pages); }
    }

    public record PageEntry(String id, String title, boolean locked, boolean bookmarked) { }

    public record SearchResult(String pageId, String chapterTitle, String pageTitle,
            boolean locked, String excerpt) { }

    /** Pixel values are already adjusted for the user's independent guide scale. */
    public record Layout(double scale, int navigationWidth, int contentWidth, int columns,
            int baseFontPixels, int lineHeightPixels) {
        public Layout {
            if (!Double.isFinite(scale) || scale <= 0 || navigationWidth < 0 || contentWidth < 1
                    || columns < 1 || baseFontPixels < 1 || lineHeightPixels < baseFontPixels) {
                throw new IllegalArgumentException("invalid guide layout");
            }
        }
    }
}
