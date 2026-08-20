package vectorregnum.neoforge;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.TypedActionResult;
import org.lwjgl.glfw.GLFW;
import vectorregnum.neoforge.guide.FieldManualClientApi;
import vectorregnum.neoforge.editor.CircleEditorClientNetworking;
import vectorregnum.neoforge.ponder.PonderTraceClientNetworking;
import vectorregnum.neoforge.progression.ProgressionPayload;
import vectorregnum.neoforge.presentation.ClientPresentationRuntime;
import vectorregnum.neoforge.presentation.PresentationAccessibilityScreen;

/** Client-only presentation entrypoint; it never decides gameplay outcomes. */
public final class VectorRegnumClient implements ClientModInitializer {
    private static final AtomicReference<Set<String>> UNLOCKS = new AtomicReference<>(Set.of());
    private static KeyBinding ponderKey;
    private static KeyBinding editorKey;
    private static KeyBinding presentationQualityKey;
    private static Runnable openManual;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(ProgressionPayload.ID,
                (payload, context) -> context.client().execute(
                        () -> UNLOCKS.set(Set.copyOf(payload.unlocks()))));
        CircleEditorClientNetworking.initialize();
        PonderTraceClientNetworking.initialize();
        ClientPresentationRuntime.initialize();

        openManual = FieldManualClientApi.minecraftOpener(UNLOCKS::get,
                () -> PonderTraceClientNetworking.requestLatest("manual"));
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!isFieldManual(stack)) {
                return TypedActionResult.pass(stack);
            }
            openManual.run();
            return TypedActionResult.success(stack, true);
        });
        // WrittenBookItem can still win the client screen race on some launch paths.
        // Replace only our uniquely titled manual, leaving every ordinary book alone.
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof BookScreen && client.player != null
                    && (isFieldManual(client.player.getMainHandStack())
                            || isFieldManual(client.player.getOffHandStack()))) {
                client.execute(() -> {
                    if (client.currentScreen == screen) {
                        openManual.run();
                    }
                });
            }
        });

        ponderKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.vector_regnum.ponder", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K,
                "category.vector_regnum"));
        editorKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.vector_regnum.circle_editor", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V,
                "category.vector_regnum"));
        presentationQualityKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.vector_regnum.presentation_quality", InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_O, "category.vector_regnum"));
        ClientTickEvents.END_CLIENT_TICK.register(VectorRegnumClient::tick);
    }

    public static Set<String> clientUnlocks() {
        return UNLOCKS.get();
    }

    private static void tick(MinecraftClient client) {
        while (ponderKey.wasPressed()) {
            PonderTraceClientNetworking.requestLatest("keybind");
        }
        while (editorKey.wasPressed()) {
            CircleEditorClientNetworking.open();
        }
        while (presentationQualityKey.wasPressed()) {
            client.setScreen(new PresentationAccessibilityScreen());
        }
    }

    private static boolean isFieldManual(ItemStack stack) {
        if (!stack.isOf(Items.WRITTEN_BOOK)) {
            return false;
        }
        WrittenBookContentComponent content = stack.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
        return content != null
                && content.title().raw().startsWith(TutorialGuide.FIELD_MANUAL_TITLE_PREFIX);
    }
}
