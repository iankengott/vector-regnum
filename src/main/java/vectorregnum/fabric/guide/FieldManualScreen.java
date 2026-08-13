package vectorregnum.fabric.guide;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

/** Native, dependency-free rendering of the illustrated visual Field Manual. */
public final class FieldManualScreen extends Screen {
    private static final int INK = 0xff241b25;
    private static final int MUTED_INK = 0xff66536a;
    private static final int GOLD = 0xffc99b45;
    private static final int PAPER = 0xffeee2c5;
    private static final int PANEL = 0xffdccba9;
    private static final int LOCKED = 0xff9b8996;
    private static final int NORMAL_CONTENT_TOP = 42;
    private static final int COMPACT_CONTENT_TOP = 66;
    private static final int SCROLL_STEP = 30;
    private static final long RECIPE_CYCLE_MILLIS = 1_500L;

    private final GuideScreenController controller;
    private final Runnable ponderOpener;
    private final GuideRecipeCatalog recipes;
    private final GuideScrollState scroll = new GuideScrollState();
    private final List<HoverRegion> hoverRegions = new ArrayList<>();
    private TextFieldWidget search;
    private String renderedPageId;

    public FieldManualScreen(GuideScreenController controller) {
        this(controller, () -> { }, GuideRecipeCatalog.empty());
    }

    public FieldManualScreen(GuideScreenController controller, Runnable ponderOpener) {
        this(controller, ponderOpener, GuideRecipeCatalog.empty());
    }

    public FieldManualScreen(GuideScreenController controller, Runnable ponderOpener,
            GuideRecipeCatalog recipes) {
        super(Text.literal(controller.book().title()));
        this.controller = controller;
        this.ponderOpener = ponderOpener;
        this.recipes = recipes;
    }

    @Override
    protected void init() {
        boolean compact = compactToolbar();
        int searchY = compact ? 38 : 14;
        int searchX = compact ? 8 : Math.max(66, width - 186);
        int searchWidth = compact ? Math.max(80, width - 16) : 130;
        search = new TextFieldWidget(textRenderer, searchX, searchY, searchWidth, 20,
                Text.literal("Search Field Manual"));
        search.setPlaceholder(Text.literal("Search…").formatted(Formatting.DARK_GRAY));
        search.setChangedListener(controller::setSearchQuery);
        addDrawableChild(search);
        addDrawableChild(ButtonWidget.builder(Text.literal("←"), button -> controller.back())
                .dimensions(8, 14, 24, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("★"), button -> controller.toggleBookmark())
                .dimensions(36, 14, 24, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("−"), button -> {
            controller.setScale(controller.scale() - 0.125);
            clearAndInit();
        }).dimensions(width - 50, 14, 20, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), button -> {
            controller.setScale(controller.scale() + 0.125);
            clearAndInit();
        }).dimensions(width - 28, 14, 20, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xff17121a);
        GuideScreenModel model = controller.snapshot(width, height);
        syncPage(model.page().id());
        hoverRegions.clear();
        int top = contentTop();
        context.fill(6, top - 4, width - 6, height - 6, PAPER);
        String heading = compactToolbar() ? "Field Manual" : model.bookTitle();
        context.drawCenteredTextWithShadow(textRenderer, heading, width / 2, 18, GOLD);
        if (model.layout().navigationWidth() > 0) renderNavigation(context, model, top);
        renderPage(context, model, top, mouseX, mouseY);
        super.render(context, mouseX, mouseY, delta);
        hoverRegions.stream().filter(region -> region.contains(mouseX, mouseY)).findFirst()
                .ifPresent(region -> context.drawTooltip(textRenderer, region.tooltip(), mouseX, mouseY));
    }

