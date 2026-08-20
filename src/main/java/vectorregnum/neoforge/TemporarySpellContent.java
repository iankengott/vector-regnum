package vectorregnum.neoforge;

import java.util.EnumMap;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import vectorregnum.core.semantic.CreationMaterial;

/** Internal scheduled blocks used by temporary utility/automation spells. */
public final class TemporarySpellContent {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(VectorRegnumMod.MOD_ID);
    private static final EnumMap<CreationMaterial, DeferredBlock<? extends Block>> CREATED_FORMS =
            new EnumMap<>(CreationMaterial.class);

    public static final DeferredBlock<MageLightBlock> MAGE_LIGHT = BLOCKS.register("mage_light",
            () -> new MageLightBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SEA_LANTERN)
                    .strength(-1.0F, 1_200.0F).noCollission().noOcclusion().noLootTable()
                    .lightLevel(state -> 15)));

    public static final DeferredBlock<OracleSignalBlock> ORACLE_SIGNAL = BLOCKS.register("oracle_signal",
            () -> new OracleSignalBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.REDSTONE_BLOCK)
                    .strength(-1.0F, 1_200.0F).noLootTable()));

    static {
        registerForm(CreationMaterial.STONE, Blocks.STONE);
        registerForm(CreationMaterial.ICE, Blocks.ICE);
        registerForm(CreationMaterial.WATER, Blocks.BLUE_STAINED_GLASS);
        registerForm(CreationMaterial.FIRE, Blocks.MAGMA_BLOCK);
        registerForm(CreationMaterial.LIGHT, Blocks.SEA_LANTERN);
        registerForm(CreationMaterial.ARCANE_FORCE, Blocks.AMETHYST_BLOCK);
    }

    private TemporarySpellContent() {
    }

    /** Registers all temporary blocks on the mod bus. */
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }

    /** Compatibility hook for the pre-NeoForge lifecycle; registration is now event-bus driven. */
    public static void initialize() {
    }

    /** Resolves a created-form block only when a spell is actually executing. */
    public static Block createdForm(CreationMaterial material) {
        DeferredBlock<? extends Block> block = CREATED_FORMS.get(material);
        return block == null ? null : block.get();
    }

    public static Block mageLight() {
        return MAGE_LIGHT.get();
    }

    public static Block oracleSignal() {
        return ORACLE_SIGNAL.get();
    }

    private static void registerForm(CreationMaterial material, Block appearance) {
        DeferredBlock<Block> block = BLOCKS.register("created_" + material.id(),
                () -> new CreatedFormBlock(BlockBehaviour.Properties.ofFullCopy(appearance)
                        .strength(-1.0F, 1_200.0F).noLootTable()));
        CREATED_FORMS.put(material, block);
    }
}
