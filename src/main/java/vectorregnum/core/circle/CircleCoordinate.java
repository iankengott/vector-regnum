package vectorregnum.core.circle;

/** A discrete position on a magic circle. Ring zero is the outermost ring. */
public record CircleCoordinate(int ring, int clockwiseSlot) {
    public CircleCoordinate {
        if (ring < 0) {
            throw new IllegalArgumentException("ring must be non-negative");
        }
        if (clockwiseSlot < 0) {
            throw new IllegalArgumentException("clockwiseSlot must be non-negative");
        }
    }

    public void requireInside(int ringCount, int slotsPerRing) {
        if (ring >= ringCount || clockwiseSlot >= slotsPerRing) {
            throw new IllegalArgumentException(
                    "coordinate " + this + " is outside a " + ringCount + "x" + slotsPerRing + " circle");
        }
    }
}
