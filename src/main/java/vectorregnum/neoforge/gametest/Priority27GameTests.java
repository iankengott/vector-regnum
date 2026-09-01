package vectorregnum.neoforge.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import vectorregnum.api.v1.ActionResult;
import vectorregnum.api.v1.CastContext;
import vectorregnum.api.v1.CastModifier;
import vectorregnum.api.v1.CastParameters;
import vectorregnum.api.v1.DisruptionRequest;
import vectorregnum.api.v1.DisruptionResult;
import vectorregnum.api.v1.IntegrationRegistry;
import vectorregnum.api.v1.ManaRegionSnapshot;
import vectorregnum.api.v1.PlayerMagicSnapshot;
import vectorregnum.api.v1.StoryEvent;
import vectorregnum.api.v1.VectorRegnumApiV1;
import vectorregnum.core.Element;
import vectorregnum.core.NaturalElementSelector;
import vectorregnum.core.casting.CastCost;
import vectorregnum.core.vm2.Instruction;
import vectorregnum.core.vm2.Program;
import vectorregnum.core.vm2.SourceLocation;
import vectorregnum.neoforge.CastingResourceService;
import vectorregnum.neoforge.ManaData;
import vectorregnum.neoforge.NeoForgeVmService;
import vectorregnum.neoforge.PlayerAttachmentContent;
import vectorregnum.neoforge.api.v1.VectorRegnumApi;
import vectorregnum.neoforge.progression.ManaAffinity;
import vectorregnum.neoforge.progression.ManaCrystalNodeBlock;
import vectorregnum.neoforge.progression.ManaReservoirBlockEntity;
import vectorregnum.neoforge.progression.ProgressionContent;
import vectorregnum.neoforge.progression.ProgressionUnlock;
import vectorregnum.core.casting.ResourceEscrow;

/** Real dedicated-server coverage for the optional priority-27 integration API. */
@GameTestHolder("vector_regnum")
@PrefixGameTestTemplate(false)
public final class Priority27GameTests {
    @GameTest(template = "empty", batch = "priority27_metadata")
    public void metadataAndVersionAreStableOnDedicatedServer(GameTestHelper context) {
        context.assertValueEqual(1, VectorRegnumApiV1.VERSION,
                "the loader-neutral API major version must remain one");
        context.assertTrue(VectorRegnumApiV1.OPTIONAL,
                "the integration API must not require companion mods");
        context.assertValueEqual(List.of("origins", "combat", "progression", "world_story",
                        "administration", "modpack"), VectorRegnumApiV1.domains(),
                "the six integration domains must retain their stable order");
        context.assertValueEqual(8, IntegrationRegistry.MAX_NATURAL_ELEMENT_PROVIDERS,
                "natural-element providers must stay bounded");
        context.assertValueEqual(8, IntegrationRegistry.MAX_CAST_MODIFIER_PROVIDERS,
                "cast modifiers must stay bounded");
        context.assertValueEqual(8, IntegrationRegistry.MAX_STORY_LISTENERS,
                "story listeners must stay bounded");
        context.assertValueEqual(64, ManaRegionSnapshot.MAX_QUERY_RADIUS,
                "mana queries must stay within the 64-block radius");
        context.assertValueEqual(256, ManaRegionSnapshot.MAX_QUERY_ENTRIES,
                "mana queries must stay within the 256-entry bound");
        context.succeed();
    }

    @GameTest(template = "empty", batch = "priority27_snapshot")
    public void playerSnapshotIsAuthoritativeAndKnownUnlockGrantIsStructured(
            GameTestHelper context) {
        ServerPlayer player = player(context);
        try {
            ManaData.setChannelAffinity(player, ManaAffinity.FIRE);
            PlayerMagicSnapshot before = VectorRegnumApi.playerSnapshot(player);
            context.assertValueEqual(player.getUUID(), before.playerId(),
                    "snapshot must carry the authoritative player identity");
            context.assertValueEqual(ManaData.naturalElement(player).id(), before.naturalElement(),
                    "snapshot natural identity must come from the player attachment");
            context.assertValueEqual("fire", before.channel(),
                    "snapshot channel must come from the player attachment");
            context.assertTrue(before.unlockIds().isEmpty(),
                    "a fresh mock player must not start with research unlocks");

            context.assertValueEqual(ActionResult.APPLIED,
                    VectorRegnumApi.grantUnlock(player, ProgressionUnlock.CRYSTAL_HARVEST.id()),
                    "known progression unlocks must be accepted");
            context.assertValueEqual(ActionResult.ALREADY_PRESENT,
                    VectorRegnumApi.grantUnlock(player, "crystal_harvest"),
                    "repeating a known grant must be idempotent");
            context.assertValueEqual(ActionResult.UNKNOWN_ID,
                    VectorRegnumApi.grantUnlock(player, "vector_regnum:not_a_unlock"),
                    "unknown unlock IDs must fail closed");

            PlayerMagicSnapshot after = VectorRegnumApi.playerSnapshot(player);
            context.assertValueEqual(List.of("crystal_harvest"), after.unlockIds(),
                    "snapshot must expose the sorted Vector-Regnum unlock projection");
            boolean immutable = false;
            try {
                after.unlockIds().add("should_fail");
            } catch (UnsupportedOperationException expected) {
                immutable = true;
            }
            context.assertTrue(immutable, "snapshot unlocks must not expose mutable state");
            context.succeed();
        } finally {
            cleanup(context, player);
        }
    }

