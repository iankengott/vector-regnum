package vectorregnum.neoforge.gametest;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import vectorregnum.core.casting.CastCost;
import vectorregnum.core.casting.CastingMethod;
import vectorregnum.core.casting.ReagentKind;
import vectorregnum.core.casting.ResourceEscrow;
import vectorregnum.neoforge.CastingResourceService;
import vectorregnum.neoforge.ManaData;
import vectorregnum.neoforge.NeoForgeVmService;
import vectorregnum.neoforge.SpellMediaContent;

/** Real inventory, attachment, mana, scroll, and settlement coverage for priority 22. */
@GameTestHolder("vector_regnum")
@PrefixGameTestTemplate(false)
public final class Priority22GameTests {
    @GameTest(template = "empty")
    public void escrowRefundsInfrastructureFailureAndConsumesGenuineFault(GameTestHelper context) {
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        ManaData.setForTesting(player, 100.0, 100.0);
        player.getInventory().placeItemBackInInventory(new ItemStack(Items.AMETHYST_SHARD, 2));
        context.assertTrue(CastingResourceService.stage(player, ReagentKind.MANA, 1),
                "one real amethyst shard should stage as a mana reagent");

        CastCost baseline = new CastCost(20.0, 20.0, 1.0, 1.0);
        var refundable = CastingResourceService.begin(player, CastingMethod.BARE,
                baseline, true, true, ItemStack.EMPTY).orElseThrow();
        context.assertValueEqual(85.0, ManaData.available(player),
                "the bounded 5-mana reagent discount should reserve 15 mana");
        context.assertValueEqual(1, player.getInventory().countItem(Items.AMETHYST_SHARD),
                "staged reagent should remain outside inventory while escrow is active");
        CastingResourceService.settle(player, refundable, ResourceEscrow.Outcome.ENGINE_FAILURE);
        context.assertValueEqual(100.0, ManaData.available(player),
                "an internal engine failure must refund exact reserved mana");
        context.assertValueEqual(2, player.getInventory().countItem(Items.AMETHYST_SHARD),
                "an internal engine failure must refund exact reagent items");

        context.assertTrue(CastingResourceService.stage(player, ReagentKind.MANA, 1),
                "reagent should be stageable again after refund");
        ItemStack scroll = new ItemStack(SpellMediaContent.spellScroll());
        var committed = CastingResourceService.begin(player, CastingMethod.SCROLL,
                baseline, true, true, scroll).orElseThrow();
        context.assertTrue(scroll.isEmpty(), "the physical scroll must enter escrow before execution");
        CastingResourceService.settle(player, committed,
                ResourceEscrow.Outcome.GENUINE_SPELL_FAULT);
        context.assertValueEqual(85.0, ManaData.available(player),
                "a genuine spell fault must consume the committed mana");
        context.assertValueEqual(1, player.getInventory().countItem(Items.AMETHYST_SHARD),
                "a genuine spell fault must consume the committed reagent");
        context.assertValueEqual(0, CastingResourceService.activeCount(player.getUUID()),
                "terminal settlement must leave no active escrow");

        context.getLevel().getServer().getPlayerList().remove(player);
        context.succeed();
    }

