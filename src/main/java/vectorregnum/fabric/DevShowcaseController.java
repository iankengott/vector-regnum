package vectorregnum.fabric;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import vectorregnum.core.circle.SpellArtifact;
import vectorregnum.fabric.progression.ProgressionContent;
import vectorregnum.fabric.progression.ProgressionData;
import vectorregnum.fabric.progression.ProgressionSpellLibrary;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Makes Loom visual checks reproducible without shipping automation into release builds. */
public final class DevShowcaseController {
    private static final Map<UUID, Integer> PENDING = new HashMap<>();
    private static final java.util.List<StagedBlock> STAGED_BLOCKS = new java.util.ArrayList<>();

    private DevShowcaseController() {
    }

    public static void initialize() {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment() || !visualCheckRequested()) {
            return;
        }

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                PENDING.put(handler.player.getUuid(), 100));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                PENDING.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (StagedBlock staged : STAGED_BLOCKS) {
                if (staged.world.getBlockState(staged.pos).isOf(ProgressionContent.MANA_CRYSTAL_NODE)) {
                    staged.world.setBlockState(staged.pos, staged.previous);
                }
            }
            STAGED_BLOCKS.clear();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Iterator<Map.Entry<UUID, Integer>> iterator = PENDING.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, Integer> entry = iterator.next();
                int ticks = entry.getValue() - 1;
                if (ticks > 0) {
                    entry.setValue(ticks);
                    continue;
                }
                iterator.remove();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                if (player != null) {
                    runShowcase(player);
                }
            }
        });
    }

    private static boolean visualCheckRequested() {
        return Boolean.getBoolean("vectorregnum.visualCheck")
                || "1".equals(System.getenv("VECTOR_REGNUM_VISUAL_CHECK"));
    }

    private static void runShowcase(ServerPlayerEntity player) {
        ManaData.setForTesting(player, 2_000.0, 2_000.0);
        ProgressionData.unlockAll(player);
        player.getServerWorld().setTimeOfDay(6000L);
        CircleAuthoringService.loadStarter(player);
        var mediaCircle = CircleAuthoringService.session(player).current();
        var compilation = CircleAuthoringService.compile(player);
        if (compilation.hasErrors()) {
            throw new IllegalStateException("development starter circle failed compilation");
        }

        BlockPos sourcePos = player.getBlockPos().offset(player.getHorizontalFacing(), 3);
        if (player.getServerWorld().isAir(sourcePos)) {
            BlockState previous = player.getServerWorld().getBlockState(sourcePos);
            player.getServerWorld().setBlockState(sourcePos,
                    ProgressionContent.MANA_CRYSTAL_NODE.getDefaultState());
            STAGED_BLOCKS.add(new StagedBlock(player.getServerWorld(), sourcePos.toImmutable(), previous));
        }
        giveIfMissing(player, ProgressionContent.MANA_CRYSTAL_SHARD,
                new ItemStack(ProgressionContent.MANA_CRYSTAL_SHARD, 8));
        giveIfMissing(player, SpellMediaContent.SPELL_SCROLL,
                CircleAuthoringService.createArtifactStack(SpellArtifact.scroll("showcase-scroll", mediaCircle)));
        giveIfMissing(player, SpellMediaContent.SPELL_BOOK,
                CircleAuthoringService.createArtifactStack(SpellArtifact.book("showcase-book", mediaCircle)));
        giveIfMissing(player, SpellMediaContent.CARVED_TABLET_ITEM,
                CircleAuthoringService.createArtifactStack(SpellArtifact.tablet("showcase-tablet", mediaCircle)));

        CircleAuthoringService.loadVmStarter(player);
        var circle = CircleAuthoringService.session(player).current();
        var typedCompilation = CircleAuthoringService.compile(player);
        if (typedCompilation.hasErrors()
                || !CircleAuthoringService.activateCircleAt(
                        player, circle, false, player.getEyePos())) {
            throw new IllegalStateException("typed authored circle visual preflight failed");
        }

        var probe = FabricVmService.perceptionProbe(player, 16.0);
        if (probe.status() != vectorregnum.core.vm2.TickResult.Status.HALTED
                || ProgressionSpellLibrary.ALL.size() != 15) {
            throw new IllegalStateException("vm2/library automated visual preflight failed");
        }

        CastService.cast(player, SpellPresets.FIREBOLT, false);
        CastService.cast(player, SpellPresets.FROST_NOVA, false);
        LibrarySpellService.castForShowcase(player, "aegis_shell");
        LibrarySpellService.castForShowcase(player, "featherfall");
        LibrarySpellService.castForShowcase(player, "redstone_oracle");
        player.sendMessage(Text.literal("VECTOR-REGNUM • PRIORITIES 1–10 VISUAL CHECKPOINT")
                .formatted(Formatting.GOLD, Formatting.BOLD), false);
        VectorRegnumMod.LOGGER.info(
                "VISUAL_CHECKPOINT_READY milestone=priorities_1_10 player={} circle_sigils={} "
                        + "library_spells={} vm_status={} vm_cost={} duration_ticks={}",
                player.getGameProfile().getName(),
                circle.sigils().size(),
                ProgressionSpellLibrary.ALL.size(),
                probe.status(),
                probe.cost().total(),
                SpellVisualManager.DEV_SHOWCASE_DURATION_TICKS);
    }

    private static void giveIfMissing(ServerPlayerEntity player, Item item, ItemStack stack) {
        if (!player.getInventory().contains(candidate -> candidate.isOf(item))) {
            player.getInventory().offerOrDrop(stack);
        }
    }

    private record StagedBlock(ServerWorld world, BlockPos pos, BlockState previous) {
    }
}
