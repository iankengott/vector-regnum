package vectorregnum.core.circle;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

/** Versioned item/block-entity payload codec. The embedded circle retains its own checksum. */
public final class SpellArtifactPersistence {
    private static final String HEADER = "vr-artifact\t1";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private SpellArtifactPersistence() {
    }

    public static String encode(SpellArtifact artifact) {
        StringBuilder body = new StringBuilder(HEADER).append('\n')
                .append("id\t").append(text(artifact.id())).append('\n')
                .append("medium\t").append(artifact.medium()).append('\n')
                .append("state\t").append(artifact.state()).append('\n')
                .append("activations\t").append(artifact.successfulActivations()).append('\n');
        if (artifact.anchor() == null) {
            body.append("anchor\t-\n");
        } else {
            body.append("anchor\t").append(text(artifact.anchor().dimension())).append('\t')
                    .append(artifact.anchor().x()).append('\t').append(artifact.anchor().y()).append('\t')
                    .append(artifact.anchor().z()).append('\n');
        }
        body.append("circle\t").append(text(CirclePersistence.encode(artifact.circle()))).append('\n');
        String content = body.toString();
        return content + "sha256\t" + sha256(content) + '\n';
    }

    public static SpellArtifact decode(String document) {
        if (document == null || !document.endsWith("\n")) {
            throw new CirclePersistence.PersistenceException("artifact document must end with a newline");
        }
        int checksumStart = document.lastIndexOf("sha256\t");
        if (checksumStart <= 0) {
            throw new CirclePersistence.PersistenceException("missing artifact checksum");
        }
        String content = document.substring(0, checksumStart);
        String checksum = document.substring(checksumStart + "sha256\t".length()).stripTrailing();
        if (!checksum.matches("[0-9a-f]{64}") || !MessageDigest.isEqual(
                sha256(content).getBytes(StandardCharsets.US_ASCII),
                checksum.getBytes(StandardCharsets.US_ASCII))) {
            throw new CirclePersistence.PersistenceException("artifact checksum mismatch");
        }
        String[] lines = content.split("\n", -1);
        if (lines.length != 8 || !HEADER.equals(lines[0])) {
            throw new CirclePersistence.PersistenceException("unsupported or malformed artifact header");
        }
        try {
            String id = namedText(lines[1], "id");
            SpellMedium medium = SpellMedium.valueOf(namedRaw(lines[2], "medium"));
            SpellArtifact.State state = SpellArtifact.State.valueOf(namedRaw(lines[3], "state"));
            long activations = Long.parseLong(namedRaw(lines[4], "activations"));
            SpellArtifact.WorldAnchor anchor = decodeAnchor(lines[5]);
            MagicCircle circle = CirclePersistence.decode(namedText(lines[6], "circle"));
            return new SpellArtifact(SpellArtifact.CURRENT_SCHEMA_VERSION, id, medium,
                    circle, state, anchor, activations);
        } catch (CirclePersistence.PersistenceException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new CirclePersistence.PersistenceException("invalid artifact document: " + e.getMessage(), e);
        }
    }

    private static SpellArtifact.WorldAnchor decodeAnchor(String line) {
        String[] parts = line.split("\\t", -1);
        if (parts.length == 2 && parts[0].equals("anchor") && parts[1].equals("-")) {
            return null;
        }
        if (parts.length != 5 || !parts[0].equals("anchor")) {
            throw new CirclePersistence.PersistenceException("malformed anchor field");
        }
        return new SpellArtifact.WorldAnchor(plainText(parts[1]),
                Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), Integer.parseInt(parts[4]));
    }

    private static String namedText(String line, String name) {
        return plainText(namedRaw(line, name));
    }

    private static String namedRaw(String line, String name) {
        String[] parts = line.split("\\t", -1);
        if (parts.length != 2 || !parts[0].equals(name)) {
            throw new CirclePersistence.PersistenceException("expected artifact " + name + " field");
        }
        return parts[1];
    }

    private static String text(String value) {
        return ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String plainText(String value) {
        try {
            byte[] bytes = DECODER.decode(value);
            if (!ENCODER.encodeToString(bytes).equals(value)) {
                throw new IllegalArgumentException("non-canonical base64url");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new CirclePersistence.PersistenceException("invalid base64url value", e);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
