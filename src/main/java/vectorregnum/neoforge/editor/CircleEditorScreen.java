package vectorregnum.neoforge.editor;

import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import vectorregnum.core.circle.CircleCoordinate;
import vectorregnum.core.circle.CirclePersistence;
import vectorregnum.core.circle.MagicCircle;
import vectorregnum.core.circle.SpellMedium;

/** Native client editor backed by the server request/snapshot protocol. */
public final class CircleEditorScreen extends Screen {
    private static final int PALETTE_ROW_HEIGHT = 14;
    private static final int PANEL = 0xdd211a29;
    private static final int INK = 0xffeee2ca;
    private static final int GOLD = 0xffd1a85a;
    private static final int MUTED = 0xffa899ae;
    private CircleEditorController controller;
    private String status;
    private boolean bindable;
    private String anchorDescription;
    private String paletteQuery = "";
    private String selectedPalette;
    private CircleCoordinate selectedCoordinate;
    private CircleCoordinate draggedCoordinate;
    private String draggedPalette;
    private TextFieldWidget search;
    private TextFieldWidget parameterValues;

    private CircleEditorScreen(CircleEditorSnapshotPayload payload) {
        super(Text.literal("Vector-Regnum Circle Editor"));
        reset(payload);
    }

    public static CircleEditorScreen from(CircleEditorSnapshotPayload payload) {
        return new CircleEditorScreen(payload);
    }

    public void applySnapshot(CircleEditorSnapshotPayload payload) {
        reset(payload);
    }

    private void reset(CircleEditorSnapshotPayload payload) {
        MagicCircle circle = CirclePersistence.decode(payload.encodedCircle());
        controller = new CircleEditorController(circle,
                (updated, medium) -> CircleEditorController.BindingResult.accepted("Queued on server"));
        status = payload.status();
        bindable = payload.bindable();
        anchorDescription = payload.anchorDescription();
        if (width >= 480 && height >= 270 && !paletteQuery.isBlank()) {
            controller.handle(new CircleEditorRequest.SearchPalette(paletteQuery), width, height);
        }
        if (selectedCoordinate != null) {
            try {
                controller.handle(new CircleEditorRequest.Select(selectedCoordinate),
                        Math.max(480, width), Math.max(270, height));
            } catch (RuntimeException ignored) {
                selectedCoordinate = null;
            }
        }
    }

