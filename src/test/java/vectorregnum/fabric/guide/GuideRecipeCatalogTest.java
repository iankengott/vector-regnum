package vectorregnum.fabric.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.stream.IntStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class GuideRecipeCatalogTest {
    @Test
    void referencedRecipesLoadTheirRealShapedAndShapelessGrids() throws IOException {
        ClassLoader loader = getClass().getClassLoader();
        GuideRecipeCatalog catalog = GuideRecipeCatalog.load(
                GuideDataLoader.loadDefault(loader), loader);

        assertEquals(9, catalog.size());
        GuideRecipe node = catalog.recipe("vector_regnum:mana_crystal_node").orElseThrow();
        assertEquals(GuideRecipe.Kind.SHAPED, node.kind());
        assertEquals(List.of("minecraft:amethyst_block"), node.ingredient(0, 0).choices());
        assertEquals(List.of("minecraft:crying_obsidian"), node.ingredient(1, 1).choices());
        assertEquals("vector_regnum:mana_crystal_node", node.result());

        GuideRecipe shard = catalog.recipe("vector_regnum:mana_crystal_shard").orElseThrow();
        assertEquals(GuideRecipe.Kind.SHAPELESS, shard.kind());
        assertEquals(List.of("minecraft:amethyst_shard"), shard.ingredient(0, 0).choices());
        assertEquals(List.of("minecraft:lapis_lazuli"), shard.ingredient(0, 1).choices());
        assertEquals(List.of("minecraft:glowstone_dust"), shard.ingredient(0, 2).choices());
        assertTrue(shard.ingredient(1, 0).isEmpty());

        GuideRecipe scroll = catalog.recipe("vector_regnum:spell_scroll").orElseThrow();
        assertEquals(List.of("minecraft:ink_sac", "minecraft:black_dye"),
                scroll.ingredient(0, 1).choices());

        GuideRecipe vial = catalog.recipe("vector_regnum:crystal_vial").orElseThrow();
        assertEquals(GuideRecipe.Kind.SHAPED, vial.kind());
        assertEquals(List.of("vector_regnum:mana_crystal_shard"),
                vial.ingredient(0, 1).choices());
        assertEquals("vector_regnum:crystal_vial", vial.result());
        assertTrue(catalog.recipe("vector_regnum:runed_mana_cell").isPresent());
        assertTrue(catalog.recipe("vector_regnum:resonant_vault").isPresent());
    }

    @Test
    void alternativesAndTagsRemainLiveBoundedChoices() {
        GuideRecipe recipe = GuideRecipeCatalog.parse(JsonParser.parseString("""
                {
                  "type": "minecraft:crafting_shapeless",
                  "ingredients": [
                    [{"item":"minecraft:paper"},{"item":"minecraft:book"}],
                    {"tag":"minecraft:planks"}
                  ],
                  "result": {"id":"minecraft:stick","count":2}
                }
                """).getAsJsonObject());

        GuideIngredient alternatives = recipe.ingredient(0, 0);
        assertEquals("minecraft:paper", alternatives.displayChoice(0, ignored -> List.of()));
        assertEquals("minecraft:book", alternatives.displayChoice(1, ignored -> List.of()));
        GuideIngredient tag = recipe.ingredient(0, 1);
        assertTrue(tag.containsTag());
        assertEquals(List.of("minecraft:oak_planks", "minecraft:spruce_planks"),
                tag.displayChoices(ignored -> List.of(
                        "minecraft:oak_planks", "minecraft:spruce_planks")));
        assertEquals(GuideIngredient.MAX_DISPLAY_CHOICES,
                tag.displayChoices(ignored -> IntStream.range(0, 100)
                        .mapToObj(index -> "example:item_" + index).toList()).size());
        assertFalse(recipe.ingredient(0, 2).containsTag());
    }
}