    @GameTest(template = "empty", batch = "priority27_registry")
    public void naturalProviderOrderingFallbackAndCastModifiersStayBounded(
            GameTestHelper context) {
        ServerPlayer provided = null;
        ServerPlayer fallback = null;
        List<IntegrationRegistry.RegistrationHandle> registrations = new ArrayList<>();
        try {
            registrations.add(VectorRegnumApi.registry().registerNaturalElementProvider(
                    "test:z_origin", ignored -> "fire"));
            registrations.add(VectorRegnumApi.registry().registerNaturalElementProvider(
                    "test:a_origin", ignored -> "frost"));
            provided = player(context);
            context.assertValueEqual(VectorRegnumApi.resolveNaturalElement(provided), Element.ICE,
                    "source-ID order must select the first canonical provider result");
            context.assertValueEqual(Element.ICE.id(),
                    provided.getData(PlayerAttachmentContent.NATURAL_ELEMENT).toLowerCase(),
                    "provider output must persist in canonical form");

            registrations.forEach(IntegrationRegistry.RegistrationHandle::close);
            registrations.clear();
            fallback = player(context);
            context.assertValueEqual(NaturalElementSelector.select(fallback.getUUID()),
                    VectorRegnumApi.resolveNaturalElement(fallback),
                    "no provider must use the deterministic UUID fallback");

            registrations.add(VectorRegnumApi.registry().registerCastModifierProvider(
                    "test:a_modifier", ignored -> new CastModifier(2.0, 2.0, 2.0, 2.0)));
            registrations.add(VectorRegnumApi.registry().registerCastModifierProvider(
                    "test:z_modifier", ignored -> new CastModifier(2.0, 2.0, 2.0, 2.0)));
            CastCost baseline = new CastCost(10.0, 5.0, 4.0, 1.0);
            CastContext castContext = new CastContext(provided.getUUID(), "vector_regnum:test",
                    "fire", "bare", new CastParameters(baseline.mana(), baseline.castingTime(),
                            baseline.upkeep(), baseline.instability()),
                    provided.serverLevel().getGameTime());
            CastCost adjusted = VectorRegnumApi.applyCastModifiers(provided, castContext, baseline);
            context.assertValueEqual(20.0, adjusted.mana(),
                    "registered mana modifiers must affect the central cost path");
            context.assertValueEqual(10.0, adjusted.castingTime(),
                    "registered casting-time modifiers must affect the central cost path");
            context.assertValueEqual(8.0, adjusted.upkeep(),
                    "registered upkeep modifiers must affect the central cost path");
            context.assertValueEqual(2.0, adjusted.instability(),
                    "registered instability modifiers must affect the central cost path");
            context.assertTrue(adjusted.mana() >= CastingResourceService.policy().floors().mana(),
                    "existing server policy floors must remain enforced after modifiers");
            context.succeed();
        } finally {
            registrations.forEach(IntegrationRegistry.RegistrationHandle::close);
            cleanup(context, provided, fallback);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 30, batch = "priority27_combat")
    public void disruptionUsesActiveSpellAndSharedSecurityPolicy(GameTestHelper context) {
        ServerPlayer attacker = player(context);
        ServerPlayer target = player(context);
        AtomicReference<ResourceEscrow.Outcome> terminal = new AtomicReference<>();
        boolean originalPvp = attacker.getServer().isPvpAllowed();
        try {
            attacker.moveTo(0.5, 2.0, 0.5, 0.0F, 0.0F);
            target.moveTo(2.5, 2.0, 0.5, 180.0F, 0.0F);
            Program program = new Program(List.of(
                    Instruction.duration(20, new SourceLocation(0, 1, 1, "PRIORITY27_DURATION")),
                    Instruction.halt(new SourceLocation(1, 1, 2, "PRIORITY27_HALT"))));
            context.assertTrue(NeoForgeVmService.startAuthored(target, program, false,
                            "Priority 27 disruption test", terminal::set),
                    "the target spell must be admitted before a disruption request");
            DisruptionRequest mismatched = new DisruptionRequest(attacker.getUUID(),
                    UUID.randomUUID(), "test:combat", true, true, 0L);
            context.assertValueEqual(DisruptionResult.Code.INVALID_REQUEST,
                    VectorRegnumApi.disrupt(attacker, target, mismatched).code(),
                    "the adapter must recheck attacker/target identity");
            DisruptionRequest request = new DisruptionRequest(attacker.getUUID(), target.getUUID(),
                    "test:combat", true, true, 0L);
            attacker.getServer().setPvpAllowed(false);
            DisruptionResult denied = VectorRegnumApi.disrupt(attacker, target, request);
            context.assertValueEqual(DisruptionResult.Code.PVP_DISABLED, denied.code(),
                    "the shared policy must reject disruption while PvP is disabled");
            attacker.getServer().setPvpAllowed(true);
            DisruptionResult result = VectorRegnumApi.disrupt(attacker, target, request);
            context.assertValueEqual(DisruptionResult.Code.ACCEPTED, result.code(),
                    "a valid active-spell disruption must be accepted");
            context.runAfterDelay(3, () -> {
                context.assertValueEqual(ResourceEscrow.Outcome.GENUINE_SPELL_FAULT, terminal.get(),
                        "accepted disruption must cancel through the authoritative VM service");
                context.assertTrue(!NeoForgeVmService.hasActiveSpell(target.getUUID()),
                        "the target VM must be removed after the server cancellation tick");
                attacker.getServer().setPvpAllowed(originalPvp);
                cleanup(context, attacker, target);
                context.succeed();
            });
            return;
        } catch (RuntimeException exception) {
            attacker.getServer().setPvpAllowed(originalPvp);
            cleanup(context, attacker, target);
            throw exception;
        }
    }

    @GameTest(template = "empty", batch = "priority27_mana")
    public void manaRegionQueryReadsLoadedEntriesWithoutForceLoading(GameTestHelper context) {
        BlockPos sourcePos = new BlockPos(2, 1, 2);
        BlockPos reservoirPos = sourcePos.east();
        context.setBlock(sourcePos, ProgressionContent.manaCrystalNode().defaultBlockState()
                .setValue(ManaCrystalNodeBlock.CHARGE, 3)
                .setValue(ManaCrystalNodeBlock.AFFINITY, ManaAffinity.FIRE));
        context.setBlock(reservoirPos, ProgressionContent.crystalVial().defaultBlockState()
                .setValue(vectorregnum.neoforge.progression.ManaReservoirBlock.AFFINITY,
                        ManaAffinity.WATER));
        var reservoir = context.getBlockEntity(reservoirPos);
        context.assertTrue(reservoir instanceof ManaReservoirBlockEntity,
                "the loaded reservoir block must expose its real block entity");
        CompoundTag stored = new CompoundTag();
        stored.putInt("stored_mana", 80);
        stored.putString("affinity", "water");
        ((ManaReservoirBlockEntity) reservoir).loadWithComponents(stored,
                context.getLevel().registryAccess());

        ManaRegionSnapshot loaded = VectorRegnumApi.queryManaRegion(context.getLevel(),
                context.absolutePos(new BlockPos(2, 1, 2)), 4);
        context.assertTrue(!loaded.unloaded(),
                "a query contained by the loaded GameTest chunk must be fully loaded");
        context.assertTrue(!loaded.truncated(), "two entries must fit under the query cap");
        context.assertTrue(loaded.entriesExamined() >= 2
                        && loaded.entriesExamined() < ManaRegionSnapshot.MAX_QUERY_ENTRIES,
                "the loaded query must inspect the known sources plus bounded framework entries");
        context.assertValueEqual(300.0, loaded.manaByElement().get("fire"),
                "source charges must be summarized as canonical elemental mana");
        context.assertValueEqual(80.0, loaded.manaByElement().get("water"),
                "reservoir storage must be summarized as canonical elemental mana");

        ManaRegionSnapshot bounded = VectorRegnumApi.queryManaRegion(context.getLevel(),
                context.absolutePos(new BlockPos(2, 1, 2)), ManaRegionSnapshot.MAX_QUERY_RADIUS);
        context.assertTrue(bounded.unloaded(),
                "a larger query must report unloaded surrounding chunks rather than loading them");
        context.assertTrue(bounded.entriesExamined() <= ManaRegionSnapshot.MAX_QUERY_ENTRIES,
                "the loaded-only query must retain its entry bound");

        int placed = 0;
        dense:
        for (int y = 3; y <= 4; y++) {
            for (int z = 0; z < 15; z++) {
                for (int x = 0; x < 15; x++) {
                    context.setBlock(new BlockPos(x, y, z),
                            ProgressionContent.crystalVial().defaultBlockState());
                    if (++placed == ManaRegionSnapshot.MAX_QUERY_ENTRIES + 1) break dense;
                }
            }
        }
        ManaRegionSnapshot denseZeroEntries = VectorRegnumApi.queryManaRegion(context.getLevel(),
                context.absolutePos(new BlockPos(7, 3, 7)), 7);
        context.assertValueEqual(ManaRegionSnapshot.MAX_QUERY_ENTRIES,
                denseZeroEntries.entriesExamined(),
                "zero-mana block entities must still consume the scan budget");
        context.assertTrue(denseZeroEntries.truncated(),
                "the 257th in-range block entity must truncate before inspection");
        context.succeed();
    }

    @GameTest(template = "empty", batch = "priority27_story")
    public void storyEventsAreImmutableAndListenerFailuresAndUnregistrationAreIsolated(
            GameTestHelper context) {
        ServerPlayer actor = player(context);
        ServerPlayer target = player(context);
        List<IntegrationRegistry.RegistrationHandle> registrations = new ArrayList<>();
        AtomicInteger delivered = new AtomicInteger();
        AtomicReference<StoryEvent> observed = new AtomicReference<>();
        try {
            registrations.add(VectorRegnumApi.registry().registerStoryListener(
                    "test:failing", ignored -> { throw new IllegalStateException("isolated listener failure"); }));
            registrations.add(VectorRegnumApi.registry().registerStoryListener(
                    "test:good", event -> {
                        delivered.incrementAndGet();
                        observed.set(event);
                    }));

            StoryEvent identity = VectorRegnumApi.publishIdentityEvent(actor);
            StoryEvent progression = VectorRegnumApi.publishProgressionEvent(actor, "crystal_harvest");
            UUID spellId = UUID.randomUUID();
            StoryEvent start = VectorRegnumApi.publishSpellStartEvent(actor, spellId,
                    "vector_regnum:story_test", Element.FIRE);
            StoryEvent terminal = VectorRegnumApi.publishSpellTerminalEvent(actor, spellId,
                    "vector_regnum:story_test", Element.FIRE, "success");
            StoryEvent disruption = VectorRegnumApi.publishDisruptionEvent(actor, target,
                    "test:combat");
            context.assertValueEqual(start.eventId(), terminal.eventId(),
                    "spell start and terminal events must share a stable event ID");
            context.assertValueEqual(start.revision() + 1L, terminal.revision(),
                    "spell terminal publication must advance the shared revision");
            context.assertTrue(!identity.deliveryKey().equals(progression.deliveryKey()),
                    "identity and progression observations need distinct stable IDs");
            context.assertTrue(!disruption.deliveryKey().equals(start.deliveryKey()),
                    "disruption observations need their own stable ID");
            context.assertTrue(delivered.get() >= 5,
                    "one failing listener must not prevent the healthy listener from receiving events");
            context.assertValueEqual(disruption, observed.get(),
                    "listeners must receive the immutable event value itself");

            IntegrationRegistry.RegistrationHandle good = registrations.removeLast();
            good.close();
            good.close();
            int before = delivered.get();
            VectorRegnumApi.publishIdentityEvent(actor);
            context.assertValueEqual(before, delivered.get(),
                    "closing a listener twice must stop future delivery exactly once");
            context.assertTrue(good.isClosed(), "listener unregistration must be idempotent");
            context.succeed();
        } finally {
            registrations.forEach(IntegrationRegistry.RegistrationHandle::close);
            cleanup(context, actor, target);
        }
    }

    private static ServerPlayer player(GameTestHelper context) {
        return context.makeMockServerPlayerInLevel();
    }

    private static void cleanup(GameTestHelper context, ServerPlayer... players) {
        for (ServerPlayer player : players) {
            if (player != null && context.getLevel().getServer().getPlayerList()
                    .getPlayer(player.getUUID()) != null) {
                context.getLevel().getServer().getPlayerList().remove(player);
            }
        }
    }
}