    @GameTest(template = "empty")
    public void ritualOfferingIsSeparateAndAllReusableMethodsReserve(GameTestHelper context) {
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        ManaData.setForTesting(player, 100.0, 100.0);
        player.getInventory().placeItemBackInInventory(new ItemStack(Items.QUARTZ, 2));
        player.getInventory().placeItemBackInInventory(new ItemStack(Items.AMETHYST_SHARD, 1));
        context.assertTrue(CastingResourceService.stageOffering(player, 1),
                "quartz should stage as an offering-only ritual resource");
        context.assertTrue(CastingResourceService.stage(player, ReagentKind.MANA, 1),
                "amethyst should remain a typed mana discount reagent");

        CastCost baseline = new CastCost(20.0, 60.0, 1.0, 1.0);
        var ritual = CastingResourceService.begin(player, CastingMethod.RITUAL,
                baseline, true, true, ItemStack.EMPTY).orElseThrow();
        context.assertValueEqual(1, ritual.quote().loadout().offeringUnits(),
                "ritual quote should commit exactly the separately staged offering");
        context.assertValueEqual(1, ritual.quote().loadout().units(ReagentKind.MANA),
                "offering must not replace or duplicate the typed reagent discount");
        CastingResourceService.settle(ritual, ResourceEscrow.Outcome.ENGINE_FAILURE);
        context.assertValueEqual(2, player.getInventory().countItem(Items.QUARTZ),
                "refundable failure should return the exact ritual offering");
        context.assertValueEqual(1, player.getInventory().countItem(Items.AMETHYST_SHARD),
                "refundable failure should return the exact typed reagent");

        for (CastingMethod method : new CastingMethod[] {CastingMethod.BARE,
                CastingMethod.ENGRAVING, CastingMethod.SPELLBOOK,
                CastingMethod.INSTALLED_CIRCLE}) {
            var reusable = CastingResourceService.begin(player, method,
                    baseline, true, false, ItemStack.EMPTY).orElseThrow();
            CastingResourceService.settle(reusable, ResourceEscrow.Outcome.ENGINE_FAILURE);
        }
        context.assertValueEqual(100.0, ManaData.available(player),
                "all reusable casting methods should refund exact mana on engine failure");
        context.assertValueEqual(0, CastingResourceService.activeCount(player.getUUID()),
                "all reusable-method reservations should reach a terminal state");
        context.getLevel().getServer().getPlayerList().remove(player);
        context.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60, batch = "priority22_escrow_isolated")
    public void offlineOwnerAndUnloadedTargetRefundExactly(GameTestHelper context) {
        ServerPlayer offline = context.makeMockServerPlayerInLevel();
        ManaData.setForTesting(offline, 100.0, 100.0);
        ItemStack scroll = new ItemStack(SpellMediaContent.spellScroll());
        context.getLevel().getServer().getPlayerList().remove(offline);
        CastingResourceService.begin(offline, CastingMethod.SCROLL,
                new CastCost(20.0, 12.0, 1.0, 1.0), true, false, scroll).orElseThrow();
        context.assertValueEqual(1, CastingResourceService.refundOwner(
                        offline.getUUID(), ResourceEscrow.Outcome.OWNER_LIFECYCLE),
                "owner-UUID settlement must not depend on the live player list");
        context.assertValueEqual(100.0, ManaData.available(offline),
                "offline-owner settlement should refund exact mana");
        context.assertValueEqual(1, offline.getInventory().countItem(SpellMediaContent.spellScroll()),
                "offline-owner settlement should return the reserved scroll");
        context.assertValueEqual(0, CastingResourceService.activeCount(offline.getUUID()),
                "offline settlement must not orphan an active escrow");

        ServerPlayer shutdown = context.makeMockServerPlayerInLevel();
        ManaData.setForTesting(shutdown, 100.0, 100.0);
        context.getLevel().getServer().getPlayerList().remove(shutdown);
        CastingResourceService.begin(shutdown, CastingMethod.BARE,
                new CastCost(20.0, 20.0, 1.0, 1.0), true, false, ItemStack.EMPTY).orElseThrow();
        context.assertValueEqual(1, CastingResourceService.refundAll(
                        context.getLevel().getServer(), ResourceEscrow.Outcome.SHUTDOWN),
                "shutdown fallback must settle reservations for absent owners");
        context.assertValueEqual(100.0, ManaData.available(shutdown),
                "shutdown fallback should refund exact mana to the retained payer");

        ServerPlayer player = context.makeMockServerPlayerInLevel();
        ManaData.setForTesting(player, 100.0, 100.0);
        AtomicReference<ResourceEscrow.Outcome> terminal = new AtomicReference<>();
        context.assertTrue(NeoForgeVmService.startAuthored(player,
                        NeoForgeVmService.impulseProgram(java.util.UUID.randomUUID().toString(),
                                new vectorregnum.core.vm2.Vector3(1, 0, 0), 0, 1),
                        true, "Unloaded target escrow test", terminal::set),
                "a bounded missing-entity program should be admitted before execution");
        context.runAfterDelay(40, () -> {
            context.assertValueEqual(ResourceEscrow.Outcome.UNLOADED_TARGET, terminal.get(),
                    "missing execution target must produce the explicit refundable outcome");
            context.assertValueEqual(100.0, ManaData.available(player),
                    "unloaded-target execution must refund exact reserved mana");
            context.assertValueEqual(0, CastingResourceService.activeCount(player.getUUID()),
                    "unloaded-target settlement must close escrow");
            context.getLevel().getServer().getPlayerList().remove(player);
            context.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public void duplicateLifecycleCancellationNotifiesTerminalOnce(GameTestHelper context) {
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        ManaData.setForTesting(player, 100.0, 100.0);
        AtomicInteger callbacks = new AtomicInteger();
        context.assertTrue(NeoForgeVmService.startAuthored(player,
                        NeoForgeVmService.impulseProgram(player.getStringUUID(),
                                new vectorregnum.core.vm2.Vector3(1, 0, 0), 40, 1),
                        true, "Lifecycle idempotency test", ignored -> callbacks.incrementAndGet()),
                "delayed cast should reserve before lifecycle cancellation");
        NeoForgeVmService.cancelOwner(player.getUUID(), "first cancellation");
        NeoForgeVmService.cancelOwner(player.getUUID(), "duplicate cancellation");
        context.runAfterDelay(2, () -> {
            context.assertValueEqual(1, callbacks.get(),
                    "duplicate lifecycle signals must invoke the terminal callback once");
            context.assertValueEqual(100.0, ManaData.available(player),
                    "lifecycle cancellation should refund exact mana");
            context.assertValueEqual(0, CastingResourceService.activeCount(player.getUUID()),
                    "lifecycle cancellation must close escrow exactly once");
            context.getLevel().getServer().getPlayerList().remove(player);
            context.succeed();
        });
    }
}
