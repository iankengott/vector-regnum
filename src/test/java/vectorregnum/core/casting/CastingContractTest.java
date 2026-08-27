package vectorregnum.core.casting;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CastingContractTest {
    private static final CastingPolicy POLICY = CastingPolicy.canonical();

    @Test
    void methodsHaveDistinctPortableConsumptionAndInstallationContracts() {
        assertArrayEquals(new CastingMethod[] {
                CastingMethod.BARE,
                CastingMethod.RITUAL,
                CastingMethod.ENGRAVING,
                CastingMethod.SPELLBOOK,
                CastingMethod.SCROLL,
                CastingMethod.INSTALLED_CIRCLE
        }, CastingMethod.values());

        assertFalse(CastingMethod.BARE.offeringRequired());
        assertFalse(CastingMethod.BARE.portable());
        assertFalse(CastingMethod.BARE.singleUse());
        assertFalse(CastingMethod.BARE.installationRequired());

        assertTrue(CastingMethod.RITUAL.requiresOffering());
        assertFalse(CastingMethod.RITUAL.isPortable());
        assertFalse(CastingMethod.RITUAL.singleUse());
        assertFalse(CastingMethod.RITUAL.requiresInstallation());

        assertTrue(CastingMethod.ENGRAVING.requiresInstallation());
        assertFalse(CastingMethod.ENGRAVING.offeringRequired());
        assertFalse(CastingMethod.ENGRAVING.portable());
        assertFalse(CastingMethod.ENGRAVING.singleUse());

        assertTrue(CastingMethod.SPELLBOOK.portable());
        assertFalse(CastingMethod.SPELLBOOK.singleUse());
        assertFalse(CastingMethod.SPELLBOOK.installationRequired());

        assertTrue(CastingMethod.SCROLL.portable());
        assertTrue(CastingMethod.SCROLL.singleUse());
        assertFalse(CastingMethod.SCROLL.installationRequired());

        assertTrue(CastingMethod.INSTALLED_CIRCLE.installationRequired());
        assertFalse(CastingMethod.INSTALLED_CIRCLE.portable());
        assertFalse(CastingMethod.INSTALLED_CIRCLE.singleUse());
    }

    @Test
    void reagentKindsExposeStableIds() {
        assertEquals(Set.of("mana", "casting_time", "upkeep", "instability"),
                Stream.of(ReagentKind.values()).map(ReagentKind::stableId).collect(java.util.stream.Collectors.toSet()));
        assertEquals("mana", ReagentKind.MANA.id());
        assertEquals("casting_time", ReagentKind.CASTING_TIME.id());
        assertEquals("upkeep", ReagentKind.UPKEEP.id());
        assertEquals("instability", ReagentKind.INSTABILITY.id());
    }

    @Test
    void loadoutIsImmutableAndEnforcesAllConfiguredCaps() {
        CastingPolicy caps = policy(CastCost.ZERO, new CastCost(100, 100, 100, 100),
                2, 3, 4, Map.of());
        EnumMap<ReagentKind, Integer> source = new EnumMap<>(ReagentKind.class);
        source.put(ReagentKind.MANA, 2);
        ReagentLoadout loadout = ReagentLoadout.of(source, 3, caps);
        source.put(ReagentKind.MANA, 1);
        assertEquals(2, loadout.units(ReagentKind.MANA));
        assertEquals(2, loadout.totalUnits());
        assertEquals(3, loadout.offeringUnits());
        assertThrows(UnsupportedOperationException.class,
                () -> loadout.units().put(ReagentKind.UPKEEP, 1));

        assertThrows(IllegalArgumentException.class,
                () -> ReagentLoadout.of(Map.of(ReagentKind.MANA, 3), caps));
        assertThrows(IllegalArgumentException.class,
                () -> ReagentLoadout.of(Map.of(ReagentKind.MANA, 2, ReagentKind.UPKEEP, 2), caps));
        assertThrows(IllegalArgumentException.class,
                () -> ReagentLoadout.of(Map.of(ReagentKind.MANA, 1), 5, caps));
        assertThrows(IllegalArgumentException.class,
                () -> ReagentLoadout.of(Map.of(ReagentKind.MANA, -1), caps));
    }

    @Test
    void ritualRequiresAnOfferingButOtherMethodsCanQuoteWithoutOne() {
        CastCost baseline = new CastCost(20, 5, 2, .5);
        assertThrows(IllegalArgumentException.class,
                () -> POLICY.quote(CastingMethod.RITUAL, baseline, ReagentLoadout.empty()));

        ReagentLoadout offering = ReagentLoadout.empty().withOfferingUnits(2, POLICY);
        CastQuote ritual = POLICY.quote(CastingMethod.RITUAL, baseline, offering);
        assertEquals(CastingMethod.RITUAL, ritual.method());
        assertEquals(2, ritual.loadout().offeringUnits());

        CastQuote bare = POLICY.quote(CastingMethod.BARE, baseline);
        assertEquals(0, bare.loadout().offeringUnits());
        CastQuote installed = POLICY.quote(CastingMethod.INSTALLED_CIRCLE, baseline);
        assertTrue(installed.method().requiresInstallation());
    }

    @Test
    void quoteRecordsBaselineEveryContributionAndFinalBoundedValues() {
        CastCost baseline = new CastCost(50, 30, 10, 1);
        ReagentLoadout loadout = ReagentLoadout.of(Map.of(
                ReagentKind.MANA, 2,
                ReagentKind.CASTING_TIME, 1,
                ReagentKind.UPKEEP, 3,
                ReagentKind.INSTABILITY, 4), POLICY);
        CastQuote quote = POLICY.quote(CastingMethod.SCROLL, baseline, loadout);

        assertEquals(baseline, quote.undiscounted());
        assertEquals(baseline, quote.baseline());
        assertEquals(4, quote.contributions().size());
        assertEquals(List.of(ReagentKind.MANA, ReagentKind.CASTING_TIME,
                ReagentKind.UPKEEP, ReagentKind.INSTABILITY),
                quote.contributions().stream().map(ReagentContribution::kind).toList());
        assertEquals(new CastCost(10, 0, 0, 0), quote.contributions().get(0).appliedDiscount());
        assertEquals(new CastCost(0, 2, 0, 0), quote.contributions().get(1).discount());
        assertEquals(new CastCost(0, 0, 3, 0), quote.contributions().get(2).appliedDiscount());
        assertEquals(new CastCost(0, 0, 0, .4), quote.contributions().get(3).appliedDiscount());
        assertEquals(new CastCost(40, 28, 7, .6), quote.finalCost());
        assertEquals(new CastCost(10, 2, 3, .4), quote.totalAppliedDiscount());
        assertTrue(quote.hasDiscount());
        assertThrows(UnsupportedOperationException.class,
                () -> quote.contributions().clear());
    }

    @Test
    void floorsPreventFreeOrPerfectDiscountsAndContributionShowsClipping() {
        CastingPolicy floors = policy(new CastCost(10, 8, 4, .2),
                new CastCost(1_000, 1_000, 1_000, 1_000), 4, 16, 4,
                Map.of(
                        ReagentKind.MANA, new CastCost(100, 0, 0, 0),
                        ReagentKind.CASTING_TIME, new CastCost(0, 100, 0, 0),
                        ReagentKind.UPKEEP, new CastCost(0, 0, 100, 0),
                        ReagentKind.INSTABILITY, new CastCost(0, 0, 0, 100)));
        ReagentLoadout loadout = ReagentLoadout.of(Map.of(
                ReagentKind.MANA, 1,
                ReagentKind.CASTING_TIME, 1,
                ReagentKind.UPKEEP, 1,
                ReagentKind.INSTABILITY, 1), floors);
        CastQuote quote = floors.quote(CastingMethod.BARE,
                new CastCost(25, 15, 7, .5), loadout);

        assertEquals(new CastCost(10, 8, 4, .2), quote.finalCost());
        assertEquals(new CastCost(15, 0, 0, 0), quote.contributions().get(0).appliedDiscount());
        assertEquals(new CastCost(0, 7, 0, 0), quote.contributions().get(1).appliedDiscount());
        assertEquals(new CastCost(0, 0, 3, 0), quote.contributions().get(2).appliedDiscount());
        assertEquals(new CastCost(0, 0, 0, .3), quote.contributions().get(3).appliedDiscount());
        assertTrue(quote.contributions().stream().allMatch(ReagentContribution::wasCapped));
        assertThrows(IllegalArgumentException.class,
                () -> floors.quote(CastingMethod.BARE, new CastCost(9, 15, 7, .5), loadout));
    }

    @Test
    void aggregateDiscountCapsAreAppliedDeterministically() {
        CastingPolicy capped = policy(CastCost.ZERO, new CastCost(6, 5, 3, .2),
                4, 16, 4, Map.of(
                        ReagentKind.MANA, new CastCost(10, 0, 0, 0),
                        ReagentKind.CASTING_TIME, new CastCost(0, 10, 0, 0),
                        ReagentKind.UPKEEP, new CastCost(0, 0, 10, 0),
                        ReagentKind.INSTABILITY, new CastCost(0, 0, 0, 1)));
        ReagentLoadout loadout = ReagentLoadout.of(Map.of(
                ReagentKind.MANA, 2,
                ReagentKind.CASTING_TIME, 2,
                ReagentKind.UPKEEP, 2,
                ReagentKind.INSTABILITY, 2), capped);
        CastQuote quote = capped.quote(CastingMethod.BARE,
                new CastCost(100, 100, 100, 10), loadout);

        assertEquals(new CastCost(94, 95, 97, 9.8), quote.finalCost());
        assertEquals(new CastCost(6, 0, 0, 0), quote.contributions().get(0).appliedDiscount());
        assertEquals(new CastCost(0, 5, 0, 0), quote.contributions().get(1).appliedDiscount());
        assertEquals(new CastCost(0, 0, 3, 0), quote.contributions().get(2).appliedDiscount());
        assertEquals(new CastCost(0, 0, 0, .2), quote.contributions().get(3).appliedDiscount());
        assertTrue(quote.contributions().stream().allMatch(ReagentContribution::wasCapped));
    }

    @Test
    void strictValueValidationRejectsUnsafeInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> new CastCost(Double.NaN, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new CastCost(0, -1, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new CastingPolicy(new CastCost(0, 0, 0, 0),
                        new CastCost(0, 0, 0, 0), -1, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new CastingPolicy(new CastCost(0, 0, 0, 0),
                        new CastCost(0, 0, 0, 0), 1, 1, 1,
                        nullDiscounts()));
    }

    @ParameterizedTest
    @EnumSource(ResourceEscrow.Outcome.class)
    void everySettlementOutcomeIsExplicitAndIdempotent(ResourceEscrow.Outcome outcome) {
        CastQuote quote = scrollQuote();
        ResourceEscrow reserved = ResourceEscrow.reserve(quote);
        ResourceEscrow settled = reserved.settle(outcome);

        assertEquals(outcome.consumesResources()
                ? ResourceEscrow.State.CONSUMED : ResourceEscrow.State.REFUNDED,
                settled.state());
        assertEquals(outcome, settled.outcome().orElseThrow());
        assertEquals(outcome.consumesResources() ? quote.finalCost().mana() : 0.0,
                settled.manaConsumed());
        assertEquals(outcome.consumesResources() ? quote.finalCost().mana() : quote.finalCost().mana(),
                outcome.consumesResources() ? settled.manaConsumed() : settled.manaRefunded());
        assertEquals(outcome.consumesResources() ? quote.loadout() : ReagentLoadout.empty(),
                settled.reagentsConsumed());
        assertEquals(outcome.consumesResources() ? ReagentLoadout.empty() : quote.loadout(),
                settled.reagentsRefunded());
        assertEquals(outcome.consumesResources(), settled.scrollConsumed());
        assertEquals(!outcome.consumesResources(), settled.scrollRefunded());

        assertSame(settled, settled.settle(outcome));
        assertSame(settled, settled.settle(ResourceEscrow.Outcome.ENGINE_FAILURE));
    }

    @Test
    void spellbookSettlementConsumesReagentsButNeverAHandheldScroll() {
        CastQuote quote = POLICY.quote(CastingMethod.SPELLBOOK,
                new CastCost(12, 4, 2, .4),
                ReagentLoadout.of(ReagentKind.MANA, 1, POLICY));
        ResourceEscrow settled = ResourceEscrow.reserve(quote).settle(ResourceEscrow.Outcome.SUCCESS);
        assertTrue(settled.isConsumed());
        assertFalse(settled.scrollReserved());
        assertFalse(settled.scrollConsumed());
        assertFalse(settled.scrollRefunded());
        assertEquals(quote.loadout(), settled.reagentsConsumed());
    }

    @Test
    void convenienceRefundRejectsAConsumingOutcome() {
        ResourceEscrow escrow = ResourceEscrow.reserve(scrollQuote());
        assertThrows(IllegalArgumentException.class,
                () -> escrow.refund(ResourceEscrow.Outcome.SUCCESS));
        ResourceEscrow refunded = escrow.settle(ResourceEscrow.Outcome.POLICY_REJECTED);
        assertSame(refunded, refunded.settle(ResourceEscrow.Outcome.SUCCESS));
    }

    private static CastQuote scrollQuote() {
        return POLICY.quote(CastingMethod.SCROLL, new CastCost(25, 6, 3, .5),
                ReagentLoadout.of(Map.of(
                        ReagentKind.MANA, 1,
                        ReagentKind.UPKEEP, 1), POLICY));
    }

    private static CastingPolicy policy(CastCost floors, CastCost caps, int maxUnitsPerKind,
            int maxTotalUnits, int maxOfferingUnits,
            Map<ReagentKind, CastCost> discountPerUnit) {
        return new CastingPolicy(floors, caps, maxUnitsPerKind, maxTotalUnits,
                maxOfferingUnits, discountPerUnit);
    }

    private static Map<ReagentKind, CastCost> nullDiscounts() {
        EnumMap<ReagentKind, CastCost> discounts = new EnumMap<>(ReagentKind.class);
        discounts.put(ReagentKind.MANA, null);
        return discounts;
    }
}