    /** The manual renders opaque themed pages; superclass blur would obscure them. */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    private void renderNavigation(DrawContext context, GuideScreenModel model, int top) {
        int x = 12;
        int y = top + 4;
        int right = Math.min(width / 3, model.layout().navigationWidth());
        context.fill(8, top, right, height - 10, PANEL);
        for (GuideScreenModel.ChapterEntry chapter : model.navigation()) {
            drawItem(context, itemStack(chapter.icon()), x, y);
            context.drawTextWithShadow(textRenderer, Text.literal(chapter.title())
                    .formatted(Formatting.BOLD), x + 20, y + 4, INK);
            y += 20;
            for (GuideScreenModel.PageEntry page : chapter.pages()) {
                String prefix = page.bookmarked() ? "★ " : "  ";
                context.drawText(textRenderer, Text.literal(prefix + page.title()), x, y,
                        page.locked() ? LOCKED : MUTED_INK, false);
                y += 11;
                if (y > height - 24) return;
            }
            y += 5;
        }
    }

    private void renderPage(DrawContext context, GuideScreenModel model, int top,
            int mouseX, int mouseY) {
        int left = pageLeft(model);
        int available = width - left - 20;
        double scale = model.layout().scale();
        List<OrderedText> bodyLines = wrap(model.page().body(), available, scale);
        int contentHeight = measureContent(model.page(), bodyLines.size(), scale);
        int viewportHeight = Math.max(1, height - top - 18);
        scroll.setExtents(contentHeight, viewportHeight);
        int y = top + 8 - scroll.offset();

        context.enableScissor(left, top, left + available, height - 10);
        drawScaled(context, Text.literal(model.page().title()).formatted(Formatting.BOLD),
                left, y, INK, true, scale);
        y += scaled(18, scale);
        for (OrderedText line : bodyLines) {
            drawScaled(context, line, left, y, INK, false, scale);
            y += model.layout().lineHeightPixels();
        }
        y += scaled(6, scale);
        for (GuideElement element : model.page().elements()) {
            int boxHeight = elementHeight(element, scale);
            if (y + boxHeight >= top && y <= height - 10) {
                renderElement(context, element, left, y, available, boxHeight, scale,
                        mouseX, mouseY);
            }
            y += boxHeight + scaled(5, scale);
        }
        context.disableScissor();
        renderScrollbar(context, left + available - 3, top + 2, viewportHeight - 4);
        if (!search.getText().isBlank()) renderSearch(context, model, left, available);
    }

    private void renderElement(DrawContext context, GuideElement element, int left, int y,
            int available, int boxHeight, double scale, int mouseX, int mouseY) {
        context.fill(left, y, left + available, y + boxHeight, PANEL);
        context.fill(left, y, left + 3, y + boxHeight, GOLD);
        drawScaled(context, Text.literal(symbol(element.type()) + " " + element.label()),
                left + 8, y + 5, INK, true, scale);
        int visualWidth = visualWidth(element);
        List<OrderedText> alt = wrap(element.altText(),
                Math.max(48, available - visualWidth - 18), scale);
        int altY = y + scaled(18, scale);
        for (OrderedText line : alt.stream().limit(2).toList()) {
            drawScaled(context, line, left + 8, altY, MUTED_INK, false, scale);
            altY += scaled(11, scale);
        }
        switch (element.type()) {
            case IMAGE -> renderImage(context, element, left + available - visualWidth + 6, y + 5);
            case RECIPE -> renderRecipe(context, element, left + available - 102, y + 4);
            case DIAGRAM -> renderDiagram(context, left + available - 116, y + 6, 104, 34,
                    element.metadata("diagram"));
            default -> { }
        }
        addHover(left, y, left + available, y + boxHeight,
                List.of(Text.literal(element.label()).formatted(Formatting.GOLD),
                        Text.literal(element.altText()).formatted(Formatting.GRAY)), null);
    }

    private void renderSearch(DrawContext context, GuideScreenModel model, int left, int available) {
        int count = Math.min(5, model.searchResults().size());
        int y = height - Math.min(74, 14 + count * 12);
        context.fill(left, y - 4, left + available, height - 8, 0xf4d8c8a4);
        for (GuideScreenModel.SearchResult result : model.searchResults().stream().limit(5).toList()) {
            context.drawText(textRenderer, Text.literal((result.locked() ? "◇ " : "→ ")
                    + result.chapterTitle() + " / " + result.pageTitle()), left + 6, y,
                    result.locked() ? LOCKED : INK, false);
            addHover(left, y, left + available, y + 12,
                    List.of(Text.literal(result.pageTitle()).formatted(Formatting.GOLD),
                            Text.literal(result.excerpt()).formatted(Formatting.GRAY)), null);
            y += 12;
        }
    }

