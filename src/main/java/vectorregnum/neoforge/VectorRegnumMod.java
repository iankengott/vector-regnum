package vectorregnum.neoforge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import vectorregnum.neoforge.automation.AutomationContent;
import vectorregnum.neoforge.automation.AutomationService;
import vectorregnum.neoforge.multiplayer.MultiplayerLifecycleService;
import vectorregnum.neoforge.multiplayer.SpellSecurityPolicy;
import vectorregnum.neoforge.progression.ManaAffinity;
import vectorregnum.neoforge.progression.PlayerManaBridge;
import vectorregnum.neoforge.progression.ProgressionContent;
import vectorregnum.neoforge.world.NaturalCrystalWorldgen;

/** NeoForge entrypoint; registration is attached to the real mod event bus. */
@Mod(VectorRegnumMod.MOD_ID)
public final class VectorRegnumMod {
    public static final String MOD_ID = "vector_regnum";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public VectorRegnumMod(IEventBus modBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, CastingConfig.SPEC,
                "vector-regnum-casting.toml");
        VectorRegnumContent.register(modBus);
        SpellMediaContent.register(modBus);
        TemporarySpellContent.register(modBus);
        ProgressionContent.register(modBus);
        AutomationContent.register(modBus);
        PlayerAttachmentContent.register(modBus);
        NaturalCrystalWorldgen.register(modBus);
        modBus.addListener(NeoForgeNetworking::register);

        ProgressionContent.initialize(new PlayerManaBridge() {
            @Override
            public ManaAffinity requestedAffinity(net.minecraft.server.level.ServerPlayer player) {
                return ManaData.channelAffinity(player);
            }

            @Override
            public boolean tryAcceptExact(net.minecraft.server.level.ServerPlayer player, int mana,
                    ManaAffinity sourceAffinity, net.minecraft.core.BlockPos source) {
                if (!ManaData.tryCreditExact(player, mana)) return false;
                ManaData.recordAttunedSource(player, source);
                return true;
            }

            @Override
            public boolean tryAcceptStoredExact(net.minecraft.server.level.ServerPlayer player,
                    int mana, ManaAffinity sourceAffinity, net.minecraft.core.BlockPos storage) {
                return ManaData.tryCreditExact(player, mana);
            }

            @Override
            public boolean consumeCapacityShard(net.minecraft.server.level.ServerPlayer player,
                    int capacityIncrease) {
                return ManaData.growCapacity(player, capacityIncrease);
            }
        });

        NeoForge.EVENT_BUS.register(MultiplayerLifecycleService.class);
        NeoForge.EVENT_BUS.register(NeoForgeVmService.class);
        NeoForge.EVENT_BUS.register(SpellVisualManager.class);
        NeoForge.EVENT_BUS.register(AutomationService.class);
        NeoForge.EVENT_BUS.register(SpellSecurityPolicy.class);
        NeoForge.EVENT_BUS.register(VectorRegnumGameplayEvents.class);
        NeoForge.EVENT_BUS.addListener(VectorRegnumCommands::register);

        CircleAuthoringService.initialize();
        TutorialGuide.initialize();
        NeoForgeVmService.initialize();
        SpellVisualManager.initialize();
        AutomationService.initialize();
        LibrarySpellService.initialize();
        DevShowcaseController.initialize();
        LOGGER.info("Vector-Regnum NeoForge initialized: authored circles, vm2, progression, and 15-spell library online");
    }
}
