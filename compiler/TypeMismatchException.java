package vectorregnum.compiler;

public class TypeMismatchException extends Exception {
    public final int instructionIndex;

    public TypeMismatchException(String message, int index) {
        super(message);
        this.instructionIndex = index;
    }
}
