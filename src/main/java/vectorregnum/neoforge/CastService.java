package vectorregnum.neoforge;

import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import vectorregnum.core.CastContext;
import vectorregnum.core.CastResult;
import vectorregnum.core.CompiledSpell;
import vectorregnum.core.EffectCommand;
import vectorregnum.core.Element;
import vectorregnum.core.Sigil;
import vectorregnum.core.SpellCompiler;
import vectorregnum.core.SpellEngine;
import vectorregnum.core.casting.CastCost;
import vectorregnum.core.casting.CastingMethod;
import vectorregnum.core.casting.ResourceEscrow;
import vectorregnum.core.circle.CircleCompilation;
import vectorregnum.core.circle.CircleCoordinate;
import vectorregnum.core.circle.PlacedSigil;
import vectorregnum.neoforge.ponder.PonderTraceNetworking;

/** Immediate compatibility casting through the same quote and escrow contract as vm2. */
public final class CastService {
    private static final SpellEngine ENGINE = new SpellEngine();

    private CastService() {
    }

    public static CastResult cast(ServerPlayer player, List<Sigil> sigils, boolean chargeMana) {
        return castAt(player, sigils, chargeMana, player.getEyePosition(),
                player.getViewVector(1.0F), CastingMethod.BARE, true, ItemStack.EMPTY);
    }

    public static CastResult castAt(ServerPlayer player, List<Sigil> sigils,
            boolean chargeMana, Vec3 origin, Vec3 direction) {
        return castAt(player, sigils, chargeMana, origin, direction,
                CastingMethod.BARE, true, ItemStack.EMPTY);
    }

    public static CastResult castAt(ServerPlayer player, List<Sigil> sigils,
            boolean chargeMana, Vec3 origin, Vec3 direction, CastingMethod method,
            boolean useStaged, ItemStack mediumStack) {
        if (!NeoForgeVmService.admitImmediateCast(player)) {
            return new CastResult.EngineFailure("RATE_LIMITED",
                    "Caster exceeded the bounded spell start rate");
        }
        if (chargeMana && ManaData.isChannelLocked(player)) {
            long ticks = ManaData.remainingLockTicks(player);
            player.sendSystemMessage(Component.literal("Mana channel locked for " + ticks + " more ticks")
                    .withStyle(ChatFormatting.RED), true);
            return new CastResult.EngineFailure("CHANNEL_LOCKED", "Caster is temporarily unable to channel mana");
        }

        CompiledSpell program;
        try {
            program = SpellCompiler.compile(sigils);
        } catch (RuntimeException exception) {
            return new CastResult.EngineFailure("COMPILER_REJECTED", exception.getMessage());
        }
        Element spellElement = spellElement(sigils);
        double adjustedMana = ManaData.adjustedCost(player, program.totalManaCost(), spellElement);
        CastCost baseline = CastingResourceService.baseline(method, adjustedMana,
                program.instructionCount(), 0.0, ManaData.instability(player, spellElement));
        Optional<CastingResourceService.Reservation> reserved = CastingResourceService.begin(
                player, method, baseline, chargeMana, useStaged, mediumStack);
        if (reserved.isEmpty()) {
            return new CastResult.EngineFailure("RESOURCE_REJECTED", "Casting resources were not reserved");
        }
        CastingResourceService.Reservation reservation = reserved.orElseThrow();
        long seed = player.serverLevel().getGameTime()
                ^ player.getUUID().getMostSignificantBits()
                ^ player.getUUID().getLeastSignificantBits();
        CastResult result;
        try {
            result = ENGINE.cast(program, new CastContext(player.getStringUUID(),
                    toCore(origin), toCore(direction), seed));
        } catch (RuntimeException exception) {
            CastingResourceService.settle(player, reservation, ResourceEscrow.Outcome.ENGINE_FAILURE);
            VectorRegnumMod.LOGGER.error("Compatibility engine threw after escrow", exception);
            return new CastResult.EngineFailure("ENGINE_EXCEPTION", exception.getMessage());
        }

        if (result instanceof CastResult.EngineFailure failure) {
            CastingResourceService.settle(player, reservation, ResourceEscrow.Outcome.ENGINE_FAILURE);
            VectorRegnumMod.LOGGER.error("Engine failure {}: {}", failure.code(), failure.message());
            player.sendSystemMessage(Component.literal("Vector-Regnum engine fault: " + failure.code())
                    .withStyle(ChatFormatting.DARK_RED), false);
            publishPonder(player, sigils, program, result);
            return result;
        }
        for (EffectCommand effect : result.effects()) {
            SpellVisualManager.apply(player, effect);
        }
        if (result instanceof CastResult.Success) {
            CastingResourceService.settle(player, reservation, ResourceEscrow.Outcome.SUCCESS);
            player.sendSystemMessage(Component.literal(String.format(
                            "Spell executed • %.2f μ • %.2f μ remaining",
                            reservation.quote().finalCost().mana(), ManaData.available(player)))
                    .withStyle(ChatFormatting.AQUA), true);
        } else if (result instanceof CastResult.SpellFailure failure) {
            CastingResourceService.settle(player, reservation,
                    ResourceEscrow.Outcome.GENUINE_SPELL_FAULT);
            if (chargeMana) {
                ManaData.lockChannel(player, ManaData.stabilityLockTicks(
                        100L, reservation.quote().finalCost().instability()));
            }
            player.sendSystemMessage(Component.literal("WILD MAGIC: "
                            + failure.fault().wildMagicCategory() + " at sigil "
                            + failure.fault().sourceIndex())
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        }
        publishPonder(player, sigils, program, result);
        return result;
    }

    private static void publishPonder(ServerPlayer player, List<Sigil> sigils,
            CompiledSpell program, CastResult result) {
        List<PlacedSigil> order = java.util.stream.IntStream.range(0, sigils.size())
                .mapToObj(index -> new PlacedSigil(new CircleCoordinate(0, index),
                        sigils.get(index).type()))
                .toList();
        PonderTraceNetworking.publishCompatibility(player, "server-compatibility-trace",
                "Compatibility spell • authoritative result",
                new CircleCompilation(order, sigils, program, List.of()), result);
    }

    private static vectorregnum.core.Vec3 toCore(Vec3 vector) {
        return new vectorregnum.core.Vec3(vector.x, vector.y, vector.z);
    }

    private static Element spellElement(List<Sigil> sigils) {
        return sigils.stream().map(Sigil::type)
                .filter(type -> type.startsWith("ELEMENT_"))
                .map(type -> type.substring("ELEMENT_".length()))
                .map(Element::fromId).flatMap(Optional::stream).findFirst()
                .orElse(Element.ARCANE);
    }
}
