package vectorregnum.neoforge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import vectorregnum.neoforge.automation.AutomationContent;
import vectorregnum.neoforge.progression.ProgressionContent;

/** NeoForge entrypoint; registration is attached to the real mod event bus. */
@Mod(VectorRegnumMod.MOD_ID)
public final class VectorRegnumMod {
    public static final String MOD_ID = "vector_regnum";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public VectorRegnumMod(IEventBus modBus) {
        VectorRegnumContent.register(modBus);
        SpellMediaContent.register(modBus);
        TemporarySpellContent.register(modBus);
        ProgressionContent.register(modBus);
        AutomationContent.register(modBus);
    }
}
