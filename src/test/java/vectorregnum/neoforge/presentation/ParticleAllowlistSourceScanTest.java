package vectorregnum.neoforge.presentation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural allowlist enforcement for priority 20a. Every built-in particle
 * emission must flow through the single guarded choke point, and vanilla
 * particle types may be named only by the guarded renderer and the allowlist
 * itself. Under an active Veil backend the runtime allowlist admits exactly one
 * particle: the enchanting-table cue.
 */
class ParticleAllowlistSourceScanTest {
    private static final Path SOURCE_ROOT = locateSourceRoot();
    /** Only these files may call vanilla particle emission APIs. */
    private static final String EMISSION_CHOKE_POINT =
            "vectorregnum/neoforge/presentation/ClientPresentationRuntime.java";
    /** Only these files may reference concrete vanilla particle types. */
    private static final List<String> TYPE_REFERENCE_FILES = List.of(
            EMISSION_CHOKE_POINT,
            "vectorregnum/neoforge/presentation/VanillaParticleAllowlist.java");

    @Test
    void everyEmissionFlowsThroughTheGuardedChokePoint() throws IOException {
        Pattern emission = Pattern.compile(
                "\\bsendParticles\\s*\\(|\\baddParticle\\s*\\(|ClientboundLevelParticlesPacket");
        List<String> violations = new ArrayList<>();
        forEachMainSource(path -> {
            String relative = relativePath(path);
            int hits = countMatches(emission, read(path));
            if (hits > 0 && !relative.equals(EMISSION_CHOKE_POINT)) {
                violations.add(relative + " emits vanilla particles directly (" + hits + ")");
            }
        });
        assertTrue(violations.isEmpty(), "unguarded particle emissions: " + violations);
    }

    @Test
    void concreteParticleTypesAppearOnlyInSanctionedFiles() throws IOException {
        Pattern typeReference = Pattern.compile("\\bParticleTypes\\s*\\.\\s*[A-Z]");
        List<String> violations = new ArrayList<>();
        forEachMainSource(path -> {
            String relative = relativePath(path);
            if (TYPE_REFERENCE_FILES.contains(relative)) return;
            int hits = countMatches(typeReference, read(path));
            if (hits > 0) violations.add(relative + " names vanilla particle types (" + hits + ")");
        });
        assertTrue(violations.isEmpty(), "unsanctioned ParticleTypes references: " + violations);
    }

    @Test
    void chokePointGuardsEveryEmissionThroughTheAllowlist() throws IOException {
        String source = read(SOURCE_ROOT.resolve(EMISSION_CHOKE_POINT));
        assertTrue(source.contains("VanillaParticleAllowlist.mayEmit(particle)"),
                "the shared add() choke point must consult the runtime allowlist");
        String allowlist = read(SOURCE_ROOT.resolve(
                "vectorregnum/neoforge/presentation/VanillaParticleAllowlist.java"));
        assertTrue(allowlist.contains("particle.getType() == ParticleTypes.ENCHANT"),
                "the sole active-Veil allowlist entry must be the enchanting-table particle");
    }

    private interface SourceConsumer {
        void accept(Path path) throws IOException;
    }

    private static void forEachMainSource(SourceConsumer consumer) throws IOException {
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            for (Path path : files.filter(file -> file.toString().endsWith(".java")).toList()) {
                consumer.accept(path);
            }
        }
    }

    private static String relativePath(Path path) {
        return SOURCE_ROOT.relativize(path).toString().replace('\\', '/');
    }

    private static int countMatches(Pattern pattern, String source) {
        Matcher matcher = pattern.matcher(source);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path);
    }

    private static Path locateSourceRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            Path resolved = candidate.resolve("src/main/java");
            if (Files.isDirectory(resolved)) return resolved;
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("could not locate src/main/java from working directory");
    }
}
