package vectorregnum.neoforge.editor;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import vectorregnum.core.circle.CirclePersistence;
import vectorregnum.core.circle.SpellMedium;
import vectorregnum.neoforge.CircleAuthoringService;

/** Server-authoritative gateway for the graphical editor request protocol. */
public final class CircleEditorNetworking {
    private CircleEditorNetworking() {
    }

    /** Registers the client-to-server editor request payload. */
    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(CircleEditorPayload.TYPE, CircleEditorPayload.CODEC,
                CircleEditorNetworking::handlePayload);
    }

    /** Handles requests on NeoForge's main server thread (the registrar default). */
    public static void handlePayload(CircleEditorPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            handle(player, payload);
        }
    }

    private static void handle(ServerPlayer player, CircleEditorPayload payload) {
        String status;
        boolean bindable = false;
        try {
            switch (payload.action()) {
                case "open", "select", "search" -> status = "Circle loaded from the server";
                case "place" -> {
                    String[] parts = payload.data().split("\\t", 3);
                    require(parts.length == 3, "place requires ring, slot, and sigil");
                    boolean changed = CircleAuthoringService.place(player,
                            Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), parts[2]);
                    status = changed ? "Sigil placed" : "Placement rejected";
                }
                case "move" -> {
                    String[] parts = payload.data().split("\\t", 4);
                    require(parts.length == 4,
                            "move requires source ring/slot and destination ring/slot");
                    boolean changed = CircleAuthoringService.move(player,
                            Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                    status = changed ? "Sigil moved with its parameters" : "Move rejected";
                }
                case "remove" -> {
                    String[] parts = payload.data().split("\\t", 2);
                    require(parts.length == 2, "remove requires ring and slot");
                    boolean changed = CircleAuthoringService.remove(player,
                            Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                    status = changed ? "Sigil removed" : "Removal rejected";
                }
                case "params" -> {
                    String[] parts = payload.data().split("\\t", 3);
                    require(parts.length == 3, "params requires ring, slot, and values");
                    boolean changed = CircleAuthoringService.parameterizeValues(player,
                            Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), parts[2]);
                    status = changed ? "Parameters updated" : "Parameters rejected";
                }
                case "undo" -> {
                    status = CircleAuthoringService.undo(player)
                            ? "Undo applied" : "Nothing to undo";
                }
                case "compile" -> {
                    CircleAuthoringService.AuthoringCompilation compilation =
                            CircleAuthoringService.compile(player);
                    bindable = !compilation.hasErrors();
                    status = bindable ? "Circle compiled" : "Resolve compiler diagnostics";
                }
                case "anchor_face" -> {
                    require(payload.data().isEmpty(), "anchor coordinates are server-captured");
                    CircleAuthoringService.EditorAnchorResult result =
                            CircleAuthoringService.captureEditorAnchor(player);
                    status = result.message();
                }
                case "anchor_clear" -> {
                    require(payload.data().isEmpty(), "anchor clear carries no data");
                    status = CircleAuthoringService.clearEditorAnchor(player).message();
                }
                case "bind" -> {
                    SpellMedium medium = SpellMedium.valueOf(payload.data().toUpperCase());
                    bindable = CircleAuthoringService.giveMedium(player, medium);
                    status = bindable ? "Bound " + medium.name().toLowerCase() : "Binding rejected";
                }
                default -> throw new IllegalArgumentException("unknown editor action");
            }
        } catch (RuntimeException exception) {
            status = "Rejected: " + safeMessage(exception);
        }
        send(player, status, bindable);
    }

    public static void send(ServerPlayer player, String status, boolean bindable) {
        if (!player.connection.hasChannel(CircleEditorSnapshotPayload.TYPE)) {
            return;
        }
        String encoded = CirclePersistence.encode(CircleAuthoringService.session(player).current());
        player.connection.send(new CircleEditorSnapshotPayload(encoded, status, bindable,
                CircleAuthoringService.editorAnchorDescription(player)));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
