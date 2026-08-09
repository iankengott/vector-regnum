package vectorregnum.compiler;

public class StateDependencyException extends Exception {
    public final int instructionIndex;

    public StateDependencyException(String message, int index) {
        super(message);
        this.instructionIndex = index;
    }
}
