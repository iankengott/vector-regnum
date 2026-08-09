package net.minecraft.util.math;

public class Vec3d {
    public static final Vec3d ZERO = new Vec3d(0, 0, 0);
    public final double x, y, z;
    
    public Vec3d(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
    
    @Override
    public String toString() {
        return "(" + x + ", " + y + ", " + z + ")";
    }
}
