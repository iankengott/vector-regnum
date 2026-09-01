package vectorregnum.api.v1;

/** Stable v1 outcome vocabulary for authoritative adapter actions. */
public enum ActionResult {
    APPLIED("applied"),
    ALREADY_PRESENT("already_present"),
    UNKNOWN_ID("unknown_id"),
    WRONG_THREAD("wrong_thread"),
    UNAVAILABLE("unavailable"),
    REJECTED("rejected");

    private final String code;

    ActionResult(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
