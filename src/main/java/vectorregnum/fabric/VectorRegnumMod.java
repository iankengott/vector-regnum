package vectorregnum.fabric;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import vectorregnum.fabric.progression.ManaAffinity;
import vectorregnum.fabric.progression.PlayerManaBridge;
import vectorregnum.fabric.progression.ProgressionContent;
import vectorregnum.fabric.progression.ProgressionSync;
import vectorregnum.fabric.world.NaturalCrystalWorldgen;
import vectorregnum.fabric.editor.CircleEditorNetworking;
import vectorregnum.fabric.automation.AutomationService;
import vectorregnum.fabric.ponder.PonderTraceNetworking;
import vectorregnum.fabric.multiplayer.MultiplayerLifecycleService;

public final class VectorRegnumMod implements ModInitializer {
    public static final String MOD_ID = "vector_regnum";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ManaData.initialize();
        ProgressionSync.initialize();
        ProgressionContent.initialize(new PlayerManaBridge() {
            @Override
            public ManaAffinity requestedAffinity(ServerPlayerEntity player) {
                return ManaData.affinity(player);
            }

            @Override
            public boolean tryAcceptExact(ServerPlayerEntity player, int mana,
                    ManaAffinity sourceAffinity, BlockPos source) {
                if (!ManaData.tryCreditExact(player, mana)) {
                    return false;
                }
                ManaData.recordAttunedSource(player, source);
                return true;
            }

            @Override
            public boolean tryAcceptStoredExact(ServerPlayerEntity player, int mana,
                    ManaAffinity sourceAffinity, BlockPos storage) {
                return ManaData.tryCreditExact(player, mana);
            }

            @Override
            public boolean consumeCapacityShard(ServerPlayerEntity player, int capacityIncrease) {
                return ManaData.growCapacity(player, capacityIncrease);
            }
        });
        NaturalCrystalWorldgen.initialize();
        VectorRegnumContent.initialize();
        TemporarySpellContent.initialize();
        SpellMediaContent.initialize();
        CircleAuthoringService.initialize();
        CircleEditorNetworking.initialize();
        PonderTraceNetworking.initialize();
        AutomationService.initialize();
        TutorialGuide.initialize();
        SpellVisualManager.initialize();
        MultiplayerLifecycleService.initialize();
        FabricVmService.initialize();
        LibrarySpellService.initialize();
        DevShowcaseController.initialize();
        VectorRegnumCommands.initialize();
        LOGGER.info("Vector-Regnum initialized: authored circles, vm2, progression, and 15-spell library online");
    }
}
