package vectorregnum.neoforge;

import com.mojang.serialization.Codec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import vectorregnum.neoforge.progression.ProgressionState;

/**
 * The persistent per-player state owned by Vector-Regnum.
 *
 * <p>NeoForge attachments are registered objects, rather than Fabric's
 * class-initialization registry entries.  Keeping every player value in one
 * registry makes the registration boundary explicit and lets Entity's
 * built-in attachment serializer handle player NBT and respawn copies.</p>
 */
public final class PlayerAttachmentContent {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, VectorRegnumMod.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Double>> MANA =
            ATTACHMENTS.register("mana", () -> AttachmentType.<Double>builder(() -> ManaData.STARTING_MANA)
                    .serialize(Codec.DOUBLE)
                    .copyOnDeath()
                    .build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Double>> MANA_CAPACITY =
            ATTACHMENTS.register("mana_capacity", () -> AttachmentType.<Double>builder(() -> ManaData.STARTING_CAPACITY)
                    .serialize(Codec.DOUBLE)
                    .copyOnDeath()
                    .build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<String>> MANA_AFFINITY =
            ATTACHMENTS.register("mana_affinity", () -> AttachmentType.<String>builder(
                            () -> vectorregnum.neoforge.progression.ManaAffinity.ARCANE.name())
                    .serialize(Codec.STRING)
                    .copyOnDeath()
                    .build());

    /** The character's permanent natural element; an empty value is migrated once. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<String>> NATURAL_ELEMENT =
            ATTACHMENTS.register("natural_element", () -> AttachmentType.<String>builder(() -> "")
                    .serialize(Codec.STRING)
                    .copyOnDeath()
                    .build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> ATTUNED_SOURCE =
            ATTACHMENTS.register("attuned_source", () -> AttachmentType.<Long>builder(() -> Long.MIN_VALUE)
                    .serialize(Codec.LONG)
                    .copyOnDeath()
                    .build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<String>> ATTUNED_DIMENSION =
            ATTACHMENTS.register("attuned_dimension", () -> AttachmentType.<String>builder(() -> "")
                    .serialize(Codec.STRING)
                    .copyOnDeath()
                    .build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> CHANNEL_LOCK_UNTIL =
            ATTACHMENTS.register("channel_lock_until", () -> AttachmentType.<Long>builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .copyOnDeath()
                    .build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<String>> AUTHORED_CIRCLE =
            ATTACHMENTS.register("authored_circle", () -> AttachmentType.<String>builder(
                            () -> vectorregnum.core.circle.CirclePersistence.encode(
                                    CircleAuthoringService.starterCircle()))
                    .serialize(Codec.STRING)
                    .copyOnDeath()
                    .build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> RECEIVED_TUTORIAL_GUIDE =
            ATTACHMENTS.register("received_tutorial_guide", () -> AttachmentType.<Boolean>builder(() -> false)
                    .serialize(Codec.BOOL)
                    .copyOnDeath()
                    .build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> TUTORIAL_GUIDE_VERSION =
            ATTACHMENTS.register("tutorial_guide_version", () -> AttachmentType.<Integer>builder(() -> 0)
                    .serialize(Codec.INT)
                    .copyOnDeath()
                    .build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> PLAYER_DATA_SCHEMA =
            ATTACHMENTS.register("player_data_schema", () -> AttachmentType.<Integer>builder(() -> 0)
                    .serialize(Codec.INT)
                    .copyOnDeath()
                    .build());

    private static final Codec<ProgressionState> PROGRESSION_CODEC = Codec.STRING.listOf().xmap(
            ProgressionState::fromIds,
            ProgressionState::ids);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ProgressionState>> PROGRESSION_UNLOCKS =
            ATTACHMENTS.register("progression_unlocks", () -> AttachmentType.<ProgressionState>builder(
                            () -> ProgressionState.EMPTY)
                    .serialize(PROGRESSION_CODEC)
                    .copyOnDeath()
                    .build());

    private PlayerAttachmentContent() {
    }

    /** Registers all player attachments on the NeoForge mod event bus. */
    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }
}
