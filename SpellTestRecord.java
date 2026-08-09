package vectorregnum;

import vectorregnum.compiler.Sigil;
import java.util.List;

public class SpellTestRecord {
    public final String name;
    public final String expectedState;
    public final List<Sigil> sigils;

    public SpellTestRecord(String name, String expectedState, List<Sigil> sigils) {
        this.name = name;
        this.expectedState = expectedState;
        this.sigils = sigils;
    }
}
