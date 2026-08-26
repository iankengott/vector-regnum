package vectorregnum.neoforge;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import vectorregnum.core.CastContext;
import vectorregnum.core.CastResult;
import vectorregnum.core.CompiledSpell;
import vectorregnum.core.Element;
import vectorregnum.core.EffectCommand;
import vectorregnum.core.FaultCode;
import vectorregnum.core.Sigil;
import vectorregnum.core.SpellCompiler;
import vectorregnum.core.SpellEngine;
import vectorregnum.core.SpellFault;
import vectorregnum.core.WildMagicCategory;
import vectorregnum.core.circle.CircleCompilation;
import vectorregnum.core.circle.CircleCoordinate;
import vectorregnum.core.circle.PlacedSigil;
import vectorregnum.neoforge.ponder.PonderTraceNetworking;

import java.util.List;
import java.util.Optional;

public final class CastService {
    private static final SpellEngine ENGINE = new SpellEngine();

    private CastService() {
    }

    public static CastResult cast(ServerPlayer player, List<Sigil> sigils, boolean chargeMana) {
        return castAt(player, sigils, chargeMana, player.getEyePosition(), player.getViewVector(1.0F));
    }

    public static CastResult castAt(ServerPlayer player, List<Sigil> sigils,
            boolean chargeMana, Vec3 origin, Vec3 direction) {
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

        CompiledSpell program = SpellCompiler.compile(sigils);
        long seed = player.serverLevel().getGameTime()
                ^ player.getUUID().getMostSignificantBits()
                ^ player.getUUID().getLeastSignificantBits();
        CastContext context = new CastContext(
                player.getStringUUID(),
                toCore(origin),
                toCore(direction),
                seed);
        CastResult result = ENGINE.cast(program, context);
        Element spellElement = spellElement(sigils);

        if (result instanceof CastResult.EngineFailure failure) {
            VectorRegnumMod.LOGGER.error("Engine failure {}: {}", failure.code(), failure.message());
            player.sendSystemMessage(Component.literal("Vector-Regnum engine fault: " + failure.code())
                    .withStyle(ChatFormatting.DARK_RED), false);
            return result;
        }
        double adjustedCost = ManaData.adjustedCost(player, result.manaCost(), spellElement);

        if (chargeMana && (!ManaData.ensureAvailable(player, adjustedCost)
                || !ManaData.trySpend(player, adjustedCost))) {
            ManaData.drain(player);
            ManaData.lockChannel(player, 200L);
            EffectCommand.WildMagic starvation = new EffectCommand.WildMagic(
                    player.getStringUUID(),
                    WildMagicCategory.INTERNAL_MANA_DETONATION,
                    Optional.of(toCore(origin)),
                    Optional.empty(),
                    Optional.empty(),
                    0,
                    "Insufficient mana to stabilize the spell",
                    seed);
            SpellVisualManager.apply(player, starvation);
            player.sendSystemMessage(Component.literal("INSUFFICIENT MANA — the circle collapses inward")
                    .withStyle(ChatFormatting.DARK_RED), false);
            SpellFault fault = new SpellFault(
                    FaultCode.INSUFFICIENT_MANA,
                    starvation.reason(),
                    starvation.sourceIndex(),
                    starvation.category());
            CastResult failure = new CastResult.SpellFailure(program, fault, List.of(starvation));
            publishPonder(player, sigils, program, failure);
            return failure;
        }

        for (EffectCommand effect : result.effects()) {
            SpellVisualManager.apply(player, effect);
        }

        if (result instanceof CastResult.Success) {
            String message = String.format(
                    "Spell executed • %.2f μ • %.2f μ remaining",
                    adjustedCost, ManaData.available(player));
            player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.AQUA), true);
        } else if (result instanceof CastResult.SpellFailure failure) {
            if (chargeMana) {
                ManaData.lockChannel(player, ManaData.stabilityLockTicks(player, 100L, spellElement));
            }
            player.sendSystemMessage(Component.literal(
                            "WILD MAGIC: " + failure.fault().wildMagicCategory()
                                    + " at sigil " + failure.fault().sourceIndex())
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
                "Compatibility spell — authoritative result",
                new CircleCompilation(order, sigils, program, List.of()), result);
    }

    private static vectorregnum.core.Vec3 toCore(Vec3 vector) {
        return new vectorregnum.core.Vec3(vector.x, vector.y, vector.z);
    }

    private static Element spellElement(List<Sigil> sigils) {
        return sigils.stream()
                .map(Sigil::type)
                .filter(type -> type.startsWith("ELEMENT_"))
                .map(type -> type.substring("ELEMENT_".length()))
                .map(Element::fromId)
                .flatMap(Optional::stream)
                .findFirst()
                .orElse(Element.ARCANE);
    }
}
