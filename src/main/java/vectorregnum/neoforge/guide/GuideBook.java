package vectorregnum.neoforge.guide;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable data-driven definition of the Vector-Regnum Field Manual. */
public final class GuideBook {
    private final String id;
    private final String title;
    private final int version;
    private final String theme;
    private final List<GuideChapter> chapters;
    private final Map<String, GuidePage> pages;
    private final Map<String, String> contextPages;

    public GuideBook(String id, String title, int version, String theme, List<GuideChapter> chapters) {
        this.id = GuidePage.requireId(id);
        this.title = GuidePage.requireText(title, "title");
        if (version < 1) throw new IllegalArgumentException("guide version must be positive");
        this.version = version;
        this.theme = GuidePage.requireText(theme, "theme");
        this.chapters = List.copyOf(Objects.requireNonNull(chapters, "chapters"));
        if (this.chapters.isEmpty()) throw new IllegalArgumentException("guide needs a chapter");

        Set<String> chapterIds = new HashSet<>();
        Map<String, GuidePage> byId = new HashMap<>();
        for (GuideChapter chapter : this.chapters) {
            if (!chapterIds.add(chapter.id())) {
                throw new IllegalArgumentException("duplicate guide chapter: " + chapter.id());
            }
            for (GuidePage page : chapter.pages()) {
                if (byId.putIfAbsent(page.id(), page) != null) {
                    throw new IllegalArgumentException("duplicate guide page: " + page.id());
                }
            }
        }
        Map<String, String> contexts = new HashMap<>();
        for (GuidePage page : byId.values()) {
            for (GuideElement element : page.elements()) {
                if (element.type() == GuideElement.Type.LINK
                        && !byId.containsKey(element.metadata("target"))) {
                    throw new IllegalArgumentException("unknown guide page link from " + page.id()
                            + ": " + element.metadata("target"));
                }
                String context = element.metadata().get("context");
                if (context != null) {
                    GuideRecipe.requireIdentifier(context, "guide context");
                    String previous = contexts.putIfAbsent(context, page.id());
                    if (previous != null && !previous.equals(page.id())) {
                        throw new IllegalArgumentException("guide context " + context
                                + " is claimed by both " + previous + " and " + page.id());
                    }
                }
            }
        }
        this.pages = Map.copyOf(byId);
        this.contextPages = Map.copyOf(contexts);
    }

    public String id() { return id; }
    public String title() { return title; }
    public int version() { return version; }
    public String theme() { return theme; }
    public List<GuideChapter> chapters() { return chapters; }
    public Optional<GuidePage> page(String pageId) { return Optional.ofNullable(pages.get(pageId)); }
    public Optional<GuidePage> contextPage(String contentId) {
        String pageId = contextPages.get(contentId);
        return pageId == null ? Optional.empty() : page(pageId);
    }
    public GuidePage firstPage() { return chapters.getFirst().pages().getFirst(); }
}
