package github.com.gengyoubo.tcmodern.tc4.recipe;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.tcmodern.api.recipe.TcRecipeDefinition;
import github.com.gengyoubo.tcmodern.api.recipe.TcRecipeDefinition.Ingredient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Tc4RecipeJsonTest {
    /** Modern items become a fixed original identity carrying their count and NBT. */
    private static final Tc4RecipeJson.HostItems HOST = (id, count, nbt) -> new LegacyStack("mfcl:host/" + id, 0, count, nbt == null ? "" : nbt);

    private static TcRecipeDefinition parse(String json) {
        return Tc4RecipeJson.parse(JsonParser.parseString(json).getAsJsonObject(), HOST);
    }

    @Test
    void shapedReadsPatternKeyAspectsWildcardAndMirroring() {
        var recipe = (TcRecipeDefinition.ArcaneShaped) parse("""
                {"type":"tc4modern:arcane_shaped","research":"BASICARTIFACE","pattern":["G G"," I "],
                 "key":{"G":{"ore":"blockGlass"},"I":{"legacy_item":"Thaumcraft:ItemResource","metadata":"*"}},
                 "aspects":{"aer":5,"ordo":3},"result":{"legacy_item":"Thaumcraft:ItemResource","metadata":2,"count":4},"mirrored":false}""");
        assertEquals(List.of("G G", " I "), recipe.pattern());
        assertEquals(new Ingredient.Ore("blockGlass"), recipe.key().get('G'));
        assertEquals(new Ingredient.Item(new LegacyStack("Thaumcraft:ItemResource", Tc4RecipeJson.WILDCARD, 1, "")), recipe.key().get('I'));
        assertEquals(Map.of("aer", 5, "ordo", 3), recipe.aspects());
        assertEquals(new LegacyStack("Thaumcraft:ItemResource", 2, 4, ""), recipe.result());
        assertFalse(recipe.mirrored());
    }

    @Test
    void shapelessCrucibleAndInfusionAcceptModernItems() {
        var shapeless = (TcRecipeDefinition.ArcaneShapeless) parse("""
                {"type":"tc4modern:arcane_shapeless","research":"R","ingredients":[{"item":"minecraft:amethyst_shard"},{"ore":"gemQuartz"}],
                 "result":{"item":"tc4modern:deepslate_cinnabar_ore","count":2,"nbt":"{display:{Name:'\\"x\\"'}}"}}""");
        assertEquals(new Ingredient.Item(new LegacyStack("mfcl:host/minecraft:amethyst_shard", 0, 1, "")), shapeless.ingredients().get(0));
        assertEquals(2, shapeless.result().count());
        assertTrue(shapeless.result().snbt().contains("display"));
        var crucible = (TcRecipeDefinition.Crucible) parse("""
                {"type":"tc4modern:crucible","research":"R","catalyst":{"ore":"ingotIron"},"aspects":{"ignis":4},
                 "result":{"legacy_item":"Thaumcraft:ItemNugget","metadata":16}}""");
        assertEquals(new Ingredient.Ore("ingotIron"), crucible.catalyst());
        var infusion = (TcRecipeDefinition.Infusion) parse("""
                {"type":"tc4modern:infusion","research":"R","input":{"legacy_item":"minecraft:diamond"},
                 "components":[{"legacy_item":"minecraft:gold_ingot"},{"item":"minecraft:echo_shard"}],"aspects":{"aer":16},
                 "instability":2,"result":{"legacy_item":"Thaumcraft:ItemResource","metadata":15}}""");
        assertEquals(2, infusion.components().size());
        assertEquals(2, infusion.instability());
    }

    @Test
    void malformedRecipesAreRejectedWithAReason() {
        assertThrows(JsonParseException.class, () -> parse("""
                {"type":"tc4modern:smelting","research":"R"}"""));
        assertThrows(JsonParseException.class, () -> parse("""
                {"type":"tc4modern:arcane_shapeless","ingredients":[{"ore":"x"}],"result":{"legacy_item":"a:b"}}"""));
        assertThrows(JsonParseException.class, () -> parse("""
                {"type":"tc4modern:arcane_shapeless","research":"R","ingredients":[{"ore":"x","legacy_item":"a:b"}],"result":{"legacy_item":"a:b"}}"""));
        assertThrows(JsonParseException.class, () -> parse("""
                {"type":"tc4modern:arcane_shapeless","research":"R","ingredients":[{"legacy_item":"a:b","count":2}],"result":{"legacy_item":"a:b"}}"""));
        assertThrows(JsonParseException.class, () -> parse("""
                {"type":"tc4modern:arcane_shapeless","research":"R","ingredients":[{"ore":"x"}],"result":{"legacy_item":"a:b","metadata":"*"}}"""));
        assertThrows(JsonParseException.class, () -> parse("""
                {"type":"tc4modern:arcane_shapeless","research":"R","ingredients":[{"ore":"x"}],"result":{"item":"a:b","metadata":1}}"""));
        assertThrows(JsonParseException.class, () -> parse("""
                {"type":"tc4modern:infusion","research":"R","input":{"legacy_item":"a:b"},"components":[{"ore":"x"}],"result":{"legacy_item":"a:b"}}"""));
        assertThrows(JsonParseException.class, () -> parse("""
                {"type":"tc4modern:arcane_shaped","research":"R","pattern":["GG"],"key":{"GG":{"ore":"x"}},"result":{"legacy_item":"a:b"}}"""));
        assertThrows(IllegalArgumentException.class, () -> parse("""
                {"type":"tc4modern:arcane_shaped","research":"R","pattern":["GX"],"key":{"G":{"ore":"x"}},"result":{"legacy_item":"a:b"}}"""));
    }
}
