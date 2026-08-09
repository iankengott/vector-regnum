package vectorregnum.compiler;

import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.entity.Entity;

public class SpellState {
    public final World world;
    public final Entity caster;
    
    // Spell structural properties
    public Vec3d origin;
    public Vec3d direction;
    public double radius = 0.0;
    
    // Properties determined by sigils
    public String element = null; // e.g. "fire", "arcane"
    public String shape = null;   // e.g. "projectile", "aura"
    public double magnitude = 1.0;
    
    public boolean isBroken = false;
    public String breakReason = null;
    public int breakIndex = -1;
    
    public SpellState(World world, Entity caster) {
        this.world = world;
        this.caster = caster;
    }

    /**
     * In Vector-Regnum, when a spell breaks, it doesn't just fail.
     * It mutates and acts unpredictably.
     */
    public void breakSpell(String reason, int index) {
        this.isBroken = true;
        this.breakReason = reason;
        this.breakIndex = index;
    }
}
