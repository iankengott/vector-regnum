package vectorregnum.neoforge;

import java.util.Set;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.lwjgl.glfw.GLFW;
import vectorregnum.neoforge.editor.CircleEditorClientNetworking;
import vectorregnum.neoforge.guide.FieldManualClientApi;
import vectorregnum.neoforge.ponder.PonderTraceClientNetworking;
import vectorregnum.neoforge.progression.ProgressionSync;
import vectorregnum.neoforge.presentation.ClientPresentationRuntime;
import vectorregnum.neoforge.presentation.PresentationAccessibilityScreen;

/** Client-only presentation entrypoint; it never decides gameplay outcomes. */
public final class VectorRegnumClient {
    private static final Runnable OPEN_MANUAL = FieldManualClientApi.minecraftOpener(
            VectorRegnumClient::clientUnlocks,
            () -> PonderTraceClientNetworking.requestLatest("manual"));

    private static KeyMapping ponderKey;
    private static KeyMapping editorKey;
    private static KeyMapping presentationQualityKey;

    private VectorRegnumClient() {
    }

    /**
     * Called by the client-side progression payload handler. The copy keeps
     * handlers from retaining a mutable decode buffer or caller-owned set.
     */
    public static void setClientUnlocks(Set<String> unlocks) {
        ProgressionSync.setClientUnlocks(unlocks);
    }

    public static Set<String> clientUnlocks() {
        return ProgressionSync.clientUnlocks();
    }

    /** Mod event bus handlers are discovered only on a physical client. */
    @EventBusSubscriber(modid = VectorRegnumMod.MOD_ID, value = Dist.CLIENT,
            bus = EventBusSubscriber.Bus.MOD)
    public static final class ModBusEvents {
        private ModBusEvents() {
        }

        @SubscribeEvent
        public static void clientSetup(FMLClientSetupEvent ignored) {
            CircleEditorClientNetworking.initialize();
            PonderTraceClientNetworking.initialize();
            ClientPresentationRuntime.initialize();
        }

        @SubscribeEvent
        public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
            ponderKey = new KeyMapping("key.vector_regnum.ponder",
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.vector_regnum");
            editorKey = new KeyMapping("key.vector_regnum.circle_editor",
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.vector_regnum");
            presentationQualityKey = new KeyMapping("key.vector_regnum.presentation_quality",
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, "category.vector_regnum");
            event.register(ponderKey);
            event.register(editorKey);
            event.register(presentationQualityKey);
        }

        @SubscribeEvent
        public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) ignored ->
                    ClientPresentationRuntime.onResourceReload());
        }
    }

    /** Game event handlers remain isolated from the common {@code @Mod} class. */
    @EventBusSubscriber(modid = VectorRegnumMod.MOD_ID, value = Dist.CLIENT,
            bus = EventBusSubscriber.Bus.GAME)
    public static final class GameBusEvents {
        private GameBusEvents() {
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post ignored) {
            Minecraft client = Minecraft.getInstance();
            while (ponderKey != null && ponderKey.consumeClick()) {
                PonderTraceClientNetworking.requestLatest("keybind");
            }
            while (editorKey != null && editorKey.consumeClick()) {
                CircleEditorClientNetworking.open();
            }
            while (presentationQualityKey != null && presentationQualityKey.consumeClick()) {
                client.setScreen(new PresentationAccessibilityScreen());
            }
        }

        @SubscribeEvent
        public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
            // This event is also fired for the logical server in an integrated
            // client. The manual is a client presentation and must not cancel
            // the server's authoritative item interaction.
            if (!event.getLevel().isClientSide || !isFieldManual(event.getItemStack())) {
                return;
            }
            OPEN_MANUAL.run();
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }

        @SubscribeEvent
        public static void onScreenInit(ScreenEvent.Init.Post event) {
            Screen screen = event.getScreen();
            Minecraft client = Minecraft.getInstance();
            if (!(screen instanceof BookViewScreen) || client.player == null
                    || (!isFieldManual(client.player.getMainHandItem())
                            && !isFieldManual(client.player.getOffhandItem()))) {
                return;
            }

            // WrittenBookItem can still win the client screen race on some
            // launch paths. Re-check the active screen on the next client task
            // so ordinary vanilla books remain untouched.
            client.execute(() -> {
                if (client.screen == screen) {
                    OPEN_MANUAL.run();
                }
            });
        }
    }

    private static boolean isFieldManual(ItemStack stack) {
        if (!stack.is(Items.WRITTEN_BOOK)) {
            return false;
        }
        WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        return content != null
                && content.title().raw().startsWith(TutorialGuide.FIELD_MANUAL_TITLE_PREFIX);
    }
}
