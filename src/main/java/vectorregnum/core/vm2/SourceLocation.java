package vectorregnum.core.vm2;

import java.util.Objects;

/** Exact authored location retained through compilation and runtime faults. */
public record SourceLocation(int sourceIndex, int line, int column, String sigilId) {
    public SourceLocation {
        if (sourceIndex < 0 || line < 1 || column < 1) {
            throw new IllegalArgumentException("sourceIndex must be >= 0 and line/column >= 1");
        }
        Objects.requireNonNull(sigilId, "sigilId");
        if (sigilId.isBlank()) {
            throw new IllegalArgumentException("sigilId cannot be blank");
        }
    }

    public static SourceLocation at(int sourceIndex, String sigilId) {
        return new SourceLocation(sourceIndex, 1, sourceIndex + 1, sigilId);
    }
}
