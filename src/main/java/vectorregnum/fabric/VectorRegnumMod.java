package vectorregnum.fabric;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VectorRegnumMod implements ModInitializer {
    public static final String MOD_ID = "vector_regnum";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ManaData.initialize();
        VectorRegnumContent.initialize();
        TutorialGuide.initialize();
        SpellVisualManager.initialize();
        DevShowcaseController.initialize();
        VectorRegnumCommands.initialize();
        LOGGER.info("Vector-Regnum initialized: compatibility spell engine online");
    }
}
