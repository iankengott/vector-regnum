package vectorregnum;

import vectorregnum.compiler.Sigil;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public class SpellLibrary {

    public static List<SpellTestRecord> getTests() {
        List<SpellTestRecord> tests = new ArrayList<>();

        // 1. A perfectly structured, basic attack spell
        tests.add(new SpellTestRecord(
            "Basic Firebolt",
            "WORKS: Properly constructs origin, element, direction, shape, and execution.",
            Arrays.asList(
                new Sigil("ORIGIN_SELF"),
                new Sigil("ELEMENT_FIRE"),
                new Sigil("VECTOR_FORWARD"),
                new Sigil("SHAPE_PROJECTILE"),
                new Sigil("EXPAND", 1.0),
                new Sigil("EXECUTE")
            )
        ));

        // 2. A spell with a valid structure, but using a different element and shape
        tests.add(new SpellTestRecord(
            "Frost Nova",
            "WORKS: Aura shape does not require a direction vector.",
            Arrays.asList(
                new Sigil("ORIGIN_SELF"),
                new Sigil("ELEMENT_FROST"), // "FROST" is just a string, VM will accept it
                new Sigil("SHAPE_AURA"),
                new Sigil("EXPAND", 5.0),
                new Sigil("EXECUTE")
            )
        ));

        // 3. Syntax Error: Missing Origin
        tests.add(new SpellTestRecord(
            "Ground Zero (Missing Origin)",
            "ISSUE AT INDEX 0: Trying to apply an element before defining an origin. Causes 'Internal Mana Detonation'.",
            Arrays.asList(
                new Sigil("ELEMENT_FIRE"), // Fails here
                new Sigil("ORIGIN_SELF"),
                new Sigil("EXECUTE")
            )
        ));

        // 4. State Dependency: Modifying structure before it exists
        tests.add(new SpellTestRecord(
            "Premature Expansion",
            "ISSUE AT INDEX 2: Tries to expand radius before resolving the physical shape. Causes 'Unstructured Element Burst'.",
            Arrays.asList(
                new Sigil("ORIGIN_SELF"),
                new Sigil("ELEMENT_ARCANE"),
                new Sigil("EXPAND", 10.0), // Fails here
                new Sigil("SHAPE_AURA"),
                new Sigil("EXECUTE")
            )
        ));

        // 5. Type Mismatch: Passing wrong data type to an instruction
        tests.add(new SpellTestRecord(
            "Greedy Arithmetic",
            "ISSUE AT INDEX 4: EXPAND requires a Double, given a String. Causes 'Violent Miscast' because shape was already established.",
            Arrays.asList(
                new Sigil("ORIGIN_SELF"),
                new Sigil("ELEMENT_FIRE"),
                new Sigil("VECTOR_FORWARD"),
                new Sigil("SHAPE_PROJECTILE"),
                new Sigil("EXPAND", "massive"), // Fails here
                new Sigil("EXECUTE")
            )
        ));

        // 6. Incomplete Logic: Forgetting to resolve shape
        tests.add(new SpellTestRecord(
            "Formless Void",
            "ISSUE AT INDEX 3: EXECUTE called without a shape. Causes 'Unstructured Element Burst'.",
            Arrays.asList(
                new Sigil("ORIGIN_SELF"),
                new Sigil("ELEMENT_VOID"),
                new Sigil("VECTOR_FORWARD"),
                new Sigil("EXECUTE") // Fails here
            )
        ));
        
        // 7. State Dependency: Projectile requires direction
        tests.add(new SpellTestRecord(
            "Blind Projectile",
            "ISSUE AT INDEX 2: A projectile shape requires a vector to be set beforehand. Causes 'Unstructured Element Burst'.",
            Arrays.asList(
                new Sigil("ORIGIN_SELF"),
                new Sigil("ELEMENT_FIRE"),
                new Sigil("SHAPE_PROJECTILE"), // Fails here (no VECTOR_FORWARD called)
                new Sigil("EXECUTE")
            )
        ));

        // 8. Unknown Sigil: a typo'd/corrupted rune must hard-fail at its position
        tests.add(new SpellTestRecord(
            "Corrupted Rune (Unknown Sigil)",
            "ISSUE AT INDEX 2: An unknown/typo'd sigil cannot be compiled. Causes 'Unstructured Element Burst'.",
            Arrays.asList(
                new Sigil("ORIGIN_SELF"),
                new Sigil("ELEMENT_FIRE"),
                new Sigil("SHAP_PROJECTILE"), // typo: fails here (was SHAPE_PROJECTILE)
                new Sigil("EXECUTE")
            )
        ));

        // 9. Modifier: AMPLIFY scales magnitude once a shape exists
        tests.add(new SpellTestRecord(
            "Amplified Firebolt",
            "WORKS: AMPLIFY scales magnitude (->3.0) after the shape is resolved; EXPAND sets radius (->2.0).",
            Arrays.asList(
                new Sigil("ORIGIN_SELF"),
                new Sigil("ELEMENT_FIRE"),
                new Sigil("VECTOR_FORWARD"),
                new Sigil("SHAPE_PROJECTILE"),
                new Sigil("EXPAND", 2.0),
                new Sigil("AMPLIFY", 3.0),
                new Sigil("EXECUTE")
            )
        ));

        return tests;
    }
}
