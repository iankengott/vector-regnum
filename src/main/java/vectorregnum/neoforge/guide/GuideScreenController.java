package vectorregnum.neoforge.guide;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Render-neutral controller for the native Field Manual screen. The eventual
 * Minecraft Screen only needs to translate clicks/keys into these operations.
 */
public final class GuideScreenController {
    public static final double MIN_SCALE = 0.75;
    public static final double MAX_SCALE = 1.75;
    private static final int MAX_HISTORY = 64;

    private final GuideBook book;
    private final Set<String> unlocks = new HashSet<>();
    private final Set<String> bookmarks = new HashSet<>();
    private final Deque<String> history = new ArrayDeque<>();
    private String currentPageId;
    private String searchQuery = "";
    private double scale = 1.0;

    public GuideScreenController(GuideBook book, Set<String> initialUnlocks) {
        this.book = Objects.requireNonNull(book, "book");
        updateUnlocks(initialUnlocks);
        this.currentPageId = firstUnlockedPage().id();
    }

    public GuideBook book() { return book; }
    public GuidePage currentPage() { return book.page(currentPageId).orElseThrow(); }
    public double scale() { return scale; }
    public Set<String> bookmarks() { return Set.copyOf(bookmarks); }

    public void updateUnlocks(Set<String> availableUnlocks) {
        Objects.requireNonNull(availableUnlocks, "availableUnlocks");
        if (availableUnlocks.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("unlocks cannot contain null");
        }
        unlocks.clear();
        unlocks.addAll(availableUnlocks);
        if (currentPageId != null && !currentPage().unlockedBy(unlocks)) {
            history.clear();
            currentPageId = firstUnlockedPage().id();
        }
    }

    public boolean open(String pageId) {
        GuidePage page = book.page(Objects.requireNonNull(pageId, "pageId")).orElse(null);
        if (page == null || !page.unlockedBy(unlocks)) return false;
        if (!page.id().equals(currentPageId)) {
            if (history.size() == MAX_HISTORY) history.removeFirst();
            history.addLast(currentPageId);
            currentPageId = page.id();
        }
        return true;
    }

    public boolean follow(GuideElement element) {
        Objects.requireNonNull(element, "element");
        return element.type() == GuideElement.Type.LINK && open(element.metadata("target"));
    }

    /** Opens the page registered for an item/block id, while retaining unlock gating. */
    public boolean openContext(String contentId) {
        Objects.requireNonNull(contentId, "contentId");
        return book.contextPage(contentId).map(page -> open(page.id())).orElse(false);
    }

    public boolean back() {
        while (!history.isEmpty()) {
            String candidate = history.removeLast();
            GuidePage page = book.page(candidate).orElse(null);
            if (page != null && page.unlockedBy(unlocks)) {
                currentPageId = candidate;
                return true;
            }
        }
        return false;
    }

    public void toggleBookmark() {
        if (!bookmarks.remove(currentPageId)) bookmarks.add(currentPageId);
    }

    public void setSearchQuery(String query) {
        searchQuery = Objects.requireNonNull(query, "query").strip()
                .toLowerCase(Locale.ROOT);
    }

    public void setScale(double requestedScale) {
        if (!Double.isFinite(requestedScale)) throw new IllegalArgumentException("scale must be finite");
        scale = Math.clamp(requestedScale, MIN_SCALE, MAX_SCALE);
    }

    public GuideScreenModel snapshot(int viewportWidth, int viewportHeight) {
        if (viewportWidth < 240 || viewportHeight < 160) {
            throw new IllegalArgumentException("guide viewport is too small");
        }
        List<GuideScreenModel.ChapterEntry> navigation = new ArrayList<>();
        List<GuideScreenModel.SearchResult> results = new ArrayList<>();
        for (GuideChapter chapter : book.chapters()) {
            List<GuideScreenModel.PageEntry> pageEntries = new ArrayList<>();
            for (GuidePage page : chapter.pages()) {
                boolean locked = !page.unlockedBy(unlocks);
                pageEntries.add(new GuideScreenModel.PageEntry(page.id(), page.title(), locked,
                        bookmarks.contains(page.id())));
                if (!searchQuery.isBlank() && page.matches(searchQuery)) {
                    results.add(new GuideScreenModel.SearchResult(page.id(), chapter.title(), page.title(),
                            locked, excerpt(page.body(), searchQuery)));
                }
            }
            navigation.add(new GuideScreenModel.ChapterEntry(chapter.id(), chapter.title(),
                    chapter.icon(), pageEntries));
        }
        int logicalWidth = (int) Math.floor(viewportWidth / scale);
        int navigationWidth = logicalWidth >= 760 && viewportHeight >= 280 ? 220 : 0;
        int contentWidth = Math.max(1, logicalWidth - navigationWidth - 48);
        int columns = contentWidth >= 720 ? 2 : 1;
        int font = Math.max(9, (int) Math.round(11 * scale));
        GuideScreenModel.Layout layout = new GuideScreenModel.Layout(scale,
                (int) Math.round(navigationWidth * scale),
                (int) Math.round(contentWidth * scale), columns, font,
                Math.max(font + 2, (int) Math.round(15 * scale)));
        return new GuideScreenModel(book.title(), book.theme(), currentPage(), navigation,
                results, layout, bookmarks.contains(currentPageId), !history.isEmpty());
    }

    private GuidePage firstUnlockedPage() {
        return book.chapters().stream().flatMap(chapter -> chapter.pages().stream())
                .filter(page -> page.unlockedBy(unlocks)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Field Manual has no unlocked landing page"));
    }

    private static String excerpt(String body, String query) {
        String normalized = body.toLowerCase(Locale.ROOT);
        int match = normalized.indexOf(query);
        if (match < 0) return body.substring(0, Math.min(96, body.length()));
        int start = Math.max(0, match - 36);
        int end = Math.min(body.length(), match + query.length() + 60);
        return (start > 0 ? "…" : "") + body.substring(start, end) + (end < body.length() ? "…" : "");
    }
}
