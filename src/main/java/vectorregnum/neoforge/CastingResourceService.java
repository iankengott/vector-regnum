package vectorregnum.neoforge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import vectorregnum.core.casting.CastCost;
import vectorregnum.core.casting.CastQuote;
import vectorregnum.core.casting.CastingMethod;
import vectorregnum.core.casting.CastingPolicy;
import vectorregnum.core.casting.ReagentContribution;
import vectorregnum.core.casting.ReagentKind;
import vectorregnum.core.casting.ReagentLoadout;
import vectorregnum.core.casting.ResourceEscrow;

/** Server-thread inventory and mana adapter for the loader-neutral priority-22 escrow. */
public final class CastingResourceService {
    private static final String STAGING_HEADER_V1 = "vr-reagents:1";
    private static final String STAGING_HEADER = "vr-reagents:2";
    private static final Item OFFERING_ITEM = Items.QUARTZ;
    private static final Map<ReagentKind, Item> REAGENT_ITEMS = Map.of(
            ReagentKind.MANA, Items.AMETHYST_SHARD,
            ReagentKind.CASTING_TIME, Items.SUGAR,
            ReagentKind.UPKEEP, Items.GLOWSTONE_DUST,
            ReagentKind.INSTABILITY, Items.FERMENTED_SPIDER_EYE);
    private static final Map<UUID, ActiveReservation> ACTIVE = new LinkedHashMap<>();

    private CastingResourceService() {
    }

    public static CastingPolicy policy() {
        return CastingConfig.policy();
    }

    public static CastCost baseline(CastingMethod method, double mana,
            int instructionCount, double declaredUpkeep, double instability) {
        Objects.requireNonNull(method, "method");
        if (instructionCount < 0) throw new IllegalArgumentException("instruction count cannot be negative");
        double castingTicks = method.baseCastingTicks(instructionCount);
        CastingPolicy policy = policy();
        return new CastCost(Math.max(policy.floors().mana(), mana),
                Math.max(policy.floors().castingTime(), castingTicks),
                Math.max(policy.floors().upkeep(), declaredUpkeep),
                Math.max(policy.floors().instability(), instability));
    }

    public static ReagentLoadout staged(ServerPlayer player) {
        try {
            return decodeStaged(player.getData(PlayerAttachmentContent.STAGED_REAGENTS), policy());
        } catch (RuntimeException exception) {
            VectorRegnumMod.LOGGER.error("Rejected corrupt staged reagents for {}",
                    player.getUUID(), exception);
            return ReagentLoadout.empty();
        }
    }

