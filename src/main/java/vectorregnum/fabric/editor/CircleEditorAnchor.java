package vectorregnum.fabric.editor;

import java.util.Locale;
import java.util.Objects;

/**
 * Immutable server-captured block-face anchor for an editor session. It stores
 * world coordinates only and deliberately contains no player/entity identity,
 * so an anchored circle cannot follow its author.
 */
public record CircleEditorAnchor(String dimension, int x, int y, int z, Face face) {
    private static final int WORLD_BORDER = 30_000_000;

    public CircleEditorAnchor {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(face, "face");
        if (dimension.isBlank() || dimension.length() > 128) {
            throw new IllegalArgumentException("dimension must contain 1 to 128 characters");
        }
        if (Math.abs((long) x) > WORLD_BORDER || Math.abs((long) z) > WORLD_BORDER) {
            throw new IllegalArgumentException("anchor lies outside the supported world border");
        }
    }

    public String description() {
        return dimension + " " + x + "," + y + "," + z + " "
                + face.name().toLowerCase(Locale.ROOT);
    }

    public enum Face {
        DOWN(0, -1, 0), UP(0, 1, 0), NORTH(0, 0, -1), SOUTH(0, 0, 1),
        WEST(-1, 0, 0), EAST(1, 0, 0);

        private final int offsetX;
        private final int offsetY;
        private final int offsetZ;

        Face(int offsetX, int offsetY, int offsetZ) {
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
        }

        public int offsetX() { return offsetX; }
        public int offsetY() { return offsetY; }
        public int offsetZ() { return offsetZ; }
    }
}
