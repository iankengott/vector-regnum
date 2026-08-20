package vectorregnum.neoforge.editor;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import vectorregnum.core.circle.CircleCoordinate;
import vectorregnum.core.circle.SpellMedium;

/** Client half of the graphical editor protocol. */
public final class CircleEditorClientNetworking {
    private static boolean waitingForOpen;

    private CircleEditorClientNetworking() {
    }

    /** Registers the server-to-client editor snapshot payload. */
    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(CircleEditorSnapshotPayload.TYPE, CircleEditorSnapshotPayload.CODEC,
                CircleEditorClientNetworking::handleSnapshot);
    }

    /** Runs on the client main thread because the registrar uses its default handler thread. */
    public static void handleSnapshot(CircleEditorSnapshotPayload payload, IPayloadContext ignored) {
        Minecraft client = Minecraft.getInstance();
        if (waitingForOpen || !(client.screen instanceof CircleEditorScreen)) {
            waitingForOpen = false;
            client.setScreen(CircleEditorScreen.from(payload));
        } else {
            ((CircleEditorScreen) client.screen).applySnapshot(payload);
        }
    }

    /** Retained as a no-op source-compatibility hook for the client entrypoint. */
    public static void initialize() {
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
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() == null
                || !client.getConnection().hasChannel(CircleEditorPayload.TYPE)) {
            return;
        }
        client.getConnection().send(new CircleEditorPayload(action, data));
    }

    private static String coordinate(CircleCoordinate coordinate) {
        return coordinate.ring() + "\t" + coordinate.clockwiseSlot();
    }
}
