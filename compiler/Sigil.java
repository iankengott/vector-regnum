package vectorregnum.compiler;

public class Sigil {
    public final String type;
    public final Object[] params;

    public Sigil(String type, Object... params) {
        this.type = type;
        this.params = params != null ? params : new Object[0];
    }
}