    private void renderDiagram(DrawContext context, int x, int y, int w, int h, String diagram) {
        int color = diagram.contains("mana") ? 0xff6f3fa0 : 0xff3e6877;
        int centerX = x + w / 2;
        int centerY = y + h / 2;
        context.drawBorder(x, y, w, h, color);
        context.fill(centerX - 2, centerY - 2, centerX + 3, centerY + 3, GOLD);
        context.drawHorizontalLine(x + 6, x + w - 7, centerY, color);
        context.drawVerticalLine(centerX, y + 5, y + h - 6, color);
    }

    private void renderScrollbar(DrawContext context, int x, int y, int viewportHeight) {
        if (!scroll.canScroll()) return;
        context.fill(x, y, x + 2, y + viewportHeight, 0x5566536a);
        int thumbHeight = Math.max(12, viewportHeight * viewportHeight
                / (viewportHeight + scroll.maximum()));
        int travel = Math.max(1, viewportHeight - thumbHeight);
        int thumbY = y + travel * scroll.offset() / scroll.maximum();
        context.fill(x - 1, thumbY, x + 3, thumbY + thumbHeight, GOLD);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            for (HoverRegion region : hoverRegions) {
                if (region.contextId() != null && region.contains(mouseX, mouseY)
                        && controller.openContext(region.contextId())) {
                    return true;
                }
            }
        }
        GuideScreenModel model = controller.snapshot(width, height);
        int left = pageLeft(model);
        int available = width - left - 20;
        if (!search.getText().isBlank()) {
            int count = Math.min(5, model.searchResults().size());
            int resultY = height - Math.min(74, 14 + count * 12);
            for (GuideScreenModel.SearchResult result : model.searchResults().stream().limit(5).toList()) {
                if (mouseX >= left && mouseX < left + available
                        && mouseY >= resultY && mouseY < resultY + 12 && !result.locked()) {
                    controller.open(result.pageId());
                    search.setText("");
                    return true;
                }
                resultY += 12;
            }
        }
        int top = contentTop();
        if (model.layout().navigationWidth() > 0 && mouseX < model.layout().navigationWidth()
                && mouseY >= top + 4) {
            int y = top + 4;
            for (GuideScreenModel.ChapterEntry chapter : model.navigation()) {
                y += 20;
                for (GuideScreenModel.PageEntry page : chapter.pages()) {
                    if (mouseY >= y && mouseY < y + 11 && !page.locked()) {
                        controller.open(page.id());
                        return true;
                    }
                    y += 11;
                }
                y += 5;
            }
        }
        double scale = model.layout().scale();
        int elementY = top + 8 + scaled(18, scale);
        elementY += wrap(model.page().body(), available, scale).size()
                * model.layout().lineHeightPixels() + scaled(6, scale) - scroll.offset();
        for (GuideElement element : model.page().elements()) {
            int boxHeight = elementHeight(element, scale);
            if (mouseX >= left && mouseX < left + available
                    && mouseY >= elementY && mouseY < elementY + boxHeight) {
                if (element.type() == GuideElement.Type.PONDER) {
                    ponderOpener.run();
                    return true;
                }
                if (element.type() == GuideElement.Type.LINK) {
                    controller.follow(element);
                    return true;
                }
            }
            elementY += boxHeight + scaled(5, scale);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
            double horizontalAmount, double verticalAmount) {
        if (mouseY >= contentTop() && scroll.scrollBy((int) Math.round(-verticalAmount * SCROLL_STEP))) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (search == null || !search.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_PAGE_UP) return scroll.scrollBy(-pageScrollDistance());
            if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) return scroll.scrollBy(pageScrollDistance());
            if (keyCode == GLFW.GLFW_KEY_HOME) { scroll.toStart(); return true; }
            if (keyCode == GLFW.GLFW_KEY_END) { scroll.toEnd(); return true; }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static String symbol(GuideElement.Type type) {
        return switch (type) {
            case IMAGE -> "▧";
            case DIAGRAM -> "◎";
            case RECIPE -> "▦";
            case LINK -> "↗";
            case PONDER -> "▷";
            case EXAMPLE -> "✦";
        };
    }

