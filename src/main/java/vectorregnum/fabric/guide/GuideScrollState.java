package vectorregnum.fabric.guide;

/** Bounded, render-neutral vertical viewport state for a Field Manual page. */
public final class GuideScrollState {
    private int offset;
    private int maximum;

    public int offset() { return offset; }
    public int maximum() { return maximum; }
    public boolean canScroll() { return maximum > 0; }

    public void setExtents(int contentHeight, int viewportHeight) {
        if (contentHeight < 0 || viewportHeight < 1) {
            throw new IllegalArgumentException("guide scroll extents are invalid");
        }
        maximum = Math.max(0, contentHeight - viewportHeight);
        offset = Math.clamp(offset, 0, maximum);
    }

    public boolean scrollBy(int pixels) {
        int previous = offset;
        offset = Math.clamp(offset + pixels, 0, maximum);
        return offset != previous;
    }

    public void toStart() { offset = 0; }
    public void toEnd() { offset = maximum; }
}
