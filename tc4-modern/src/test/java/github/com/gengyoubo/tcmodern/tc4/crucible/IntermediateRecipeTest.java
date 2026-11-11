package github.com.gengyoubo.tcmodern.tc4.crucible;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class IntermediateRecipeTest {
    private static IntermediateRecipe parse(String json) {
        return IntermediateRecipe.parse("test:recipe", JsonParser.parseString(json).getAsJsonObject());
    }

    @Test
    void shippedAncientThaumiumRecipeParses() throws Exception {
        try (var in = getClass().getResourceAsStream("/data/tc4modern/tc4_intermediate_crucible/ancient_thaumium_ingot.json")) {
            assertNotNull(in);
            var recipe = IntermediateRecipe.parse("tc4modern:ancient_thaumium_ingot",
                    JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
            assertEquals("intermediate_crucible", recipe.research());
            assertEquals(List.of(new IntermediateRecipe.Ingredient.Legacy("Thaumcraft:ItemResource", 2),
                    new IntermediateRecipe.Ingredient.Item("minecraft:netherite_ingot")), recipe.ingredients());
            assertEquals(Map.of("antiquitas", 2, "praecantatio", 4), recipe.aspects());
            assertEquals("tc4modern:ancient_thaumium_ingot", recipe.result().item());
            assertEquals(1, recipe.result().count());
        }
    }

    @Test
    void materialsMatchInAnyOrderAndExactly() {
        var recipe = parse("""
                {"type":"tc4modern:intermediate_crucible","research":"R","ingredients":[{"item":"a:x"},{"tag":"a:y"}],
                 "aspects":{"ordo":1},"result":{"item":"a:z"}}""");
        // Held materials are plain strings here; an ingredient accepts the string equal to its id.
        java.util.function.BiPredicate<IntermediateRecipe.Ingredient, String> accepts = (ingredient, held) ->
                ingredient instanceof IntermediateRecipe.Ingredient.Item item ? item.id().equals(held)
                        : ingredient instanceof IntermediateRecipe.Ingredient.Tag tag && tag.id().equals(held);
        assertTrue(recipe.matches(List.of("a:x", "a:y"), accepts));
        assertTrue(recipe.matches(List.of("a:y", "a:x"), accepts));
        assertFalse(recipe.matches(List.of("a:x"), accepts));
        assertFalse(recipe.matches(List.of("a:x", "a:x"), accepts));
        assertFalse(recipe.matches(List.of("a:x", "a:y", "a:y"), accepts));
    }

    @Test
    void malformedRecipesAreRejectedWithAReason() {
        assertThrows(JsonParseException.class, () -> parse("""
                {"type":"tc4modern:crucible","research":"R","ingredients":[{"item":"a:x"}],"aspects":{"ordo":1},"result":{"item":"a:z"}}"""));
        assertThrows(JsonParseException.class, () -> parse("""
                {"type":"tc4modern:intermediate_crucible","research":"R","ingredients":[{"item":"a:x"},{"item":"a:y"},{"item":"a:w"}],
                 "aspects":{"ordo":1},"result":{"item":"a:z"}}"""));
        assertThrows(JsonParseException.class, () -> parse("""
                {"type":"tc4modern:intermediate_crucible","research":"R","ingredients":[{"item":"a:x"}],"aspects":{},"result":{"item":"a:z"}}"""));
        assertThrows(JsonParseException.class, () -> parse("""
                {"type":"tc4modern:intermediate_crucible","research":"R","ingredients":[{"item":"a:x","tag":"a:y"}],
                 "aspects":{"ordo":1},"result":{"item":"a:z"}}"""));
        var wildcard = parse("""
                {"type":"tc4modern:intermediate_crucible","research":"R","ingredients":[{"legacy_item":"Thaumcraft:ItemShard","metadata":"*"}],
                 "aspects":{"ordo":1},"result":{"legacy_item":"Thaumcraft:ItemResource","metadata":2,"count":3}}""");
        assertEquals(IntermediateRecipe.WILDCARD, ((IntermediateRecipe.Ingredient.Legacy) wildcard.ingredients().get(0)).metadata());
        assertEquals(3, wildcard.result().count());
    }

    @Test
    void essentiaTakesRecipeCostsOnlyWhenComplete() {
        var essentia = new CrucibleEssentia();
        essentia.add(Map.of("antiquitas", 3, "praecantatio", 4));
        assertFalse(essentia.take(Map.of("antiquitas", 2, "praecantatio", 5)));
        assertEquals(7, essentia.total());
        assertTrue(essentia.take(Map.of("antiquitas", 2, "praecantatio", 4)));
        assertEquals(Map.of("antiquitas", 1), essentia.snapshot());
    }

    @Test
    void fluxSpillsOncePerTwoLostEssentiaAndAtLeastOnce() {
        assertEquals(1, CrucibleEssentia.fluxSpills(0));
        assertEquals(1, CrucibleEssentia.fluxSpills(3));
        assertEquals(110, CrucibleEssentia.fluxSpills(220));
    }

    @Test
    void boilOffLeavesAComponentOfACompoundAndFluxOfAPrimal() {
        var essentia = new CrucibleEssentia();
        essentia.add("metallum", 1);
        boolean flux = essentia.boilOff(new Random(1), tag -> tag.equals("metallum") ? List.of("terra", "ordo") : List.of());
        assertFalse(flux);
        assertEquals(1, essentia.total());
        assertTrue(essentia.amount("terra") == 1 || essentia.amount("ordo") == 1);
        var primal = new CrucibleEssentia();
        primal.add("ignis", 1);
        assertTrue(primal.boilOff(new Random(1), tag -> List.of()));
        assertTrue(primal.isEmpty());
    }
}
