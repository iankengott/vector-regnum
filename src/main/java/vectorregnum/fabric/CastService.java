package vectorregnum.fabric;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import vectorregnum.core.CastContext;
import vectorregnum.core.CastResult;
import vectorregnum.core.CompiledSpell;
import vectorregnum.core.EffectCommand;
import vectorregnum.core.FaultCode;
import vectorregnum.core.Sigil;
import vectorregnum.core.SpellCompiler;
import vectorregnum.core.SpellEngine;
import vectorregnum.core.SpellFault;
import vectorregnum.core.Vec3;
import vectorregnum.core.WildMagicCategory;
import vectorregnum.core.circle.CircleCompilation;
import vectorregnum.core.circle.CircleCoordinate;
import vectorregnum.core.circle.PlacedSigil;
import vectorregnum.fabric.ponder.PonderTraceNetworking;

import java.util.List;
import java.util.Optional;

public final class CastService {
    private static final SpellEngine ENGINE = new SpellEngine();

    private CastService() {
    }

    public static CastResult cast(ServerPlayerEntity player, List<Sigil> sigils, boolean chargeMana) {
        return castAt(player, sigils, chargeMana, player.getEyePos(), player.getRotationVec(1.0F));
    }

    public static CastResult castAt(ServerPlayerEntity player, List<Sigil> sigils,
            boolean chargeMana, Vec3d origin, Vec3d direction) {
        if (!FabricVmService.admitImmediateCast(player)) {
            return new CastResult.EngineFailure("RATE_LIMITED",
                    "Caster exceeded the bounded spell start rate");
        }
        if (chargeMana && ManaData.isChannelLocked(player)) {
            long ticks = ManaData.remainingLockTicks(player);
            player.sendMessage(Text.literal("Mana channel locked for " + ticks + " more ticks")
                    .formatted(Formatting.RED), true);
            return new CastResult.EngineFailure("CHANNEL_LOCKED", "Caster is temporarily unable to channel mana");
        }

        CompiledSpell program = SpellCompiler.compile(sigils);
        long seed = player.getServerWorld().getTime()
                ^ player.getUuid().getMostSignificantBits()
                ^ player.getUuid().getLeastSignificantBits();
        CastContext context = new CastContext(
                player.getUuidAsString(),
                toCore(origin),
                toCore(direction),
                seed);
        CastResult result = ENGINE.cast(program, context);

        if (result instanceof CastResult.EngineFailure failure) {
            VectorRegnumMod.LOGGER.error("Engine failure {}: {}", failure.code(), failure.message());
            player.sendMessage(Text.literal("Vector-Regnum engine fault: " + failure.code())
                    .formatted(Formatting.DARK_RED), false);
            return result;
        }

        if (chargeMana && (!ManaData.ensureAvailable(player, result.manaCost())
                || !ManaData.trySpend(player, result.manaCost()))) {
            ManaData.drain(player);
            ManaData.lockChannel(player, 200L);
            EffectCommand.WildMagic starvation = new EffectCommand.WildMagic(
                    player.getUuidAsString(),
                    WildMagicCategory.INTERNAL_MANA_DETONATION,
                    Optional.of(toCore(origin)),
                    Optional.empty(),
                    Optional.empty(),
                    0,
                    "Insufficient mana to stabilize the spell",
                    seed);
            SpellVisualManager.apply(player, starvation);
            player.sendMessage(Text.literal("INSUFFICIENT MANA — the circle collapses inward")
                    .formatted(Formatting.DARK_RED), false);
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
                    result.manaCost(), ManaData.available(player));
            player.sendMessage(Text.literal(message).formatted(Formatting.AQUA), true);
        } else if (result instanceof CastResult.SpellFailure failure) {
            if (chargeMana) {
                ManaData.lockChannel(player, 100L);
            }
            player.sendMessage(Text.literal(
                            "WILD MAGIC: " + failure.fault().wildMagicCategory()
                                    + " at sigil " + failure.fault().sourceIndex())
                    .formatted(Formatting.LIGHT_PURPLE), false);
        }
        publishPonder(player, sigils, program, result);
        return result;
    }

    private static void publishPonder(ServerPlayerEntity player, List<Sigil> sigils,
            CompiledSpell program, CastResult result) {
        List<PlacedSigil> order = java.util.stream.IntStream.range(0, sigils.size())
                .mapToObj(index -> new PlacedSigil(new CircleCoordinate(0, index),
                        sigils.get(index).type()))
                .toList();
        PonderTraceNetworking.publishCompatibility(player, "server-compatibility-trace",
                "Compatibility spell — authoritative result",
                new CircleCompilation(order, sigils, program, List.of()), result);
    }

    private static Vec3 toCore(Vec3d vector) {
        return new Vec3(vector.x, vector.y, vector.z);
    }
}
