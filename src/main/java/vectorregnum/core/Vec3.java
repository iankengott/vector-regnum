package vectorregnum.core;

/** Loader-neutral, immutable three-dimensional vector. */
public record Vec3(double x, double y, double z) {
    public static final Vec3 ZERO = new Vec3(0.0, 0.0, 0.0);
    private static final double ZERO_EPSILON = 1.0e-12;

    public Vec3 {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Vector components must be finite");
        }
    }

    public double length() {
        return Math.hypot(x, Math.hypot(y, z));
    }

    public boolean isEffectivelyZero() {
        return length() <= ZERO_EPSILON;
    }

    public Vec3 normalized() {
        double length = length();
        if (!Double.isFinite(length) || length <= ZERO_EPSILON) {
            throw new IllegalStateException("Cannot normalize a zero-length direction");
        }
        return new Vec3(x / length, y / length, z / length);
    }
}
