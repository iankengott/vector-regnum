package net.minecraft.entity;

import net.minecraft.util.math.Vec3d;

public class Entity {
    private final String name;
    private final Vec3d pos;

    public Entity(String name, Vec3d pos) {
        this.name = name;
        this.pos = pos;
    }

    public Vec3d getPos() {
        return pos;
    }

    public String getName() {
        return name;
    }
}
