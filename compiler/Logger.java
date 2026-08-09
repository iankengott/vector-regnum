package vectorregnum.compiler;

public class Logger {
    public static boolean ENABLED = true; // Easily toggleable

    public static void log(String message) {
        if (ENABLED) {
            System.out.println("[VR Log] " + message);
        }
    }
    
    public static void error(String message) {
        // Errors always print, independent of the verbose ENABLED flag.
        // A hidden compiler/VM error is worse than noisy output.
        System.err.println("[VR Error] " + message);
    }
}
