package vectorregnum.core.vm2;

/** Finite loader-neutral three-dimensional coordinate/vector. */
public record Vector3(double x, double y, double z) {
    public static final Vector3 ZERO = new Vector3(0, 0, 0);

    public Vector3 {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("components must be finite");
        }
    }

    public double length() {
        return Math.hypot(x, Math.hypot(y, z));
    }

    public Vector3 plus(Vector3 other) {
        return new Vector3(x + other.x, y + other.y, z + other.z);
    }

    public Vector3 minus(Vector3 other) {
        return new Vector3(x - other.x, y - other.y, z - other.z);
    }

    public Vector3 scaled(double scalar) {
        if (!Double.isFinite(scalar)) throw new IllegalArgumentException("scalar must be finite");
        return new Vector3(x * scalar, y * scalar, z * scalar);
    }

    public Vector3 normalized() {
        double magnitude = length();
        if (magnitude <= 1.0e-12) throw new IllegalStateException("cannot normalize zero vector");
        return scaled(1.0 / magnitude);
    }
}
