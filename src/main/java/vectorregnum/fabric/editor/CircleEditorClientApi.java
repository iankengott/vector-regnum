package vectorregnum.fabric.editor;

import java.util.Objects;

/**
 * Dependency-free client integration seam. The Minecraft Screen renders the
 * snapshot and sends every mutation through a server-backed gateway.
 */
public final class CircleEditorClientApi {
    private CircleEditorClientApi() { }

    @FunctionalInterface
    public interface ScreenHost {
        void open(ScreenSession session);
    }

    public interface ServerGateway {
        CircleEditorScreenModel open(int viewportWidth, int viewportHeight);

        CircleEditorScreenModel submit(CircleEditorRequest request,
                int viewportWidth, int viewportHeight);
    }

    public static ScreenSession open(ScreenHost host, ServerGateway gateway,
            int viewportWidth, int viewportHeight) {
        Objects.requireNonNull(host, "host");
        ScreenSession session = new ScreenSession(gateway, viewportWidth, viewportHeight);
        host.open(session);
        return session;
    }

    public static final class ScreenSession {
        private final ServerGateway gateway;
        private int viewportWidth;
        private int viewportHeight;
        private CircleEditorScreenModel model;

        private ScreenSession(ServerGateway gateway, int viewportWidth, int viewportHeight) {
            this.gateway = Objects.requireNonNull(gateway, "gateway");
            requireViewport(viewportWidth, viewportHeight);
            this.viewportWidth = viewportWidth;
            this.viewportHeight = viewportHeight;
            model = gateway.open(viewportWidth, viewportHeight);
        }

        public CircleEditorScreenModel model() { return model; }

        public CircleEditorScreenModel dispatch(CircleEditorRequest request) {
            model = gateway.submit(Objects.requireNonNull(request, "request"),
                    viewportWidth, viewportHeight);
            return model;
        }

        public void resize(int width, int height) {
            requireViewport(width, height);
            viewportWidth = width;
            viewportHeight = height;
            if (model != null) model = gateway.open(width, height);
        }

        private static void requireViewport(int width, int height) {
            if (width < 480 || height < 270) {
                throw new IllegalArgumentException("editor viewport must be at least 480x270");
            }
        }
    }
}
