package vectorregnum.neoforge.guide;

import java.util.Comparator;
import java.util.List;

/**
 * Checked-in result of the Fabric 1.21.1 guide-backend prototype comparison.
 * The detailed evidence and score rationale live in
 * {@code docs/FIELD_MANUAL_BACKEND_DECISION.md}.
 */
public final class GuideBackendDecision {
    private GuideBackendDecision() { }

    public static final String SELECTED_BACKEND = "native";

    public static List<Candidate> candidates() {
        return List.of(
                new Candidate("native", true, 5, 5, 5, 4, 3, 5),
                new Candidate("patchouli", true, 5, 3, 3, 5, 5, 4),
                new Candidate("lavender", true, 3, 4, 4, 4, 4, 4),
                new Candidate("modonomicon", true, 5, 4, 4, 4, 4, 3),
                new Candidate("guideme", false, 5, 5, 4, 5, 5, 2));
    }

    public static Candidate selected() {
        return candidates().stream().filter(Candidate::fabric1211)
                .max(Comparator.comparingInt(Candidate::score)
                        .thenComparing(Candidate::id))
                .orElseThrow();
    }

    /** Higher scores are better; a missing Fabric 1.21.1 build is disqualifying. */
    public record Candidate(String id, boolean fabric1211, int dependencyStability,
            int extensibility, int visualIdentity, int accessibility,
            int authoringEfficiency, int compatibility) {
        public Candidate {
            GuidePage.requireId(id);
            for (int score : List.of(dependencyStability, extensibility, visualIdentity,
                    accessibility, authoringEfficiency, compatibility)) {
                if (score < 1 || score > 5) {
                    throw new IllegalArgumentException("backend scores must be 1..5");
                }
            }
        }

        public int score() {
            return dependencyStability + extensibility + visualIdentity + accessibility
                    + authoringEfficiency + compatibility;
        }
    }
}