    private int measureContent(GuidePage page, int bodyLines, double scale) {
        int height = scaled(18, scale) + bodyLines * scaled(15, scale) + scaled(6, scale);
        for (GuideElement element : page.elements()) {
            height += elementHeight(element, scale) + scaled(5, scale);
        }
        return height + 12;
    }

    private static int elementHeight(GuideElement element, double scale) {
        int base = switch (element.type()) {
            case RECIPE -> 70;
            case IMAGE -> Math.max(58, metadataInt(element, "display_height", 48, 48, 160) + 10);
            case DIAGRAM -> 58;
            default -> 36;
        };
        return Math.max(base, scaled(base, scale));
    }

    private static int visualWidth(GuideElement element) {
        return switch (element.type()) {
            case RECIPE -> 110;
            case DIAGRAM -> 122;
            case IMAGE -> metadataInt(element, "display_width", 48, 48, 160) + 12;
            default -> 0;
        };
    }

    private void renderImage(DrawContext context, GuideElement element, int x, int y) {
        Identifier texture = Identifier.tryParse(element.metadata("asset"));
        if (texture == null) return;
        int width = metadataInt(element, "display_width", 48, 48, 160);
        int height = metadataInt(element, "display_height", 48, 48, 160);
        int sourceWidth = metadataInt(element, "source_width", 16, 16, 2048);
        int sourceHeight = metadataInt(element, "source_height", 16, 16, 2048);
        context.drawTexture(texture, x, y, 0, 0, width, height, sourceWidth, sourceHeight);
    }

