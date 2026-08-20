package vectorregnum.neoforge.editor;

/** Pixel geometry shared by rendering and hit-testing at every Minecraft GUI scale. */
public record CircleEditorLayout(
        boolean compact,
        Rect palette,
        Rect canvas,
        Rect inspector,
        int footerTop,
        int circleCenterX,
        int circleCenterY,
        int circleRadius) {
    private static final int MARGIN = 6;
    private static final int GAP = 4;
    private static final int CONTENT_TOP = 52;
    private static final int FOOTER_HEIGHT = 54;

    public CircleEditorLayout {
        if (palette == null || canvas == null || inspector == null
                || circleRadius < 20 || footerTop <= CONTENT_TOP) {
            throw new IllegalArgumentException("invalid circle editor layout");
        }
    }

    public static CircleEditorLayout calculate(int width, int height) {
        if (width < 320 || height < 180) {
            throw new IllegalArgumentException("editor viewport must be at least 320x180");
        }
        boolean compact = width < 640;
        int usableWidth = width - MARGIN * 2 - GAP * 2;
        int paletteWidth = compact
                ? clamp(usableWidth * 28 / 100, 92, 132)
                : clamp(usableWidth / 4, 170, 220);
        int inspectorWidth = compact
                ? clamp(usableWidth * 29 / 100, 108, 145)
                : clamp(usableWidth / 4, 190, 240);
        int canvasWidth = usableWidth - paletteWidth - inspectorWidth;
        if (canvasWidth < 96) {
            int shortage = 96 - canvasWidth;
            int paletteCut = Math.min(shortage / 2 + shortage % 2, paletteWidth - 82);
            paletteWidth -= paletteCut;
            inspectorWidth -= shortage - paletteCut;
            canvasWidth = usableWidth - paletteWidth - inspectorWidth;
        }

        int footerTop = height - FOOTER_HEIGHT;
        int contentBottom = footerTop - GAP;
        int contentHeight = contentBottom - CONTENT_TOP;
        Rect palette = new Rect(MARGIN, CONTENT_TOP, paletteWidth, contentHeight);
        Rect canvas = new Rect(palette.right() + GAP, CONTENT_TOP, canvasWidth, contentHeight);
        Rect inspector = new Rect(canvas.right() + GAP, CONTENT_TOP, inspectorWidth, contentHeight);
        int centerX = canvas.x() + canvas.width() / 2;
        int centerY = canvas.y() + Math.max(20, (canvas.height() - 24) / 2);
        int radius = Math.max(20, Math.min(140,
                Math.min((canvas.width() - 18) / 2, (canvas.height() - 38) / 2)));
        return new CircleEditorLayout(compact, palette, canvas, inspector, footerTop,
                centerX, centerY, radius);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record Rect(int x, int y, int width, int height) {
        public Rect {
            if (width < 1 || height < 1) {
                throw new IllegalArgumentException("layout rectangles must be positive");
            }
        }

        public int right() { return x + width; }
        public int bottom() { return y + height; }

        public boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < right() && pointY >= y && pointY < bottom();
        }
    }
}
