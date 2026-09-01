package vectorregnum.api.v1;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Internal validation shared by the immutable v1 API values. */
final class ApiValidation {
    static final int MAX_IDENTIFIER_LENGTH = 128;
    static final double MIN_FACTOR = 0.5;
    static final double MAX_FACTOR = 2.0;

    private static final Set<String> ELEMENTS = Set.of(
            "water", "fire", "air", "earth", "lightning", "time", "space", "light",
            "dark", "nature", "ice", "sound", "void", "arcane");
    private static final Set<String> NATURAL_ELEMENTS = Set.of(
            "water", "fire", "air", "earth", "lightning", "time", "space", "light",
            "dark", "nature", "ice", "sound", "void");

    private ApiValidation() {
    }

    static String text(String value, String name, int maximumLength) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        if (value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " exceeds " + maximumLength + " characters");
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index)) || Character.isWhitespace(value.charAt(index))) {
                throw new IllegalArgumentException(name + " cannot contain whitespace or control characters");
            }
        }
        return value;
    }

    static String identifier(String value, String name) {
        return text(value, name, MAX_IDENTIFIER_LENGTH);
    }

    static String optionalIdentifier(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isEmpty()) {
            return value;
        }
        return identifier(value, name);
    }

    static String boundedText(String value, String name, int maximumLength) {
        Objects.requireNonNull(value, name);
        if (value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " exceeds " + maximumLength + " characters");
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw new IllegalArgumentException(name + " cannot contain control characters");
            }
        }
        return value;
    }

    static String sourceId(String value) {
        String source = text(value, "sourceId", IntegrationRegistry.MAX_SOURCE_ID_LENGTH);
        int separator = source.indexOf(':');
        if (separator <= 0 || separator != source.lastIndexOf(':')
                || separator == source.length() - 1) {
            throw new IllegalArgumentException("sourceId must be namespaced as namespace:path");
        }
        for (int index = 0; index < source.length(); index++) {
            char character = source.charAt(index);
            if (index < separator) {
                if (!isNamespaceCharacter(character)) {
                    throw new IllegalArgumentException("sourceId contains an invalid namespace character");
                }
            } else if (character != ':' && !isPathCharacter(character)) {
                throw new IllegalArgumentException("sourceId contains an invalid path character");
            }
        }
        return source;
    }

    private static boolean isNamespaceCharacter(char character) {
        return character >= 'a' && character <= 'z'
                || character >= '0' && character <= '9'
                || character == '_' || character == '-' || character == '.';
    }

    private static boolean isPathCharacter(char character) {
        return isNamespaceCharacter(character) || character == '/';
    }

    static String element(String value, String name) {
        String normalized = identifier(value, name).toLowerCase(Locale.ROOT);
        if (normalized.equals("frost")) {
            normalized = "ice";
        }
        if (!ELEMENTS.contains(normalized)) {
            throw new IllegalArgumentException(name + " is not a canonical element: " + value);
        }
        return normalized;
    }

    static String naturalElement(String value, String name) {
        String normalized = element(value, name);
        if (!NATURAL_ELEMENTS.contains(normalized)) {
            throw new IllegalArgumentException(name + " cannot be arcane");
        }
        return normalized;
    }

    static String naturalElementOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return naturalElement(value, "naturalElement");
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static double nonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
        return value;
    }

    static double factor(double value, String name) {
        if (!Double.isFinite(value) || value < MIN_FACTOR || value > MAX_FACTOR) {
            throw new IllegalArgumentException(name + " must be finite and between "
                    + MIN_FACTOR + " and " + MAX_FACTOR);
        }
        return value;
    }

    static double clampFactor(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("modifier factor composition overflowed");
        }
        return Math.max(MIN_FACTOR, Math.min(MAX_FACTOR, value));
    }
}
