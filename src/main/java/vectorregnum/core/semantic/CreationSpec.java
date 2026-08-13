package vectorregnum.core.semantic;

import java.util.Objects;
import vectorregnum.core.vm2.Instruction;
import vectorregnum.core.vm2.ManaCostModel;

/** Validated creation/form opcode payload; volume is measured in block-equivalents. */
public record CreationSpec(CreationMaterial material, CreationForm form, double volume,
        int durationTicks, boolean permanent) {
    public CreationSpec {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(form, "form");
        if (!Double.isFinite(volume) || volume <= 0.0 || volume > material.maximumVolume()) {
            throw new IllegalArgumentException("volume for " + material.id() + " must be > 0 and <= "
                    + material.maximumVolume());
        }
        if (!material.forms().contains(form)) {
            throw new IllegalArgumentException(material.id() + " cannot use form " + form);
        }
        if (durationTicks < 1 || durationTicks > Instruction.MAX_DURATION_TICKS) {
            throw new IllegalArgumentException("creation duration must be 1.."
                    + Instruction.MAX_DURATION_TICKS + " ticks");
        }
        if (permanent && !material.permanentAllowed()) {
            throw new IllegalArgumentException(material.id() + " cannot be permanent");
        }
    }

    /** Conservative cost input shared by VM and semantic backends. */
    public ManaCostModel.Input declaredCost() {
        double permanenceMultiplier = permanent ? 4.0 : 1.0;
        return new ManaCostModel.Input(volume * volume * permanenceMultiplier, 0,
                durationTicks, material.rarity() * volume * permanenceMultiplier,
                1, 0, 0);
    }
}