    public static boolean stage(ServerPlayer player, ReagentKind kind, int count) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(kind, "kind");
        if (count < 1) throw new IllegalArgumentException("staged count must be positive");
        CastingPolicy policy = policy();
        ReagentLoadout current;
        try {
            current = decodeStaged(player.getData(PlayerAttachmentContent.STAGED_REAGENTS), policy);
        } catch (RuntimeException exception) {
            player.sendSystemMessage(Component.literal("Staged reagent data is corrupt; no item was moved")
                    .withStyle(ChatFormatting.DARK_RED), false);
            return false;
        }
        ReagentLoadout updated;
        try {
            updated = current.with(kind, Math.addExact(current.units(kind), count), policy);
        } catch (RuntimeException exception) {
            player.sendSystemMessage(Component.literal("That reagent loadout exceeds the server cap")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        Item item = reagentItem(kind);
        if (inventoryCount(player, item) < count) {
            player.sendSystemMessage(Component.literal("Need " + count + " "
                            + item.getDescription().getString() + " to stage " + kind.stableId())
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        consumeExact(player, item, count);
        persistStaged(player, updated);
        player.sendSystemMessage(Component.literal("Staged " + count + " " + kind.stableId()
                        + " reagent unit(s) for the next accepted cast")
                .withStyle(ChatFormatting.GREEN), false);
        reportStaged(player);
        return true;
    }

    public static boolean stageOffering(ServerPlayer player, int count) {
        Objects.requireNonNull(player, "player");
        if (count < 1) throw new IllegalArgumentException("staged count must be positive");
        CastingPolicy policy = policy();
        ReagentLoadout current;
        try {
            current = decodeStaged(player.getData(PlayerAttachmentContent.STAGED_REAGENTS), policy);
        } catch (RuntimeException exception) {
            player.sendSystemMessage(Component.literal("Staged reagent data is corrupt; no item was moved")
                    .withStyle(ChatFormatting.DARK_RED), false);
            return false;
        }
        ReagentLoadout updated;
        try {
            updated = current.withOfferingUnits(Math.addExact(current.offeringUnits(), count), policy);
        } catch (RuntimeException exception) {
            player.sendSystemMessage(Component.literal("That ritual offering exceeds the server cap")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        if (inventoryCount(player, OFFERING_ITEM) < count) {
            player.sendSystemMessage(Component.literal("Need " + count + " "
                            + OFFERING_ITEM.getDescription().getString() + " to stage a ritual offering")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        consumeExact(player, OFFERING_ITEM, count);
        persistStaged(player, updated);
        player.sendSystemMessage(Component.literal("Staged " + count
                        + " ritual offering unit(s) for the next accepted ritual")
                .withStyle(ChatFormatting.GREEN), false);
        reportStaged(player);
        return true;
    }

    public static int clearStaged(ServerPlayer player) {
        ReagentLoadout staged = staged(player);
        int returned = returnReagents(player, staged);
        persistStaged(player, ReagentLoadout.empty());
        player.sendSystemMessage(Component.literal("Returned " + returned
                        + " staged reagent/offering unit(s)")
                .withStyle(ChatFormatting.GREEN), false);
        return returned;
    }

    public static void reportStaged(ServerPlayer player) {
        ReagentLoadout staged = staged(player);
        player.sendSystemMessage(Component.literal("Staged reagents: " + staged.units()
                        + " • ritual offerings " + staged.offeringUnits()
                        + " • active escrows " + activeCount(player.getUUID()))
                .withStyle(ChatFormatting.AQUA), false);
    }

    public static CastQuote quote(ServerPlayer player, CastingMethod method,
            CastCost baseline, boolean useStaged) {
        ReagentLoadout loadout = useStaged ? staged(player) : ReagentLoadout.empty();
        if (!method.requiresOffering() && loadout.offeringUnits() > 0) {
            loadout = loadout.withOfferingUnits(0, policy());
        }
        return policy().quote(method, baseline, loadout);
    }

    public static CastQuote quoteAndReport(ServerPlayer player, CastingMethod method,
            CastCost baseline, boolean useStaged) {
        CastQuote quote = quote(player, method, baseline, useStaged);
        sendQuote(player, quote);
        return quote;
    }

    public static Optional<Reservation> begin(ServerPlayer player, CastingMethod method,
            CastCost baseline, boolean chargeMana, boolean useStaged, ItemStack mediumStack) {
        CastQuote quote;
        try {
            quote = quote(player, method, baseline, useStaged);
        } catch (IllegalArgumentException exception) {
            player.sendSystemMessage(Component.literal(exception.getMessage())
                    .withStyle(ChatFormatting.RED), false);
            return Optional.empty();
        }
        sendQuote(player, quote);
        int castingTicks = Math.max(1, (int) Math.ceil(quote.finalCost().castingTime()));
        if (!chargeMana) {
            // Internal semantic follow-ups and visual showcases are already inside an
            // admitted cast; they must not introduce a second player-facing wind-up.
            return Optional.of(new Reservation(null, quote, 1, false));
        }
        if (method == CastingMethod.SCROLL && (mediumStack == null || mediumStack.isEmpty())) {
            player.sendSystemMessage(Component.literal("A scroll cast must reserve its physical scroll")
                    .withStyle(ChatFormatting.RED), false);
            return Optional.empty();
        }
        double mana = quote.finalCost().mana();
        double upkeep = quote.finalCost().upkeep();
        double commitment = mana + upkeep;
        if (!Double.isFinite(commitment)) {
            throw new IllegalArgumentException("cast mana and upkeep commitment overflowed");
        }
        if (!ManaData.ensureAvailable(player, commitment)
                || !ManaData.trySpend(player, commitment)) {
            player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                            "%s needs %.2f μ plus %.2f μ prepaid upkeep; only %.2f μ is available",
                            method.stableLabel(), mana, upkeep, ManaData.available(player)))
                    .withStyle(ChatFormatting.RED), true);
            return Optional.empty();
        }

        ItemStack scrollRefund = ItemStack.EMPTY;
        try {
            if (method == CastingMethod.SCROLL) {
                scrollRefund = mediumStack.copyWithCount(1);
                mediumStack.shrink(1);
            }
            if (useStaged) {
                ReagentLoadout remaining = method.requiresOffering()
                        ? ReagentLoadout.empty()
                        : ReagentLoadout.empty().withOfferingUnits(staged(player).offeringUnits(), policy());
                persistStaged(player, remaining);
            }
            ResourceEscrow escrow = ResourceEscrow.reserve(quote, method == CastingMethod.SCROLL);
            UUID id = UUID.randomUUID();
            ACTIVE.put(id, new ActiveReservation(player, escrow, scrollRefund, upkeep));
            player.sendSystemMessage(Component.literal("Escrow reserved • " + id.toString().substring(0, 8)
                            + " • " + quote.loadout().totalUnits() + " reagent unit(s) • "
                            + format(upkeep) + " μ upkeep prepaid")
                    .withStyle(ChatFormatting.GOLD), false);
            return Optional.of(new Reservation(id, quote, castingTicks, true));
        } catch (RuntimeException exception) {
            if (!ManaData.tryCreditExact(player, commitment)) {
                VectorRegnumMod.LOGGER.error("Could not roll back failed escrow mana reservation for {}",
                        player.getUUID());
            }
            if (!scrollRefund.isEmpty()) player.getInventory().placeItemBackInInventory(scrollRefund);
            throw exception;
        }
    }

    public static Settlement settle(ServerPlayer player, Reservation reservation,
            ResourceEscrow.Outcome outcome) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(reservation, "reservation");
        Objects.requireNonNull(outcome, "outcome");
        if (!reservation.funded()) {
            return new Settlement(outcome, outcome.consumesResources()
                    ? ResourceEscrow.State.CONSUMED : ResourceEscrow.State.REFUNDED, false);
        }
        ActiveReservation active = ACTIVE.remove(reservation.id());
        if (active == null) {
            return new Settlement(outcome, outcome.consumesResources()
                    ? ResourceEscrow.State.CONSUMED : ResourceEscrow.State.REFUNDED, false);
        }
        if (!active.owner().equals(player.getUUID())) {
            ACTIVE.put(reservation.id(), active);
            throw new IllegalArgumentException("escrow owner mismatch");
        }
        ResourceEscrow terminal = active.escrow().settle(outcome);
        double upkeepRefund = active.upkeepClaimed ? 0.0 : active.upkeepReserved;
        if (terminal.isRefunded() || upkeepRefund > 0.0) {
            double refund = terminal.manaRefunded() + upkeepRefund;
            if (!ManaData.tryCreditExact(player, refund)) {
                ACTIVE.put(reservation.id(), active);
                throw new IllegalStateException("reserved mana no longer fits its payer capacity");
            }
        }
        if (terminal.isRefunded()) {
            returnReagents(player, terminal.reagentsRefunded());
            if (terminal.scrollRefunded() && !active.scrollRefund().isEmpty()) {
                player.getInventory().placeItemBackInInventory(active.scrollRefund().copy());
            }
        }
        player.sendSystemMessage(Component.literal("Escrow "
                        + (terminal.isConsumed() ? "consumed" : "refunded") + " • "
                        + outcome.name().toLowerCase(Locale.ROOT))
                .withStyle(terminal.isConsumed() ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GREEN), false);
        return new Settlement(outcome, terminal.state(), true);
    }

    public static Settlement settle(Reservation reservation, ResourceEscrow.Outcome outcome) {
        Objects.requireNonNull(reservation, "reservation");
        if (!reservation.funded()) {
            return new Settlement(outcome, outcome.consumesResources()
                    ? ResourceEscrow.State.CONSUMED : ResourceEscrow.State.REFUNDED, false);
        }
        ActiveReservation active = ACTIVE.get(reservation.id());
        if (active == null) {
            return new Settlement(outcome, outcome.consumesResources()
                    ? ResourceEscrow.State.CONSUMED : ResourceEscrow.State.REFUNDED, false);
        }
        return settle(active.payer(), reservation, outcome);
    }

    /**
     * Transfers the prepaid upkeep commitment from a live cast into its durable
     * persistent-effect ledger. The first claim wins so duplicate semantic
     * callbacks cannot mint a second balance.
     */
    public static double claimUpkeep(Reservation reservation) {
        Objects.requireNonNull(reservation, "reservation");
        if (!reservation.funded()) {
            // Internal follow-ups and the guarded visual showcase do not debit a
            // player. They still receive the quoted bounded budget so their
            // continuation follows the same ledger state machine.
            return reservation.quote().finalCost().upkeep();
        }
        ActiveReservation active = ACTIVE.get(reservation.id());
        if (active == null || active.upkeepClaimed) return 0.0;
        active.upkeepClaimed = true;
        return active.upkeepReserved;
    }

    /** Rolls back a failed durable-ledger handoff before cast settlement. */
    public static void releaseUpkeepClaim(Reservation reservation) {
        Objects.requireNonNull(reservation, "reservation");
        if (!reservation.funded()) return;
        ActiveReservation active = ACTIVE.get(reservation.id());
        if (active != null) active.upkeepClaimed = false;
    }

    public static int refundOwner(ServerPlayer player, ResourceEscrow.Outcome outcome) {
        return refundOwner(player.getUUID(), outcome);
    }

    public static int refundOwner(UUID owner, ResourceEscrow.Outcome outcome) {
        if (outcome.consumesResources()) throw new IllegalArgumentException("refund outcome required");
        List<UUID> ids = ACTIVE.entrySet().stream()
                .filter(entry -> entry.getValue().owner().equals(owner))
                .map(Map.Entry::getKey).toList();
        int refunded = 0;
        for (UUID id : ids) {
            ActiveReservation active = ACTIVE.get(id);
            if (active == null) continue;
            Reservation reservation = new Reservation(id, active.escrow().quote(),
                    Math.max(1, (int) Math.ceil(active.escrow().quote().finalCost().castingTime())), true);
            if (settle(reservation, outcome).changed()) refunded++;
        }
        return refunded;
    }

    public static int refundAll(MinecraftServer server, ResourceEscrow.Outcome outcome) {
        Objects.requireNonNull(server, "server");
        if (outcome.consumesResources()) throw new IllegalArgumentException("refund outcome required");
        int refunded = 0;
        for (UUID id : List.copyOf(ACTIVE.keySet())) {
            ActiveReservation active = ACTIVE.get(id);
            if (active == null) continue;
            Reservation reservation = new Reservation(id, active.escrow().quote(),
                    Math.max(1, (int) Math.ceil(active.escrow().quote().finalCost().castingTime())), true);
            if (settle(reservation, outcome).changed()) refunded++;
        }
        return refunded;
    }

    public static double reservedMana(UUID owner) {
        return ACTIVE.values().stream().filter(active -> active.owner().equals(owner))
                .mapToDouble(active -> active.escrow().reservedMana()
                        + (active.upkeepClaimed ? 0.0 : active.upkeepReserved)).sum();
    }

    public static int activeCount(UUID owner) {
        return (int) ACTIVE.values().stream().filter(active -> active.owner().equals(owner)).count();
    }

    public static int activeCount() {
        return ACTIVE.size();
    }

    public static Item reagentItem(ReagentKind kind) {
        return REAGENT_ITEMS.get(Objects.requireNonNull(kind, "kind"));
    }

    public static Item offeringItem() {
        return OFFERING_ITEM;
    }

    public static Optional<ReagentKind> reagentKind(String id) {
        return java.util.Arrays.stream(ReagentKind.values())
                .filter(kind -> kind.stableId().equals(id)).findFirst();
    }

    private static void sendQuote(ServerPlayer player, CastQuote quote) {
        CastCost before = quote.undiscounted();
        CastCost after = quote.finalCost();
        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                        "%s quote • before %.2f μ / %.0ft / %.2f upkeep / %.2f instability",
                        quote.method().stableLabel(), before.mana(), before.castingTime(),
                        before.upkeep(), before.instability()))
                .withStyle(ChatFormatting.AQUA), false);
        for (ReagentContribution contribution : quote.contributions()) {
            player.sendSystemMessage(Component.literal("  " + contribution.units() + "× "
                            + reagentItem(contribution.kind()).getDescription().getString() + " reduces "
                            + contribution.kind().stableId() + " • requested "
                            + format(contribution.requestedDiscount().value(contribution.kind()))
                            + " • applied "
                            + format(contribution.appliedDiscount().value(contribution.kind()))
                            + (contribution.wasCapped() ? " (clipped by cap/floor)" : ""))
                    .withStyle(ChatFormatting.GRAY), false);
        }
        if (quote.loadout().offeringUnits() > 0) {
            player.sendSystemMessage(Component.literal("  " + quote.loadout().offeringUnits()
                            + "× " + OFFERING_ITEM.getDescription().getString()
                            + " committed as ritual offering (no discount)")
                    .withStyle(ChatFormatting.GRAY), false);
        }
        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                        "Final • %.2f μ / %.0ft / %.2f upkeep / %.2f instability",
                        after.mana(), after.castingTime(), after.upkeep(), after.instability()))
                .withStyle(ChatFormatting.GOLD), false);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static int inventoryCount(ServerPlayer player, Item item) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) total = Math.addExact(total, stack.getCount());
        }
        return total;
    }

    private static void consumeExact(ServerPlayer player, Item item, int count) {
        int remaining = count;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(item)) continue;
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
        }
        if (remaining != 0) throw new IllegalStateException("inventory changed during reagent staging");
    }

    private static int returnReagents(ServerPlayer player, ReagentLoadout loadout) {
        int returned = 0;
        for (ReagentKind kind : ReagentKind.values()) {
            int units = loadout.units(kind);
            if (units > 0) {
                player.getInventory().placeItemBackInInventory(new ItemStack(reagentItem(kind), units));
                returned = Math.addExact(returned, units);
            }
        }
        if (loadout.offeringUnits() > 0) {
            player.getInventory().placeItemBackInInventory(
                    new ItemStack(OFFERING_ITEM, loadout.offeringUnits()));
            returned = Math.addExact(returned, loadout.offeringUnits());
        }
        return returned;
    }

    private static void persistStaged(ServerPlayer player, ReagentLoadout loadout) {
        player.setData(PlayerAttachmentContent.STAGED_REAGENTS, encodeStaged(loadout));
    }

    static String encodeStaged(ReagentLoadout loadout) {
        if (loadout.isEmpty()) return "";
        StringBuilder body = new StringBuilder(STAGING_HEADER);
        for (ReagentKind kind : ReagentKind.values()) {
            body.append('|').append(kind.stableId()).append('=').append(loadout.units(kind));
        }
        body.append('|').append("offering=").append(loadout.offeringUnits());
        String content = body.toString();
        return content + "|sha256=" + sha256(content);
    }

    static ReagentLoadout decodeStaged(String encoded, CastingPolicy policy) {
        if (encoded == null || encoded.isBlank()) return ReagentLoadout.empty();
        int checksumAt = encoded.lastIndexOf("|sha256=");
        if (checksumAt < 0) throw new IllegalArgumentException("missing reagent checksum");
        String content = encoded.substring(0, checksumAt);
        String checksum = encoded.substring(checksumAt + "|sha256=".length());
        if (!MessageDigest.isEqual(sha256(content).getBytes(StandardCharsets.US_ASCII),
                checksum.getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("reagent checksum mismatch");
        }
        String[] parts = content.split("\\|", -1);
        boolean legacy = parts[0].equals(STAGING_HEADER_V1);
        int expectedParts = ReagentKind.values().length + (legacy ? 1 : 2);
        if (parts.length != expectedParts
                || (!legacy && !parts[0].equals(STAGING_HEADER))) {
            throw new IllegalArgumentException("malformed reagent staging document");
        }
        EnumMap<ReagentKind, Integer> counts = new EnumMap<>(ReagentKind.class);
        int offeringUnits = 0;
        for (int index = 1; index < parts.length; index++) {
            String[] field = parts[index].split("=", -1);
            if (field.length != 2) throw new IllegalArgumentException("malformed reagent field");
            if (field[0].equals("offering")) {
                if (legacy || index != parts.length - 1) {
                    throw new IllegalArgumentException("misplaced ritual offering field");
                }
                offeringUnits = Integer.parseInt(field[1]);
                continue;
            }
            ReagentKind kind = reagentKind(field[0]).orElseThrow(
                    () -> new IllegalArgumentException("unknown reagent kind " + field[0]));
            if (counts.put(kind, Integer.parseInt(field[1])) != null) {
                throw new IllegalArgumentException("duplicate reagent kind " + kind);
            }
        }
        return ReagentLoadout.of(counts, offeringUnits, policy);
    }

    private static String sha256(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record Reservation(UUID id, CastQuote quote, int castingTicks, boolean funded) {
        public Reservation {
            Objects.requireNonNull(quote, "quote");
            if (castingTicks < 1) throw new IllegalArgumentException("casting ticks must be positive");
            if (funded != (id != null)) throw new IllegalArgumentException("funded reservations require an id");
        }
    }

    public record Settlement(ResourceEscrow.Outcome outcome, ResourceEscrow.State state,
            boolean changed) {
    }

    private static final class ActiveReservation {
        private final ServerPlayer payer;
        private final ResourceEscrow escrow;
        private final ItemStack scrollRefund;
        private final double upkeepReserved;
        private boolean upkeepClaimed;

        private ActiveReservation(ServerPlayer payer, ResourceEscrow escrow,
                ItemStack scrollRefund, double upkeepReserved) {
            this.payer = Objects.requireNonNull(payer, "payer");
            this.escrow = Objects.requireNonNull(escrow, "escrow");
            this.scrollRefund = Objects.requireNonNull(scrollRefund, "scrollRefund");
            if (!Double.isFinite(upkeepReserved) || upkeepReserved < 0.0) {
                throw new IllegalArgumentException("upkeep reserve must be finite and non-negative");
            }
            this.upkeepReserved = upkeepReserved;
        }

        private ServerPlayer payer() {
            return payer;
        }

        private ResourceEscrow escrow() {
            return escrow;
        }

        private ItemStack scrollRefund() {
            return scrollRefund;
        }

        private UUID owner() {
            return payer.getUUID();
        }
    }
}
