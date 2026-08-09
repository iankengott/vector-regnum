package vectorregnum.core.circle;

/** Gameplay contract for the three persisted spell media. */
public enum SpellMedium {
    /** Portable and consumed by its first successful activation. */
    SCROLL(true, true, false),
    /** Portable and reusable without consuming the authored spell. */
    BOOK(true, false, false),
    /** Reusable only after being permanently installed at a world anchor. */
    TABLET(false, false, true);

    private final boolean handheld;
    private final boolean singleUse;
    private final boolean installationRequired;

    SpellMedium(boolean handheld, boolean singleUse, boolean installationRequired) {
        this.handheld = handheld;
        this.singleUse = singleUse;
        this.installationRequired = installationRequired;
    }

    public boolean handheld() {
        return handheld;
    }

    public boolean singleUse() {
        return singleUse;
    }

    public boolean installationRequired() {
        return installationRequired;
    }
}
