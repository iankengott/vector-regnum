package vectorregnum.core.circle;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** Immutable persisted lifecycle for portable and world-installed spell media. */
public record SpellArtifact(
        int schemaVersion,
        String id,
        SpellMedium medium,
        MagicCircle circle,
        State state,
        WorldAnchor anchor,
        long successfulActivations) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    public enum State { READY, CONSUMED, INSTALLED }
    public enum ActivationStatus { ACTIVATED, REJECTED }

    public SpellArtifact {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported artifact schema version " + schemaVersion);
        }
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(medium, "medium");
        Objects.requireNonNull(circle, "circle");
        Objects.requireNonNull(state, "state");
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("artifact id must match " + ID.pattern());
        }
        if (successfulActivations < 0) {
            throw new IllegalArgumentException("successfulActivations must be non-negative");
        }
        if (medium.installationRequired()) {
            if ((state == State.INSTALLED) != (anchor != null)) {
                throw new IllegalArgumentException("installed media requires exactly one world anchor");
            }
            if (state == State.CONSUMED) {
                throw new IllegalArgumentException("installed media cannot be consumed");
            }
        } else if (anchor != null || state == State.INSTALLED) {
            throw new IllegalArgumentException("only tablets can be installed");
        }
        if (medium == SpellMedium.BOOK && state == State.CONSUMED) {
            throw new IllegalArgumentException("books cannot be consumed");
        }
        if (medium == SpellMedium.SCROLL && state == State.CONSUMED && successfulActivations != 1) {
            throw new IllegalArgumentException("a consumed scroll has exactly one successful activation");
        }
        if (medium == SpellMedium.SCROLL && successfulActivations > 1) {
            throw new IllegalArgumentException("a scroll cannot activate more than once");
        }
    }

    public static SpellArtifact scroll(String id, MagicCircle circle) {
        return new SpellArtifact(CURRENT_SCHEMA_VERSION, id, SpellMedium.SCROLL,
                circle, State.READY, null, 0);
    }

    public static SpellArtifact book(String id, MagicCircle circle) {
        return new SpellArtifact(CURRENT_SCHEMA_VERSION, id, SpellMedium.BOOK,
                circle, State.READY, null, 0);
    }

    public static SpellArtifact tablet(String id, MagicCircle circle) {
        return new SpellArtifact(CURRENT_SCHEMA_VERSION, id, SpellMedium.TABLET,
                circle, State.READY, null, 0);
    }

    public static SpellArtifact engraving(String id, MagicCircle circle) {
        return new SpellArtifact(CURRENT_SCHEMA_VERSION, id, SpellMedium.ENGRAVING,
                circle, State.READY, null, 0);
    }

    public Transition install(WorldAnchor worldAnchor) {
        if (!medium.installationRequired()) {
            return rejected("Only world media can be installed");
        }
        if (state == State.INSTALLED) {
            return rejected("This tablet is already permanently installed");
        }
        SpellArtifact installed = new SpellArtifact(schemaVersion, id, medium, circle,
                State.INSTALLED, Objects.requireNonNull(worldAnchor, "worldAnchor"), successfulActivations);
        return new Transition(ActivationStatus.ACTIVATED, installed, "Tablet installed");
    }

    /** Apply only after the spell engine reports a successful cast. */
    public Transition recordSuccessfulActivation() {
        if (state == State.CONSUMED) {
            return rejected("This scroll has already been consumed");
        }
        if (medium.installationRequired() && state != State.INSTALLED) {
            return rejected("Install this medium before activating it");
        }
        State nextState = medium == SpellMedium.SCROLL ? State.CONSUMED : state;
        SpellArtifact next = new SpellArtifact(schemaVersion, id, medium, circle,
                nextState, anchor, Math.addExact(successfulActivations, 1));
        return new Transition(ActivationStatus.ACTIVATED, next,
                medium == SpellMedium.SCROLL ? "Scroll consumed" : "Spell activated");
    }

    public boolean canBeRemovedFromWorld() {
        return !medium.permanentInstallation() || state != State.INSTALLED;
    }

    public Optional<WorldAnchor> installedAt() {
        return Optional.ofNullable(anchor);
    }

    private Transition rejected(String message) {
        return new Transition(ActivationStatus.REJECTED, this, message);
    }

    public record Transition(ActivationStatus status, SpellArtifact artifact, String message) {
        public Transition {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(artifact, "artifact");
            Objects.requireNonNull(message, "message");
        }

        public boolean accepted() {
            return status == ActivationStatus.ACTIVATED;
        }
    }

    public record WorldAnchor(String dimension, int x, int y, int z) {
        public WorldAnchor {
            Objects.requireNonNull(dimension, "dimension");
            if (dimension.isBlank() || dimension.length() > 128) {
                throw new IllegalArgumentException("dimension must contain 1 to 128 characters");
            }
        }
    }
}
