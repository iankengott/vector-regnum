package vectorregnum.fabric.progression;

public enum ProgressionUnlock {
    CRYSTAL_HARVEST("crystal_harvest"),
    MANA_STORAGE("mana_storage"),
    COMBAT_WEAVING("combat_weaving"),
    DEFENSIVE_WEAVING("defensive_weaving"),
    MOVEMENT_WEAVING("movement_weaving"),
    PERCEPTION_WEAVING("perception_weaving"),
    AUTOMATION_WEAVING("automation_weaving");

    private final String id;

    ProgressionUnlock(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static ProgressionUnlock byId(String id) {
        for (ProgressionUnlock unlock : values()) {
            if (unlock.id.equals(id)) {
                return unlock;
            }
        }
        throw new IllegalArgumentException("Unknown progression unlock: " + id);
    }
}
