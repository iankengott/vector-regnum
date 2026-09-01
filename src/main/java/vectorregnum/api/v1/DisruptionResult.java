package vectorregnum.api.v1;

import java.util.Objects;

/** Immutable result of a disruption request; it contains no VM handle. */
public record DisruptionResult(Code code, String reason) {
    public DisruptionResult {
        Objects.requireNonNull(code, "code");
        reason = ApiValidation.boundedText(reason, "reason", ApiValidation.MAX_IDENTIFIER_LENGTH);
    }

    public boolean accepted() {
        return code == Code.ACCEPTED;
    }

    public String stableCode() {
        return code.code;
    }

    public enum Code {
        ACCEPTED("accepted"),
        INVALID_REQUEST("invalid_request"),
        REJECTED_POLICY("rejected_policy"),
        ATTACKER_NOT_FOUND("attacker_not_found"),
        TARGET_NOT_FOUND("target_not_found"),
        TARGET_NOT_LOADED("target_not_loaded"),
        WRONG_DIMENSION("wrong_dimension"),
        OUT_OF_RANGE("out_of_range"),
        LINE_OF_SIGHT_BLOCKED("line_of_sight_blocked"),
        CLAIM_BLOCKED("claim_blocked"),
        PVP_DISABLED("pvp_disabled"),
        TEAM_BLOCKED("team_blocked"),
        NO_ACTIVE_SPELL("no_active_spell"),
        TIMING_WINDOW_CLOSED("timing_window_closed"),
        RATE_LIMITED("rate_limited"),
        ENGINE_FAILURE("engine_failure");

        private final String code;

        Code(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
