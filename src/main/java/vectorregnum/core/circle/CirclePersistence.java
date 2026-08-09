package vectorregnum.core.circle;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/** Deterministic, checksummed persistence codec for authored circles. */
public final class CirclePersistence {
    private static final String HEADER = "vr-circle\t1";
    private static final int MAX_DOCUMENT_BYTES = 1_048_576;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private CirclePersistence() {
    }

    public static String encode(MagicCircle circle) {
        StringBuilder body = new StringBuilder(HEADER).append('\n')
                .append("id\t").append(text(circle.id())).append('\n')
                .append("name\t").append(text(circle.name())).append('\n')
                .append("rings\t").append(circle.ringCount()).append('\n')
                .append("slots\t").append(circle.slotsPerRing()).append('\n');
        for (PlacedSigil sigil : circle.executionOrder()) {
            body.append("sigil\t")
                    .append(sigil.coordinate().ring()).append('\t')
                    .append(sigil.coordinate().clockwiseSlot()).append('\t')
                    .append(text(sigil.type())).append('\t')
                    .append(sigil.parameters().size());
            for (CircleValue parameter : sigil.parameters()) {
                body.append('\t').append(encodeValue(parameter));
            }
            body.append('\n');
        }
        String content = body.toString();
        return content + "sha256\t" + sha256(content) + '\n';
    }

    public static MagicCircle decode(String document) {
        if (document == null) {
            throw new PersistenceException("document cannot be null");
        }
        if (document.getBytes(StandardCharsets.UTF_8).length > MAX_DOCUMENT_BYTES) {
            throw new PersistenceException("document exceeds 1 MiB limit");
        }
        if (!document.endsWith("\n")) {
            throw new PersistenceException("document must end with a newline");
        }
        int checksumStart = document.lastIndexOf("sha256\t");
        if (checksumStart < 0 || checksumStart == 0) {
            throw new PersistenceException("missing checksum");
        }
        String content = document.substring(0, checksumStart);
        String checksumLine = document.substring(checksumStart).stripTrailing();
        String[] checksumParts = checksumLine.split("\\t", -1);
        if (checksumParts.length != 2 || !checksumParts[1].matches("[0-9a-f]{64}")) {
            throw new PersistenceException("malformed checksum");
        }
        if (!MessageDigest.isEqual(
                sha256(content).getBytes(StandardCharsets.US_ASCII),
                checksumParts[1].getBytes(StandardCharsets.US_ASCII))) {
            throw new PersistenceException("checksum mismatch");
        }

        String[] lines = content.split("\n", -1);
        if (lines.length < 6 || !HEADER.equals(lines[0])) {
            throw new PersistenceException("unsupported or missing circle header");
        }
        try {
            String id = decodeNamedText(lines[1], "id");
            String name = decodeNamedText(lines[2], "name");
            int rings = decodeNamedInt(lines[3], "rings");
            int slots = decodeNamedInt(lines[4], "slots");
            List<PlacedSigil> sigils = new ArrayList<>();
            for (int lineIndex = 5; lineIndex < lines.length - 1; lineIndex++) {
                String[] fields = lines[lineIndex].split("\\t", -1);
                if (fields.length < 5 || !fields[0].equals("sigil")) {
                    throw new PersistenceException("malformed sigil at line " + (lineIndex + 1));
                }
                int ring = parseInt(fields[1], "ring");
                int slot = parseInt(fields[2], "slot");
                String type = plainText(fields[3], "sigil type");
                int parameterCount = parseInt(fields[4], "parameter count");
                if (parameterCount < 0 || fields.length != 5 + parameterCount) {
                    throw new PersistenceException("parameter count mismatch at line " + (lineIndex + 1));
                }
                List<CircleValue> parameters = new ArrayList<>();
                for (int parameter = 0; parameter < parameterCount; parameter++) {
                    parameters.add(decodeValue(fields[5 + parameter]));
                }
                sigils.add(new PlacedSigil(new CircleCoordinate(ring, slot), type, parameters));
            }
            return new MagicCircle(MagicCircle.CURRENT_SCHEMA_VERSION, id, name, rings, slots, sigils);
        } catch (PersistenceException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new PersistenceException("invalid circle document: " + e.getMessage(), e);
        }
    }

    private static String encodeValue(CircleValue value) {
        return switch (value) {
            case CircleValue.NumberValue number -> "n:" + text(number.canonicalText());
            case CircleValue.TextValue string -> "s:" + text(string.value());
            case CircleValue.BooleanValue bool -> "b:" + (bool.value() ? "1" : "0");
        };
    }

    private static CircleValue decodeValue(String encoded) {
        if (encoded.startsWith("n:")) {
            CircleValue.NumberValue number = new CircleValue.NumberValue(
                    plainText(encoded.substring(2), "number"));
            if (!number.canonicalText().equals(plainText(encoded.substring(2), "number"))) {
                throw new PersistenceException("number is not in canonical form");
            }
            return number;
        }
        if (encoded.startsWith("s:")) {
            return CircleValue.text(plainText(encoded.substring(2), "text"));
        }
        if (encoded.equals("b:1") || encoded.equals("b:0")) {
            return CircleValue.bool(encoded.equals("b:1"));
        }
        throw new PersistenceException("unknown parameter encoding");
    }

    private static String decodeNamedText(String line, String expectedName) {
        String[] parts = line.split("\\t", -1);
        if (parts.length != 2 || !parts[0].equals(expectedName)) {
            throw new PersistenceException("expected " + expectedName + " field");
        }
        return plainText(parts[1], expectedName);
    }

    private static int decodeNamedInt(String line, String expectedName) {
        String[] parts = line.split("\\t", -1);
        if (parts.length != 2 || !parts[0].equals(expectedName)) {
            throw new PersistenceException("expected " + expectedName + " field");
        }
        return parseInt(parts[1], expectedName);
    }

    private static int parseInt(String value, String name) {
        if (!value.matches("0|[1-9][0-9]*")) {
            throw new PersistenceException(name + " is not a canonical non-negative integer");
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new PersistenceException(name + " is outside the supported integer range", e);
        }
    }

    private static String text(String value) {
        return ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String plainText(String value, String name) {
        try {
            byte[] decoded = DECODER.decode(value);
            String roundTrip = ENCODER.encodeToString(decoded);
            if (!roundTrip.equals(value)) {
                throw new PersistenceException(name + " is not canonical base64url");
            }
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new PersistenceException(name + " is not valid base64url", e);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by Java", e);
        }
    }

    public static final class PersistenceException extends IllegalArgumentException {
        public PersistenceException(String message) {
            super(message);
        }

        public PersistenceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
