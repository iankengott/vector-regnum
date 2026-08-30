package vectorregnum.core.vm2;

import java.util.Objects;

/** Immutable authoritative signal/output emitted by one VM tick. */
public sealed interface VmMessage permits VmMessage.Signal, VmMessage.Output {
    long sequence();
    long tick();
    int branchId();
    Vector3 point();
    double declaredRange();

    record Signal(long sequence, long tick, int branchId, String channel,
            Vector3 point, RuntimeValue payload, double declaredRange) implements VmMessage {
        public Signal {
            validate(sequence, tick, branchId, point, declaredRange);
            channel = AdvancedOperand.checkedName(channel);
            Objects.requireNonNull(payload, "payload");
        }
    }

    record Output(long sequence, long tick, int branchId, Vector3 point,
            String text, double declaredRange) implements VmMessage {
        public Output {
            validate(sequence, tick, branchId, point, declaredRange);
            Objects.requireNonNull(text, "text");
            if (text.isBlank() || text.length() > RuntimeValue.MAX_TEXT_CHARS) {
                throw new IllegalArgumentException("output must be 1.."
                        + RuntimeValue.MAX_TEXT_CHARS + " characters");
            }
        }
    }

    private static void validate(long sequence, long tick, int branchId,
            Vector3 point, double declaredRange) {
        if (sequence < 0 || tick < 0 || branchId < 0
                || !Double.isFinite(declaredRange) || declaredRange <= 0) {
            throw new IllegalArgumentException("invalid message metadata");
        }
        Objects.requireNonNull(point, "point");
    }
}
