package vectorregnum.neoforge;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import java.util.EnumMap;
import java.util.Map;
import vectorregnum.core.semantic.CreationMaterial;

/** Internal scheduled blocks used by temporary utility/automation spells. */
public final class TemporarySpellContent {
    private static final EnumMap<CreationMaterial, Block> CREATED_FORMS =
            new EnumMap<>(CreationMaterial.class);
    public static final Block MAGE_LIGHT = Registry.register(Registries.BLOCK,
            Identifier.of(VectorRegnumMod.MOD_ID, "mage_light"),
            new MageLightBlock(AbstractBlock.Settings.copy(Blocks.SEA_LANTERN)
                    .strength(-1.0F, 1_200.0F).noCollision().nonOpaque().dropsNothing()
                    .luminance(state -> 15)));

    public static final Block ORACLE_SIGNAL = Registry.register(Registries.BLOCK,
            Identifier.of(VectorRegnumMod.MOD_ID, "oracle_signal"),
            new OracleSignalBlock(AbstractBlock.Settings.copy(Blocks.REDSTONE_BLOCK)
                    .strength(-1.0F, 1_200.0F).dropsNothing()));

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

    public static void initialize() {
        // Static initialization registers both internal blocks.
    }

    public static Block createdForm(CreationMaterial material) {
        return CREATED_FORMS.get(material);
    }

    private static void registerForm(CreationMaterial material, Block appearance) {
        Block block = Registry.register(Registries.BLOCK,
                Identifier.of(VectorRegnumMod.MOD_ID, "created_" + material.id()),
                new CreatedFormBlock(AbstractBlock.Settings.copy(appearance)
                        .strength(-1.0F, 1_200.0F).dropsNothing()));
        CREATED_FORMS.put(material, block);
    }
}