    private void renderRecipe(DrawContext context, GuideElement element, int x, int y) {
        GuideRecipe recipe = recipes.recipe(element.metadata("recipe")).orElse(null);
        if (recipe == null) {
            context.drawText(textRenderer, Text.literal("Recipe unavailable"), x, y + 22,
                    MUTED_INK, false);
            return;
        }
        int gridX = x;
        int gridY = y + 4;
        long cycle = Util.getMeasuringTimeMs() / RECIPE_CYCLE_MILLIS;
        context.fill(gridX - 2, gridY - 2, gridX + 52, gridY + 52, 0x553e2e3d);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                int slotX = gridX + column * 17;
                int slotY = gridY + row * 17;
                context.drawBorder(slotX, slotY, 17, 17, 0xff9b7f67);
                GuideIngredient ingredient = recipe.ingredient(row, column);
                String itemId = ingredient.displayChoice(cycle + row * 3L + column,
                        this::resolveTag);
                ItemStack stack = itemStack(itemId);
                drawItem(context, stack, slotX, slotY);
                if (!stack.isEmpty()) {
                    List<Text> tooltip = new ArrayList<>();
                    tooltip.add(stack.getName());
                    tooltip.add(Text.literal(ingredient.sourceDescription())
                            .formatted(Formatting.GRAY));
                    if (ingredient.displayChoices(this::resolveTag).size() > 1) {
                        tooltip.add(Text.literal("Alternatives cycle automatically")
                                .formatted(Formatting.DARK_PURPLE));
                    }
                    addHover(slotX, slotY, slotX + 17, slotY + 17, tooltip, itemId);
                }
            }
        }
        context.drawText(textRenderer, Text.literal("→"), gridX + 56, gridY + 20, INK, false);
        ItemStack result = itemStack(recipe.result());
        drawItem(context, result, gridX + 70, gridY + 17);
        if (!result.isEmpty()) {
            addHover(gridX + 70, gridY + 17, gridX + 87, gridY + 34,
                    List.of(result.getName(), Text.literal("Click to open its manual entry")
                            .formatted(Formatting.DARK_PURPLE)), recipe.result());
        }
        if (recipe.resultCount() > 1) {
            context.drawTextWithShadow(textRenderer, Text.literal(Integer.toString(recipe.resultCount())),
                    gridX + 80, gridY + 28, 0xffffffff);
        }
        String kind = recipe.kind() == GuideRecipe.Kind.SHAPED ? "SHAPED" : "SHAPELESS";
        context.drawText(textRenderer, Text.literal(kind), gridX, gridY + 55, MUTED_INK, false);
    }

    private List<String> resolveTag(String tagId) {
        Identifier id = Identifier.tryParse(tagId);
        if (id == null) return List.of();
        List<String> resolved = new ArrayList<>();
        for (RegistryEntry<Item> entry : Registries.ITEM.iterateEntries(TagKey.of(RegistryKeys.ITEM, id))) {
            Identifier itemId = Registries.ITEM.getId(entry.value());
            if (itemId != null) resolved.add(itemId.toString());
            if (resolved.size() == GuideIngredient.MAX_DISPLAY_CHOICES) break;
        }
        return List.copyOf(resolved);
    }

    private static ItemStack itemStack(String itemId) {
        if (itemId == null || itemId.isEmpty()) return ItemStack.EMPTY;
        Identifier id = Identifier.tryParse(itemId);
        if (id == null) return ItemStack.EMPTY;
        Item item = Registries.ITEM.getOrEmpty(id).orElse(null);
        if (item == null) {
            Block block = Registries.BLOCK.getOrEmpty(id).orElse(null);
            item = block == null ? null : block.asItem();
        }
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static void drawItem(DrawContext context, ItemStack stack, int x, int y) {
        if (!stack.isEmpty()) context.drawItem(stack, x, y);
    }

    private List<OrderedText> wrap(String text, int pixelWidth, double scale) {
        int logicalWidth = Math.max(32, (int) Math.floor(pixelWidth / scale));
        return textRenderer.wrapLines(Text.literal(text), logicalWidth);
    }

    private void drawScaled(DrawContext context, Text text, int x, int y, int color,
            boolean shadow, double scale) {
        drawScaled(context, text.asOrderedText(), x, y, color, shadow, scale);
    }

    private void drawScaled(DrawContext context, OrderedText text, int x, int y, int color,
            boolean shadow, double scale) {
        context.getMatrices().push();
        context.getMatrices().scale((float) scale, (float) scale, 1.0f);
        int logicalX = (int) Math.floor(x / scale);
        int logicalY = (int) Math.floor(y / scale);
        if (shadow) context.drawTextWithShadow(textRenderer, text, logicalX, logicalY, color);
        else context.drawText(textRenderer, text, logicalX, logicalY, color, false);
        context.getMatrices().pop();
    }

    private void addHover(int x1, int y1, int x2, int y2, List<Text> tooltip, String contextId) {
        int clippedY1 = Math.max(contentTop(), y1);
        int clippedY2 = Math.min(height - 10, y2);
        if (clippedY2 > clippedY1) {
            // Item regions are added before their surrounding element, so their
            // richer tooltip wins the first-match lookup.
            hoverRegions.add(new HoverRegion(x1, clippedY1, x2, clippedY2,
                    List.copyOf(tooltip), contextId));
        }
    }

    private int pageLeft(GuideScreenModel model) {
        return model.layout().navigationWidth() > 0
                ? Math.min(width / 3, model.layout().navigationWidth()) + 12 : 16;
    }

    private int pageScrollDistance() {
        return Math.max(SCROLL_STEP, height - contentTop() - 34);
    }

    private int contentTop() { return compactToolbar() ? COMPACT_CONTENT_TOP : NORMAL_CONTENT_TOP; }
    private boolean compactToolbar() { return width < 560; }

    private void syncPage(String pageId) {
        if (!pageId.equals(renderedPageId)) {
            renderedPageId = pageId;
            scroll.toStart();
        }
    }

    private static int metadataInt(GuideElement element, String key, int fallback, int min, int max) {
        String value = element.metadata().get(key);
        if (value == null) return fallback;
        try {
            return Math.clamp(Integer.parseInt(value), min, max);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static int scaled(int pixels, double scale) {
        return Math.max(1, (int) Math.ceil(pixels * scale));
    }

    private record HoverRegion(int x1, int y1, int x2, int y2,
            List<Text> tooltip, String contextId) {
        private boolean contains(double x, double y) {
            return x >= x1 && x < x2 && y >= y1 && y < y2;
        }
    }
}
