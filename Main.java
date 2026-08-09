package vectorregnum;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import vectorregnum.compiler.*;
import vectorregnum.compiler.Compiler; // explicit: disambiguate from java.lang.Compiler (present on older JDKs)

import java.util.List;

public class Main {

    public static void main(String[] args) {
        System.out.println("============================================");
        System.out.println("  VECTOR-REGNUM COMPILER & VM TEST SUITE");
        System.out.println("============================================\n");

        // Set Logger to false if you want cleaner output without individual instruction logs
        Logger.ENABLED = false;
        
        // Mock World and Caster
        World mockWorld = new World();
        Entity mockCaster = new Entity("Archmage_Ian", new Vec3d(10, 64, 10));

        List<SpellTestRecord> tests = SpellLibrary.getTests();
        
        int count = 1;
        for (SpellTestRecord test : tests) {
            System.out.println("--------------------------------------------------");
            System.out.println("TEST " + count + ": " + test.name);
            System.out.println("EXPECTED: " + test.expectedState);
            System.out.println("--------------------------------------------------");
            
            SpellState state = new SpellState(mockWorld, mockCaster);
            CompiledSpell spell = Compiler.compile(test.sigils);
            
            System.out.println("-> Spell Mana Cost: " + spell.totalManaCost + " | Complexity: " + spell.totalComplexity);
            VirtualMachine.execute(spell, state);
            
            System.out.println("\n");
            count++;
        }
    }
}
