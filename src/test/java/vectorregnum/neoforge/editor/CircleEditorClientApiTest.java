package vectorregnum.neoforge.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import vectorregnum.core.circle.CircleCoordinate;
import vectorregnum.core.circle.MagicCircle;

class CircleEditorClientApiTest {
    @Test
    void screenSessionRoutesMutationsThroughServerGateway() {
        CircleEditorController serverController = new CircleEditorController(
                MagicCircle.empty("networked", "Networked", 1, 4),
                (circle, medium) -> CircleEditorController.BindingResult.accepted("bound"));
        AtomicReference<CircleEditorClientApi.ScreenSession> opened = new AtomicReference<>();
        var gateway = new CircleEditorClientApi.ServerGateway() {
            @Override
            public CircleEditorScreenModel open(int width, int height) {
                return serverController.snapshot(width, height);
            }

            @Override
            public CircleEditorScreenModel submit(CircleEditorRequest request, int width, int height) {
                return serverController.handle(request, width, height);
            }
        };

        var session = CircleEditorClientApi.open(opened::set, gateway, 800, 500);
        assertSame(session, opened.get());
        assertEquals(0, session.model().circle().sigils().size());

        var updated = session.dispatch(new CircleEditorRequest.Place(
                new CircleCoordinate(0, 0), "ORIGIN_SELF"));
        assertEquals(1, updated.circle().sigils().size());
        assertEquals(1, serverController.snapshot(800, 500).circle().sigils().size());
    }

    @Test
    void clientSeamRejectsUnreadablySmallViewport() {
        CircleEditorClientApi.ServerGateway unreachable = new CircleEditorClientApi.ServerGateway() {
            @Override public CircleEditorScreenModel open(int width, int height) { throw new AssertionError(); }
            @Override public CircleEditorScreenModel submit(CircleEditorRequest request,
                    int width, int height) { throw new AssertionError(); }
        };
        assertThrows(IllegalArgumentException.class,
                () -> CircleEditorClientApi.open(session -> { }, unreachable, 320, 200));
    }
}
