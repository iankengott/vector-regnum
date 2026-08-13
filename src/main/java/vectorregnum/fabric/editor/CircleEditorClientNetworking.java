package vectorregnum.fabric.editor;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import vectorregnum.core.circle.CircleCoordinate;
import vectorregnum.core.circle.SpellMedium;

/** Client half of the graphical editor protocol. */
public final class CircleEditorClientNetworking {
    private static boolean initialized;
    private static boolean waitingForOpen;

    private CircleEditorClientNetworking() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        ClientPlayNetworking.registerGlobalReceiver(CircleEditorSnapshotPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    if (waitingForOpen || !(context.client().currentScreen instanceof CircleEditorScreen)) {
                        waitingForOpen = false;
                        context.client().setScreen(CircleEditorScreen.from(payload));
                    } else {
                        ((CircleEditorScreen) context.client().currentScreen).applySnapshot(payload);
                    }
                }));
    }

    public static void open() {
        waitingForOpen = true;
        send("open", "");
    }

    public static void send(CircleEditorRequest request) {
        switch (request) {
            case CircleEditorRequest.Select select -> send("select", coordinate(select.coordinate()));
            case CircleEditorRequest.SearchPalette search -> send("search", search.query());
            case CircleEditorRequest.Place place -> send("place",
                    coordinate(place.coordinate()) + "\t" + place.sigilId());
            case CircleEditorRequest.Move move -> send("move", coordinate(move.source())
                    + "\t" + coordinate(move.destination()));
            case CircleEditorRequest.Remove remove -> send("remove", coordinate(remove.coordinate()));
            case CircleEditorRequest.UpdateParameters update -> send("params",
                    coordinate(update.coordinate()) + "\t"
                            + CircleEditorInteraction.encodeParameterInput(update.values()));
            case CircleEditorRequest.Undo ignored -> send("undo", "");
            case CircleEditorRequest.Compile ignored -> send("compile", "");
            case CircleEditorRequest.CaptureFaceAnchor ignored -> send("anchor_face", "");
            case CircleEditorRequest.ClearAnchor ignored -> send("anchor_clear", "");
            case CircleEditorRequest.Bind bind -> send("bind", bind.medium().name());
        }
    }

    public static void send(String action, String data) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null
                || !ClientPlayNetworking.canSend(CircleEditorPayload.ID)) {
            return;
        }
        ClientPlayNetworking.send(new CircleEditorPayload(action, data));
    }

    private static String coordinate(CircleCoordinate coordinate) {
        return coordinate.ring() + "\t" + coordinate.clockwiseSlot();
    }
}
