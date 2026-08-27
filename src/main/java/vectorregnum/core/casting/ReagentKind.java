package vectorregnum.core.casting;

/** Stable, loader-neutral reagent contribution dimensions. */
public enum ReagentKind {
    MANA("mana"),
    CASTING_TIME("casting_time"),
    UPKEEP("upkeep"),
    INSTABILITY("instability");

    private final String stableId;

    ReagentKind(String stableId) {
        this.stableId = stableId;
    }

    /** The persisted/configuration identifier; never use {@link #name()} for storage. */
    public String stableId() {
        return stableId;
    }

    /** Short alias for serializers and adapter code. */
    public String id() {
        return stableId;
    }
}
