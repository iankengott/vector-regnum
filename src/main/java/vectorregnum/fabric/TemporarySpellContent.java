package vectorregnum.fabric;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** Internal scheduled blocks used by temporary utility/automation spells. */
public final class TemporarySpellContent {
    public static final Block MAGE_LIGHT = Registry.register(Registries.BLOCK,
            Identifier.of(VectorRegnumMod.MOD_ID, "mage_light"),
            new MageLightBlock(AbstractBlock.Settings.copy(Blocks.SEA_LANTERN)
                    .strength(-1.0F, 1_200.0F).noCollision().nonOpaque().dropsNothing()
                    .luminance(state -> 15)));

    public static final Block ORACLE_SIGNAL = Registry.register(Registries.BLOCK,
            Identifier.of(VectorRegnumMod.MOD_ID, "oracle_signal"),
            new OracleSignalBlock(AbstractBlock.Settings.copy(Blocks.REDSTONE_BLOCK)
                    .strength(-1.0F, 1_200.0F).dropsNothing()));

    private TemporarySpellContent() {
    }

    public static void initialize() {
        // Static initialization registers both internal blocks.
    }
}
