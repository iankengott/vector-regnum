package vectorregnum.core.semantic;

import java.util.Set;

/** Loader-neutral material classes with explicit creation difficulty and safety envelopes. */
public enum CreationMaterial {
    STONE("stone", 1.0, 64.0, true,
            Set.of(CreationForm.BARRIER, CreationForm.CONSTRUCT, CreationForm.SURFACE, CreationForm.VOLUME)),
    ICE("ice", 1.5, 48.0, false,
            Set.of(CreationForm.PROJECTILE, CreationForm.BARRIER, CreationForm.SURFACE, CreationForm.VOLUME)),
    WATER("water", 1.0, 64.0, false,
            Set.of(CreationForm.PROJECTILE, CreationForm.FIELD, CreationForm.SURFACE, CreationForm.VOLUME)),
    FIRE("fire", 2.0, 16.0, false,
            Set.of(CreationForm.PROJECTILE, CreationForm.FIELD, CreationForm.VOLUME)),
    LIGHT("light", 2.0, 128.0, false,
            Set.of(CreationForm.FIELD, CreationForm.BARRIER, CreationForm.VOLUME)),
    ARCANE_FORCE("arcane_force", 4.0, 32.0, false, Set.of(CreationForm.values()));

    private final String id;
    private final double rarity;
    private final double maximumVolume;
    private final boolean permanentAllowed;
    private final Set<CreationForm> forms;

    CreationMaterial(String id, double rarity, double maximumVolume,
            boolean permanentAllowed, Set<CreationForm> forms) {
        this.id = id;
        this.rarity = rarity;
        this.maximumVolume = maximumVolume;
        this.permanentAllowed = permanentAllowed;
        this.forms = Set.copyOf(forms);
    }

    public String id() { return id; }
    public double rarity() { return rarity; }
    public double maximumVolume() { return maximumVolume; }
    public boolean permanentAllowed() { return permanentAllowed; }
    public Set<CreationForm> forms() { return forms; }
}
