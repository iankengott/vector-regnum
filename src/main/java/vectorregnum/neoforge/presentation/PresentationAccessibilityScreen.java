package vectorregnum.neoforge.presentation;

import java.util.Locale;
import java.util.function.DoubleFunction;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import vectorregnum.core.presentation.PresentationAccessibility;
import vectorregnum.core.presentation.PresentationQuality;

/** Localized independent sensory controls; all changes remain strictly client-side. */
public final class PresentationAccessibilityScreen extends Screen {
    private static final int GOLD = 0xffd1a85a;
    private static final int INK = 0xffeee2ca;

    public PresentationAccessibilityScreen() {
        super(Text.translatable("screen.vector_regnum.presentation_accessibility"));
    }

    @Override
    protected void init() {
        int left = Math.max(8, width / 2 - 150);
        int top = Math.max(32, height / 2 - 100);
        int buttonWidth = 146;
        addDrawableChild(button("quality", left, top, Settings::new));
        addDrawableChild(button("particles", left + 154, top,
                value -> Settings.current().particles(value)));
        addDrawableChild(button("darkness_fog", left, top + 26,
                value -> Settings.current().darkness(value)));
        addDrawableChild(button("flashes", left + 154, top + 26,
                value -> Settings.current().flashes(value)));
        addDrawableChild(button("chromatic", left, top + 52,
                value -> Settings.current().chromatic(value)));
        addDrawableChild(button("camera", left + 154, top + 52,
                value -> Settings.current().camera(value)));
        addDrawableChild(button("audio", left, top + 78,
                value -> Settings.current().audio(value)));
        addDrawableChild(toggle("reduced_motion", left + 154, top + 78,
                () -> update(Settings.current().toggleReducedMotion())));
        addDrawableChild(toggle("photosensitive", left, top + 104,
                () -> update(Settings.current().togglePhotosensitive())));
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close())
                .dimensions(left + 154, top + 104, buttonWidth, 20).build());
    }

    private ButtonWidget button(String id, int x, int y, DoubleFunction<Settings> update) {
        return ButtonWidget.builder(label(id), button -> {
            double current = value(id);
            update(update.apply(current >= .99 ? 0.0 : current >= .49 ? 1.0 : .5));
            button.setMessage(label(id));
        }).dimensions(x, y, 146, 20).build();
    }

    private ButtonWidget toggle(String id, int x, int y, Runnable action) {
        return ButtonWidget.builder(label(id), button -> {
            action.run();
            button.setMessage(label(id));
        }).dimensions(x, y, 146, 20).build();
    }

    private void update(Settings settings) {
        ClientPresentationRuntime.setAccessibility(settings.build());
    }

    private Text label(String id) {
        String value;
        PresentationAccessibility settings = ClientPresentationRuntime.accessibility();
        value = switch (id) {
            case "quality" -> Text.translatable("options.vector_regnum.presentation_quality."
                    + settings.quality().name().toLowerCase(Locale.ROOT)).getString();
            case "reduced_motion" -> enabled(settings.reducedMotion());
            case "photosensitive" -> enabled(settings.photosensitive());
            default -> percent(value(id));
        };
        return Text.translatable("options.vector_regnum.presentation." + id, value);
    }

    private double value(String id) {
        PresentationAccessibility settings = ClientPresentationRuntime.accessibility();
        return switch (id) {
            case "quality" -> settings.quality().ordinal() / 2.0;
            case "particles" -> settings.particleDensity();
            case "darkness_fog" -> settings.darknessAndFog();
            case "flashes" -> settings.flashIntensity();
            case "chromatic" -> settings.chromaticIntensity();
            case "camera" -> settings.cameraMovement();
            case "audio" -> settings.audioIntensity();
            default -> 0;
        };
    }

    private static String percent(double value) {
        return Math.round(value * 100) + "%";
    }

    private static String enabled(boolean enabled) {
        return Text.translatable(enabled ? "options.on" : "options.off").getString();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xee100d16);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2,
                Math.max(10, height / 2 - 126), GOLD);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("screen.vector_regnum.presentation_accessibility.help"),
                width / 2, Math.max(22, height / 2 - 112), INK);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    private record Settings(PresentationQuality quality, double particles, double darkness,
            double flashes, double chromatic, double camera, double audio,
            boolean reducedMotion, boolean photosensitive) {
        private Settings(double qualityValue) {
            this(PresentationQuality.values()[Math.clamp((int) Math.round(qualityValue * 2), 0, 2)],
                    current().particles, current().darkness, current().flashes,
                    current().chromatic, current().camera, current().audio,
                    current().reducedMotion, current().photosensitive);
        }

        private static Settings current() {
            PresentationAccessibility value = ClientPresentationRuntime.accessibility();
            return new Settings(value.quality(), value.particleDensity(), value.darknessAndFog(),
                    value.flashIntensity(), value.chromaticIntensity(), value.cameraMovement(),
                    value.audioIntensity(), value.reducedMotion(), value.photosensitive());
        }

        private Settings particles(double value) { return copy(value, darkness, flashes, chromatic, camera, audio); }
        private Settings darkness(double value) { return new Settings(quality, particles, value, flashes, chromatic, camera, audio, reducedMotion, photosensitive); }
        private Settings flashes(double value) { return new Settings(quality, particles, darkness, value, chromatic, camera, audio, reducedMotion, photosensitive); }
        private Settings chromatic(double value) { return new Settings(quality, particles, darkness, flashes, value, camera, audio, reducedMotion, photosensitive); }
        private Settings camera(double value) { return new Settings(quality, particles, darkness, flashes, chromatic, value, audio, reducedMotion, photosensitive); }
        private Settings audio(double value) { return new Settings(quality, particles, darkness, flashes, chromatic, camera, value, reducedMotion, photosensitive); }
        private Settings toggleReducedMotion() { return new Settings(quality, particles, darkness, flashes, chromatic, camera, audio, !reducedMotion, photosensitive); }
        private Settings togglePhotosensitive() { return new Settings(quality, particles, darkness, flashes, chromatic, camera, audio, reducedMotion, !photosensitive); }
        private Settings copy(double particles, double darkness, double flashes, double chromatic, double camera, double audio) {
            return new Settings(quality, particles, darkness, flashes, chromatic, camera, audio, reducedMotion, photosensitive);
        }
        private PresentationAccessibility build() {
            return new PresentationAccessibility(quality, particles, darkness, flashes,
                    chromatic, camera, audio, reducedMotion, photosensitive);
        }
    }
}
