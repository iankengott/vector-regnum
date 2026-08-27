package vectorregnum.core.circle;

/** Gameplay contract for persisted spell media. Casting methods live separately. */
public enum SpellMedium {
    /** Portable and consumed by its first successful activation. */
    SCROLL(true, true, false, false),
    /** Portable and reusable without consuming the authored spell. */
    BOOK(true, false, false, false),
    /** A constructed world engraving that may be broken, but is not recovered. */
    ENGRAVING(false, false, true, false),
    /** Reusable only after being permanently installed at a world anchor. */
    TABLET(false, false, true, true);

    private final boolean handheld;
    private final boolean singleUse;
    private final boolean installationRequired;
    private final boolean permanentInstallation;

    SpellMedium(boolean handheld, boolean singleUse, boolean installationRequired,
            boolean permanentInstallation) {
        this.handheld = handheld;
        this.singleUse = singleUse;
        this.installationRequired = installationRequired;
        this.permanentInstallation = permanentInstallation;
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

    public boolean permanentInstallation() {
        return permanentInstallation;
    }
}