    @Override
    protected void init() {
        CircleEditorLayout ui = editorLayout();
        CircleEditorLayout.Rect palette = ui.palette();
        CircleEditorLayout.Rect canvas = ui.canvas();
        CircleEditorLayout.Rect inspector = ui.inspector();
        search = new TextFieldWidget(textRenderer, palette.x(), 28, palette.width(), 20,
                Text.literal("Search sigils"));
        search.setText(paletteQuery);
        search.setChangedListener(value -> {
            paletteQuery = value;
            controller.handle(new CircleEditorRequest.SearchPalette(value),
                    Math.max(480, width), Math.max(270, height));
            CircleEditorClientNetworking.send(new CircleEditorRequest.SearchPalette(value));
        });
        addDrawableChild(search);
        int footerFirstRow = ui.footerTop() + 4;
        int footerSecondRow = ui.footerTop() + 28;
        int applyWidth = Math.min(56, Math.max(44, inspector.width() / 3));
        int parameterWidth = inspector.width() - applyWidth - 4;
        parameterValues = new TextFieldWidget(textRenderer, inspector.x(), footerFirstRow,
                parameterWidth, 20,
                Text.literal("Sigil parameters"));
        parameterValues.setPlaceholder(Text.literal("parameters…").formatted(Formatting.DARK_GRAY));
        addDrawableChild(parameterValues);
        addDrawableChild(ButtonWidget.builder(Text.literal("Apply"), button -> applyParameters())
                .dimensions(inspector.x() + parameterWidth + 4, footerFirstRow,
                        applyWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Preview"), button -> dispatch(
                new CircleEditorRequest.Compile())).dimensions(canvas.x(), 28, 64, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Undo"), button -> dispatch(
                new CircleEditorRequest.Undo())).dimensions(canvas.x() + 68, 28, 52, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button -> close())
                .dimensions(inspector.right() - 58, 28, 58, 20).build());
        int clearWidth = ui.compact() ? 64 : 94;
        int anchorWidth = Math.min(120, canvas.width() - clearWidth - 4);
        addDrawableChild(ButtonWidget.builder(Text.literal("Anchor aimed face"), button -> dispatch(
                new CircleEditorRequest.CaptureFaceAnchor()))
                .dimensions(canvas.x(), footerFirstRow, anchorWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Clear anchor"), button -> dispatch(
                new CircleEditorRequest.ClearAnchor()))
                .dimensions(canvas.x() + anchorWidth + 4, footerFirstRow,
                        Math.min(clearWidth, canvas.right() - canvas.x() - anchorWidth - 4), 20).build());
        int mediaGap = 3;
        int mediaWidth = (inspector.width() - mediaGap * 2) / 3;
        addDrawableChild(ButtonWidget.builder(Text.literal("Scroll"), button -> dispatch(
                new CircleEditorRequest.Bind(SpellMedium.SCROLL)))
                .dimensions(inspector.x(), footerSecondRow, mediaWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Book"), button -> dispatch(
                new CircleEditorRequest.Bind(SpellMedium.BOOK)))
                .dimensions(inspector.x() + mediaWidth + mediaGap, footerSecondRow,
                        mediaWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Tablet"), button -> dispatch(
                new CircleEditorRequest.Bind(SpellMedium.TABLET)))
                .dimensions(inspector.x() + (mediaWidth + mediaGap) * 2, footerSecondRow,
                        inspector.right() - (inspector.x() + (mediaWidth + mediaGap) * 2),
                        20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xff100d16);
        CircleEditorLayout ui = editorLayout();
        CircleEditorScreenModel model = controller.snapshot(Math.max(480, width), Math.max(270, height));
        syncParameterField(model);
        String title = textRenderer.trimToWidth(model.circle().name(), ui.palette().width());
        context.drawTextWithShadow(textRenderer, Text.literal(title).formatted(Formatting.BOLD),
                ui.palette().x(), 9, GOLD);
        int statusWidth = Math.max(40, ui.inspector().right() - ui.canvas().x());
        String boundedStatus = textRenderer.trimToWidth(
                status + (bindable ? " • bindable" : ""), statusWidth);
        context.drawText(textRenderer, Text.literal(boundedStatus), ui.canvas().x(), 10,
                MUTED, false);
        fill(context, ui.palette(), PANEL);
        fill(context, ui.canvas(), 0xaa211a29);
        fill(context, ui.inspector(), PANEL);
        renderPalette(context, model, ui);
        renderCircle(context, model, ui, mouseX, mouseY);
        renderInspector(context, model, ui);
        context.drawText(textRenderer, Text.literal(textRenderer.trimToWidth(
                        "Drag → empty slots", ui.palette().width())),
                ui.palette().x(), ui.footerTop() + 8, MUTED, false);
        context.drawText(textRenderer, Text.literal(textRenderer.trimToWidth(
                        "Right-click/Delete removes", ui.palette().width())),
                ui.palette().x(), ui.footerTop() + 32, MUTED, false);
        String anchor = anchorDescription.isBlank() ? "No fixed face anchor"
                : "Fixed: " + anchorDescription;
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal(textRenderer.trimToWidth(anchor, ui.canvas().width())),
                ui.circleCenterX(), ui.footerTop() + 31, anchorDescription.isBlank() ? MUTED : GOLD);
        super.render(context, mouseX, mouseY, delta);
        if (draggedPalette != null || draggedCoordinate != null) {
            String label = draggedPalette != null ? shortId(draggedPalette) : "move sigil";
            context.drawTextWithShadow(textRenderer, Text.literal(label), mouseX + 10, mouseY + 8,
                    GOLD);
        }
    }

    /** The editor paints an opaque work surface; superclass blur would obscure it. */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    private void renderPalette(DrawContext context, CircleEditorScreenModel model,
            CircleEditorLayout ui) {
        CircleEditorLayout.Rect panel = ui.palette();
        context.enableScissor(panel.x(), panel.y(), panel.right(), panel.bottom());
        int y = panel.y() + 5;
        for (SigilPalette.Entry entry : visiblePaletteEntries(model)) {
            boolean selected = entry.id().equals(selectedPalette);
            if (selected) context.fill(panel.x() + 2, y - 2, panel.right() - 2,
                    y + PALETTE_ROW_HEIGHT - 1, 0xff765936);
            String label = ui.compact() ? entry.label() : entry.label() + "  ["
                    + entry.category().name().toLowerCase() + "]";
            label = textRenderer.trimToWidth(label, panel.width() - 10);
            context.drawText(textRenderer, Text.literal(label), panel.x() + 5, y,
                    selected ? INK : MUTED, false);
            y += PALETTE_ROW_HEIGHT;
            if (y > panel.bottom() - 9) break;
        }
        context.disableScissor();
    }

    private void renderCircle(DrawContext context, CircleEditorScreenModel model,
            CircleEditorLayout ui, int mouseX, int mouseY) {
        CircleEditorLayout.Rect panel = ui.canvas();
        int centerX = ui.circleCenterX();
        int centerY = ui.circleCenterY();
        int radius = ui.circleRadius();
        context.enableScissor(panel.x(), panel.y(), panel.right(), panel.bottom());
        for (int ring = model.circle().ringCount() - 1; ring >= 0; ring--) {
            int ringRadius = ringRadius(model, ui, ring);
            context.drawBorder(centerX - ringRadius, centerY - ringRadius,
                    ringRadius * 2, ringRadius * 2, 0xff594766);
        }
        CircleEditorScreenModel.Slot hovered = null;
        for (CircleEditorScreenModel.Slot slot : model.slots()) {
            int ringRadius = ringRadius(model, ui, slot.coordinate().ring());
            double angle = -Math.PI / 2.0 + slot.coordinate().clockwiseSlot()
                    * (Math.PI * 2.0 / model.circle().slotsPerRing());
            int x = centerX + (int) Math.round(Math.cos(angle) * ringRadius);
            int y = centerY + (int) Math.round(Math.sin(angle) * ringRadius);
            int color = slot.hasError() ? 0xffdc6673 : slot.selected() ? GOLD : 0xff8c6fb0;
            context.fill(x - 5, y - 5, x + 6, y + 6, color);
            if (Math.hypot(mouseX - x, mouseY - y) <= 8.0) {
                hovered = slot;
            }
        }
        String centerLabel = model.slots().stream().filter(CircleEditorScreenModel.Slot::selected)
                .findFirst().map(slot -> slot.occupied() ? shortId(slot.sigilId())
                        : "empty r" + slot.coordinate().ring() + ":s"
                                + slot.coordinate().clockwiseSlot())
                .orElse("clockwise → inward");
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal(textRenderer.trimToWidth(centerLabel, Math.max(30, radius * 2 - 8))),
                centerX, centerY - 4, INK);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("outer → inner"),
                centerX, panel.bottom() - 11, MUTED);
        if (hovered != null) {
            String hover = hovered.occupied() ? hovered.sigilId()
                    : "empty r" + hovered.coordinate().ring() + ":s"
                            + hovered.coordinate().clockwiseSlot();
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(textRenderer.trimToWidth(hover, panel.width() - 8)),
                    centerX, panel.y() + 5, GOLD);
        }
        context.disableScissor();
    }

    private void renderInspector(DrawContext context, CircleEditorScreenModel model,
            CircleEditorLayout ui) {
        CircleEditorLayout.Rect panel = ui.inspector();
        int x = panel.x() + 4;
        int y = panel.y() + 5;
        int textWidth = panel.width() - 8;
        context.enableScissor(panel.x(), panel.y(), panel.right(), panel.bottom());
        SigilPalette.Entry inspected = selectedPalette == null ? model.selectedSigil()
                : model.palette().stream().filter(entry -> entry.id().equals(selectedPalette))
                        .findFirst().orElse(model.selectedSigil());
        if (inspected != null) {
            context.drawTextWithShadow(textRenderer,
                    Text.literal(textRenderer.trimToWidth(inspected.label(), textWidth)), x, y, GOLD);
            y += 14;
            int descriptionLines = ui.compact() ? 3 : 6;
            for (var line : textRenderer.wrapLines(Text.literal(inspected.description()), textWidth)
                    .stream().limit(descriptionLines).toList()) {
                context.drawText(textRenderer, line, x, y, INK, false);
                y += 11;
            }
            if (!inspected.parameters().isEmpty()) {
                String schema = inspected.parameters().stream()
                        .map(parameter -> parameter.name() + ":" + parameter.kind().name().toLowerCase())
                        .collect(Collectors.joining(inspected.repeatingParameters() ? " … " : "  "));
                context.drawText(textRenderer,
                        Text.literal(textRenderer.trimToWidth(schema, textWidth)), x, y + 2,
                        MUTED, false);
                y += 16;
            }
            y += 4;
        }
        context.drawTextWithShadow(textRenderer, Text.literal("Diagnostics"), x, y, GOLD);
        y += 16;
        for (var diagnostic : model.diagnostics().stream().limit(ui.compact() ? 3 : 10).toList()) {
            for (var line : textRenderer.wrapLines(Text.literal(diagnostic.code() + ": "
                    + diagnostic.message()), textWidth)) {
                context.drawText(textRenderer, line, x, y, diagnostic.severity()
                        == vectorregnum.core.circle.CircleDiagnostic.Severity.ERROR
                        ? 0xffdc6673 : MUTED, false);
                y += 11;
                if (y > panel.bottom() - 22) {
                    context.disableScissor();
                    return;
                }
            }
        }
        if (model.selected() != null) {
            String selected = "r" + model.selected().ring() + ":s"
                    + model.selected().clockwiseSlot() + " • right-click/Delete";
            context.drawText(textRenderer,
                    Text.literal(textRenderer.trimToWidth(selected, textWidth)), x,
                    panel.bottom() - 13, INK, false);
        }
        context.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        CircleEditorLayout ui = editorLayout();
        if (ui.palette().contains(mouseX, mouseY)) {
            int index = (int) ((mouseY - ui.palette().y() - 3) / PALETTE_ROW_HEIGHT);
            List<SigilPalette.Entry> entries = visiblePaletteEntries(controller.snapshot(
                    Math.max(480, width), Math.max(270, height)));
            if (index >= 0 && index < entries.size()) {
                selectedPalette = entries.get(index).id();
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    draggedPalette = selectedPalette;
                    draggedCoordinate = null;
                }
                return true;
            }
        }
        CircleEditorScreenModel model = controller.snapshot(
                Math.max(480, width), Math.max(270, height));
        CircleCoordinate coordinate = coordinateAt(model, mouseX, mouseY);
        if (coordinate != null) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && model.slots().stream()
                    .anyMatch(slot -> slot.coordinate().equals(coordinate) && slot.occupied())) {
                draggedCoordinate = coordinate;
                draggedPalette = null;
            }
            dispatch(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                    ? CircleEditorInteraction.secondaryClick(model, coordinate)
                    : CircleEditorInteraction.primaryClick(model, coordinate, selectedPalette));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && (draggedPalette != null || draggedCoordinate != null)) {
            CircleEditorScreenModel model = controller.snapshot(
                    Math.max(480, width), Math.max(270, height));
            CircleCoordinate destination = coordinateAt(model, mouseX, mouseY);
            if (destination != null) {
                CircleEditorInteraction.dragPlacement(model, draggedCoordinate,
                                destination, draggedPalette)
                        .ifPresent(this::dispatch);
            }
            draggedPalette = null;
            draggedCoordinate = null;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private CircleCoordinate coordinateAt(CircleEditorScreenModel model, double mouseX, double mouseY) {
        CircleEditorLayout ui = editorLayout();
        if (!ui.canvas().contains(mouseX, mouseY)) {
            return null;
        }
        int centerX = ui.circleCenterX();
        int centerY = ui.circleCenterY();
        double distance = Math.hypot(mouseX - centerX, mouseY - centerY);
        int ring = -1;
        double ringDistance = Double.MAX_VALUE;
        for (int candidate = 0; candidate < model.circle().ringCount(); candidate++) {
            int ringRadius = ringRadius(model, ui, candidate);
            if (Math.abs(distance - ringRadius) < ringDistance) {
                ring = candidate;
                ringDistance = Math.abs(distance - ringRadius);
            }
        }
        int hitTolerance = Math.max(7, Math.min(12, ui.circleRadius() / 5));
        if (ring < 0 || ringDistance > hitTolerance) return null;
        double angle = Math.atan2(mouseY - centerY, mouseX - centerX) + Math.PI / 2.0;
        if (angle < 0) angle += Math.PI * 2.0;
        int slot = (int) Math.round(angle / (Math.PI * 2.0 / model.circle().slotsPerRing()))
                % model.circle().slotsPerRing();
        return new CircleCoordinate(ring, slot);
    }

    private void dispatch(CircleEditorRequest request) {
        switch (request) {
            case CircleEditorRequest.Select select -> selectedCoordinate = select.coordinate();
            case CircleEditorRequest.Place place -> selectedCoordinate = place.coordinate();
            case CircleEditorRequest.Move move -> selectedCoordinate = move.destination();
            case CircleEditorRequest.Remove remove -> selectedCoordinate = remove.coordinate();
            case CircleEditorRequest.UpdateParameters update -> selectedCoordinate = update.coordinate();
            default -> { }
        }
        try {
            CircleEditorScreenModel model = controller.handle(request,
                    Math.max(480, width), Math.max(270, height));
            status = model.statusMessage();
            bindable = model.bindable();
            syncParameterField(model);
        } catch (RuntimeException exception) {
            status = "Preview rejected: " + exception.getMessage();
        }
        CircleEditorClientNetworking.send(request);
    }

    private void applyParameters() {
        CircleEditorScreenModel model = controller.snapshot(
                Math.max(480, width), Math.max(270, height));
        if (model.selected() == null) {
            status = "Select a sigil before editing parameters";
            return;
        }
        try {
            List<String> values = CircleEditorInteraction.parseParameterInput(parameterValues.getText());
            dispatch(new CircleEditorRequest.UpdateParameters(model.selected(), values));
        } catch (IllegalArgumentException exception) {
            status = "Invalid parameters: " + exception.getMessage();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (parameterValues != null && parameterValues.isFocused()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            applyParameters();
            return true;
        }
        if ((parameterValues == null || !parameterValues.isFocused())
                && (search == null || !search.isFocused())
                && (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE)) {
            CircleEditorScreenModel model = controller.snapshot(
                    Math.max(480, width), Math.max(270, height));
            if (model.selected() != null) {
                dispatch(new CircleEditorRequest.Remove(model.selected()));
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void syncParameterField(CircleEditorScreenModel model) {
        if (parameterValues == null || parameterValues.isFocused()) {
            return;
        }
        String value = model.slots().stream()
                .filter(slot -> slot.selected() && slot.occupied())
                .findFirst()
                .map(slot -> slot.parameters().stream().map(CircleEditorInteraction::formatParameter)
                        .collect(Collectors.joining(" ")))
                .orElse("");
        if (!parameterValues.getText().equals(value)) {
            parameterValues.setText(value);
        }
    }

    private static String shortId(String id) {
        return id.length() <= 12 ? id : id.substring(0, 12);
    }

    private List<SigilPalette.Entry> visiblePaletteEntries(CircleEditorScreenModel model) {
        int availableRows = Math.max(0, (editorLayout().palette().height() - 6)
                / PALETTE_ROW_HEIGHT);
        return model.palette().stream().limit(Math.min(28, availableRows)).toList();
    }

    private CircleEditorLayout editorLayout() {
        return CircleEditorLayout.calculate(Math.max(320, width), Math.max(180, height));
    }

    private static int ringRadius(CircleEditorScreenModel model, CircleEditorLayout ui, int ring) {
        int step = Math.max(10, ui.circleRadius() / Math.max(2, model.circle().ringCount()));
        return Math.max(10, ui.circleRadius() - ring * step);
    }

    private static void fill(DrawContext context, CircleEditorLayout.Rect rectangle, int color) {
        context.fill(rectangle.x(), rectangle.y(), rectangle.right(), rectangle.bottom(), color);
    }
}
