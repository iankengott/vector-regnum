package vectorregnum.fabric.ponder;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import vectorregnum.fabric.SpellMediaContent;
import vectorregnum.fabric.progression.ProgressionContent;

/** A bounded, trace-driven workshop diorama with Create-style playback controls. */
public final class PonderScreen extends Screen {
    private static final int GOLD = 0xffd1a85a;
    private static final int INK = 0xffede2ca;
    private static final int MUTED = 0xffa899ae;
    private static final int STONE = 0xff403947;
    private static final int STONE_LIGHT = 0xff594e60;
    private static final int VOID = 0xff100d16;
    private final PonderController controller;
    private PonderTimeline retainedServerTrace;
    private boolean primerPinned;

    public PonderScreen(PonderController controller) {
        super(Text.literal(controller.timeline().title()));
        this.controller = controller;
        if (!controller.timeline().id().equals("workshop-primer")) {
            retainedServerTrace = controller.timeline();
        }
        controller.play();
    }

    /** Applies a bounded live server update while keeping this screen open. */
    public void acceptLiveTimeline(PonderTimeline timeline) {
        retainedServerTrace = timeline;
        if (!primerPinned) controller.replaceTimeline(timeline);
    }

    @Override
    protected void init() {
        int y = height - 30;
        addDrawableChild(ButtonWidget.builder(Text.literal("↻"), button -> controller.replay())
                .dimensions(width / 2 - 100, y, 28, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("|◀"), button -> controller.stepBack())
                .dimensions(width / 2 - 68, y, 34, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("▶/Ⅱ"), button -> {
            if (controller.playing()) controller.pause(); else controller.play();
        }).dimensions(width / 2 - 30, y, 60, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("▶|"), button -> controller.stepForward())
                .dimensions(width / 2 + 34, y, 34, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Primer/Trace"), button -> togglePrimer())
                .dimensions(width - 104, 8, 92, 20).build());
    }

    @Override
    public void tick() {
        controller.tick();
    }

    @Override
    public void close() {
        PonderTraceClientNetworking.stopWatching();
        super.close();
    }

    private void togglePrimer() {
        if (primerPinned) {
            primerPinned = false;
            if (retainedServerTrace != null) {
                controller.replaceTimeline(retainedServerTrace);
            } else {
                controller.replay();
            }
        } else {
            primerPinned = true;
            controller.replaceTimeline(PonderLessonLibrary.primer());
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, VOID);
        PonderTimeline.Step step = controller.currentStep();
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal(controller.timeline().title()), width / 2, 10, GOLD);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal(step.phase().name()).formatted(Formatting.BOLD), width / 2, 25,
                phaseColor(step.phase()));
        renderWorkshop(context, step);
        renderCallout(context, step);
        renderProgress(context);
        super.render(context, mouseX, mouseY, delta);
    }

    /** This screen paints an opaque workshop; superclass blur would obscure it. */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    private void renderWorkshop(DrawContext context, PonderTimeline.Step step) {
        int panelWidth = Math.min(244, Math.max(150, width / 3));
        int left = panelWidth + 28;
        int right = width - 16;
        int top = 48;
        int bottom = height - 50;
        int centerX = (left + right) / 2;
        int centerY = Math.max(top + 72, bottom - 88);
        int halfWidth = Math.max(46, Math.min(150, (right - left - 12) / 2));
        int halfHeight = Math.max(22, Math.min(54, (bottom - top) / 5));

        context.fill(left, top, right, bottom, 0xff17131d);
        context.drawBorder(left, top, right - left, bottom - top, 0xff352c3c);
        drawDiamond(context, centerX, centerY, halfWidth, halfHeight, STONE, STONE_LIGHT);

        int circleX = centerX;
        int circleY = centerY - 10;
        int radius = Math.max(30, Math.min(82, halfWidth - 18));
        drawCircleAndTrace(context, step, circleX, circleY, radius);
        drawPedestalAndScroll(context, step, centerX, centerY - 3);
        drawManaCrystal(context, left + 30, centerY - 22, step);
        drawTrainingDummy(context, right - 32, centerY - 27, step);
        drawCueEffects(context, step, left + 30, centerX, right - 32, centerY - 23, radius);
        context.drawCenteredTextWithShadow(textRenderer, "WARDED SCRIBE WORKSHOP",
                centerX, top + 8, MUTED);
    }

    private void drawCircleAndTrace(DrawContext context, PonderTimeline.Step step,
            int centerX, int centerY, int radius) {
        drawEllipse(context, centerX, centerY, radius, Math.max(13, radius / 3), 0xff7d6a86);
        drawEllipse(context, centerX, centerY, Math.max(16, radius * 2 / 3),
                Math.max(9, radius * 2 / 9), 0xff5f5369);
        drawEllipse(context, centerX, centerY, Math.max(8, radius / 3),
                Math.max(5, radius / 9), 0xff5f5369);

        Map<Integer, PonderTimeline.SourceRef> sources = allSources();
        Optional<PonderTimeline.SourceRef> focused = step.cues().stream()
                .flatMap(cue -> cue.source().stream()).findFirst();
        int focusedIndex = focused.map(PonderTimeline.SourceRef::sourceIndex).orElse(-1);
        List<PonderTimeline.SourceRef> ordered = sources.values().stream()
                .sorted(Comparator.comparingInt(PonderTimeline.SourceRef::sourceIndex)).toList();
        int previousX = centerX;
        int previousY = centerY - radius / 3;
        boolean havePrevious = false;
        for (PonderTimeline.SourceRef source : ordered) {
            int x = sigilX(centerX, radius, source);
            int y = sigilY(centerY, radius, source);
            if (havePrevious && source.sourceIndex() <= focusedIndex) {
                drawLine(context, previousX, previousY, x, y, 0xffa995c8);
            }
            int color = source.sourceIndex() == focusedIndex
                    ? phaseColor(step.phase())
                    : source.sourceIndex() < focusedIndex ? 0xffbfa8d4 : 0xff65596d;
            int size = source.sourceIndex() == focusedIndex ? 4 : 3;
            context.fill(x - size, y - size, x + size + 1, y + size + 1, color);
            context.drawCenteredTextWithShadow(textRenderer,
                    Integer.toString(source.sourceIndex() + 1), x, y - 4, 0xff17131d);
            previousX = x;
            previousY = y;
            havePrevious = true;
        }
        focused.ifPresent(source -> context.drawCenteredTextWithShadow(textRenderer,
                source.sigilId(), centerX, centerY + radius / 3 + 8, INK));
    }

    private Map<Integer, PonderTimeline.SourceRef> allSources() {
        Map<Integer, PonderTimeline.SourceRef> sources = new LinkedHashMap<>();
        for (PonderTimeline.Step step : controller.timeline().steps()) {
            for (PonderTimeline.Cue cue : step.cues()) {
                cue.source().ifPresent(source -> sources.putIfAbsent(source.sourceIndex(), source));
            }
        }
        return sources;
    }

    private static int sigilX(int centerX, int radius, PonderTimeline.SourceRef source) {
        double angle = -Math.PI / 2.0 + source.clockwiseSlot() * (Math.PI / 4.0);
        int ringRadius = Math.max(12, radius - source.ring() * Math.max(16, radius / 3));
        return centerX + (int) Math.round(Math.cos(angle) * ringRadius);
    }

    private static int sigilY(int centerY, int radius, PonderTimeline.SourceRef source) {
        double angle = -Math.PI / 2.0 + source.clockwiseSlot() * (Math.PI / 4.0);
        int ringRadius = Math.max(12, radius - source.ring() * Math.max(16, radius / 3));
        return centerY + (int) Math.round(Math.sin(angle) * ringRadius / 3.0);
    }

    private void drawPedestalAndScroll(DrawContext context, PonderTimeline.Step step,
            int x, int y) {
        context.fill(x - 20, y + 8, x + 21, y + 15, 0xff2c2730);
        context.fill(x - 15, y - 2, x + 16, y + 9, 0xff65576b);
        boolean consumed = cueData(step, PonderTimeline.CueType.SCROLL_STATE, "state")
                .filter("accepted_and_consumed"::equals).isPresent();
        if (!consumed || controller.tickInStep() < step.durationTicks() / 2) {
            context.fill(x - 12, y - 8, x + 13, y, 0xffd7c28e);
            context.fill(x - 13, y - 9, x - 9, y + 1, 0xff8d6843);
            context.fill(x + 9, y - 9, x + 13, y + 1, 0xff8d6843);
            context.fill(x - 5, y - 6, x + 6, y - 5, 0xff844f88);
            context.drawItem(new ItemStack(SpellMediaContent.SPELL_SCROLL), x - 8, y - 17);
        } else {
            context.drawCenteredTextWithShadow(textRenderer, "ACCEPTED", x, y - 8, 0xff76c993);
        }
    }

    private void drawManaCrystal(DrawContext context, int x, int y, PonderTimeline.Step step) {
        context.fill(x - 7, y + 8, x + 8, y + 12, 0xff342c39);
        int pulse = step.phase() == PonderTimeline.Phase.MANA
                ? 2 + controller.tickInStep() % 3 : 1;
        context.fill(x - pulse, y - 12, x + pulse + 1, y + 9, 0xff865ed1);
        context.fill(x - 5, y - 5, x + 6, y + 4, 0xffb28cff);
        context.drawItem(new ItemStack(ProgressionContent.MANA_CRYSTAL_NODE_ITEM), x - 8, y - 8);
        context.drawCenteredTextWithShadow(textRenderer, "MANA", x, y + 15, MUTED);
    }

    private void drawTrainingDummy(DrawContext context, int x, int y, PonderTimeline.Step step) {
        int shove = step.cues().stream().anyMatch(cue -> cue.type() == PonderTimeline.CueType.WORLD_EFFECT)
                ? Math.min(7, controller.tickInStep() / 2) : 0;
        x += shove;
        context.fill(x - 4, y - 8, x + 5, y + 1, 0xffb9946d);
        context.fill(x - 2, y + 1, x + 3, y + 21, 0xff836348);
        context.fill(x - 10, y + 5, x + 11, y + 8, 0xff836348);
        context.fill(x - 7, y + 21, x - 2, y + 27, 0xff635044);
        context.fill(x + 2, y + 21, x + 7, y + 27, 0xff635044);
        context.drawCenteredTextWithShadow(textRenderer, "TARGET", x, y + 31, MUTED);
    }

    private void drawCueEffects(DrawContext context, PonderTimeline.Step step, int crystalX,
            int scrollX, int targetX, int y, int radius) {
        if (hasCue(step, PonderTimeline.CueType.MANA_FLOW)) {
            int movingX = crystalX + Math.floorMod(controller.tickInStep() * 3,
                    Math.max(1, scrollX - crystalX));
            drawLine(context, crystalX, y, scrollX, y + 18, 0xff7250a7);
            context.fill(movingX - 2, y - 2, movingX + 3, y + 3, 0xffd6b8ff);
        }
        if (hasCue(step, PonderTimeline.CueType.WORLD_EFFECT)) {
            drawLine(context, scrollX, y + 10, targetX, y, 0xff73d9ff);
            scatter(context, targetX, y, 0xffb3efff, 12, radius);
        }
        if (hasCue(step, PonderTimeline.CueType.COMPILER_FAULT)
                || hasCue(step, PonderTimeline.CueType.RUNTIME_FAULT)) {
            drawLine(context, scrollX - 13, y - 10, scrollX + 17, y + 20, 0xffdc6673);
            drawLine(context, scrollX + 16, y - 8, scrollX - 14, y + 19, 0xffdc6673);
        }
        cueData(step, PonderTimeline.CueType.WILD_MAGIC, "category").ifPresent(category -> {
            int color = switch (category) {
                case "INTERNAL_MANA_DETONATION" -> 0xfff49b59;
                case "UNSTRUCTURED_ELEMENT_BURST" -> 0xff61ccdf;
                default -> 0xffd85a9f;
            };
            scatter(context, scrollX, y + 10, color, 24, radius);
            context.drawCenteredTextWithShadow(textRenderer,
                    category.replace('_', ' '), scrollX, y - radius / 2 - 14, color);
        });
    }

    private void scatter(DrawContext context, int x, int y, int color, int count, int radius) {
        int progress = Math.max(1, controller.tickInStep() + 1);
        for (int index = 0; index < count; index++) {
            double angle = (index * 2.399963229728653) + progress * 0.08;
            int distance = 5 + Math.floorMod(index * 11 + progress, Math.max(6, radius / 2));
            int px = x + (int) Math.round(Math.cos(angle) * distance);
            int py = y + (int) Math.round(Math.sin(angle) * distance / 2.0);
            context.fill(px, py, px + 2, py + 2, color);
        }
    }

    private void renderCallout(DrawContext context, PonderTimeline.Step step) {
        int x = 12;
        int panelWidth = Math.min(244, Math.max(150, width / 3));
        int y = 48;
        context.fill(x, y, x + panelWidth, height - 46, 0xdd211a29);
        context.fill(x, y, x + 4, height - 46, phaseColor(step.phase()));
        context.drawTextWithShadow(textRenderer, step.title(), x + 10, y + 10, INK);
        int lineY = y + 28;
        for (OrderedText line : textRenderer.wrapLines(Text.literal(step.narration()), panelWidth - 20)) {
            if (lineY > height - 92) break;
            context.drawText(textRenderer, line, x + 10, lineY, MUTED, false);
            lineY += 11;
        }
        lineY += 7;
        for (PonderTimeline.Cue cue : step.cues()) {
            if (lineY > height - 58) break;
            String label = cue.type().name().toLowerCase().replace('_', ' ');
            String detail = cue.data().getOrDefault("dimension",
                    cue.data().getOrDefault("status", cue.data().getOrDefault("category", "")));
            context.drawText(textRenderer, Text.literal("• " + label
                    + (detail.isEmpty() ? "" : ": " + detail.toLowerCase().replace('_', ' '))),
                    x + 10, lineY, phaseColor(step.phase()), false);
            lineY += 11;
        }
    }

    private void renderProgress(DrawContext context) {
        int left = 16;
        int right = width - 16;
        int y = height - 42;
        double completeSteps = controller.stepIndex()
                + controller.tickInStep() / (double) controller.currentStep().durationTicks();
        int filled = (int) Math.round((right - left) * completeSteps
                / controller.timeline().steps().size());
        context.fill(left, y, right, y + 3, 0xff44394d);
        context.fill(left, y, left + filled, y + 3, GOLD);
        context.drawText(textRenderer, (controller.stepIndex() + 1) + "/"
                + controller.timeline().steps().size(), right - 36, y - 11, MUTED, false);
    }

    private static boolean hasCue(PonderTimeline.Step step, PonderTimeline.CueType type) {
        return step.cues().stream().anyMatch(cue -> cue.type() == type);
    }

    private static Optional<String> cueData(PonderTimeline.Step step,
            PonderTimeline.CueType type, String key) {
        return step.cues().stream().filter(cue -> cue.type() == type)
                .map(cue -> cue.data().get(key)).filter(value -> value != null).findFirst();
    }

    private static void drawDiamond(DrawContext context, int centerX, int centerY,
            int halfWidth, int halfHeight, int color, int edge) {
        for (int row = -halfHeight; row <= halfHeight; row++) {
            int span = (int) Math.round(halfWidth * (1.0 - Math.abs(row) / (double) halfHeight));
            context.fill(centerX - span, centerY + row, centerX + span + 1, centerY + row + 1,
                    row == -halfHeight || row == halfHeight ? edge : color);
        }
        drawLine(context, centerX - halfWidth, centerY, centerX, centerY - halfHeight, edge);
        drawLine(context, centerX, centerY - halfHeight, centerX + halfWidth, centerY, edge);
        drawLine(context, centerX + halfWidth, centerY, centerX, centerY + halfHeight, 0xff2d2732);
        drawLine(context, centerX, centerY + halfHeight, centerX - halfWidth, centerY, 0xff2d2732);
    }

    private static void drawEllipse(DrawContext context, int centerX, int centerY,
            int radiusX, int radiusY, int color) {
        int previousX = centerX + radiusX;
        int previousY = centerY;
        for (int segment = 1; segment <= 64; segment++) {
            double angle = segment * Math.PI * 2.0 / 64.0;
            int x = centerX + (int) Math.round(Math.cos(angle) * radiusX);
            int y = centerY + (int) Math.round(Math.sin(angle) * radiusY);
            drawLine(context, previousX, previousY, x, y, color);
            previousX = x;
            previousY = y;
        }
    }

    private static void drawLine(DrawContext context, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0);
        int sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        while (true) {
            context.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) return;
            int doubled = error * 2;
            if (doubled >= dy) {
                error += dy;
                x0 += sx;
            }
            if (doubled <= dx) {
                error += dx;
                y0 += sy;
            }
        }
    }

    private static int phaseColor(PonderTimeline.Phase phase) {
        return switch (phase) {
            case COMPILATION -> 0xff62a9bd;
            case MANA -> 0xff9e70c7;
            case EXECUTION -> 0xff76c993;
            case FAULT -> 0xffdc6673;
        };
    }
}
