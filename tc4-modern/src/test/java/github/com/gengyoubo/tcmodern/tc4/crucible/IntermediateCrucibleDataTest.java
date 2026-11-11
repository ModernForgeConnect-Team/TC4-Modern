package github.com.gengyoubo.tcmodern.tc4.crucible;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IntermediateCrucibleDataTest {
    @Test void heatFollowsTheOriginalCrucible() {
        assertEquals(1, CrucibleRules.nextHeat(0, true, true));
        assertEquals(CrucibleRules.MAX_HEAT, CrucibleRules.nextHeat(CrucibleRules.MAX_HEAT, true, true));
        assertEquals(99, CrucibleRules.nextHeat(100, true, false));
        assertEquals(99, CrucibleRules.nextHeat(100, false, true), "no water, no heating");
        assertEquals(0, CrucibleRules.nextHeat(0, false, false));
        assertFalse(CrucibleRules.boiling(CrucibleRules.BOILING, 1000));
        assertTrue(CrucibleRules.boiling(CrucibleRules.BOILING + 1, 1));
        assertFalse(CrucibleRules.boiling(CrucibleRules.MAX_HEAT, 0));
        assertEquals(20 * 60, CrucibleRules.SYNTHESIS_TICKS);
    }

    @Test void liquidRisesWithWaterAndEssentia() {
        assertEquals(0.3F, CrucibleRules.liquidHeight(0, 0), 1e-6);
        assertEquals(0.8F, CrucibleRules.liquidHeight(1000, 0), 1e-6);
        assertEquals(0.9F, CrucibleRules.liquidHeight(1000, 50), 1e-6);
        assertEquals(0.9999F, CrucibleRules.liquidHeight(1000, 5000), 1e-6);
    }

    @Test void bundledRecipesNeedTheIntermediateCrucibleResearch() throws Exception {
        var research = json("/data/custom/tc4_research/intermediate_crucible.json");
        String key = research.get("key").getAsString();
        assertEquals("Metallic_fossils", research.getAsJsonArray("parents").get(0).getAsString());

        var device = json("/data/tc4modern/tc4_recipes/intermediate_crucible.json");
        assertEquals(key, device.get("research").getAsString());
        assertEquals("tc4modern:intermediate_crucible", device.getAsJsonObject("result").get("item").getAsString());

        var ingot = IntermediateRecipe.parse("tc4modern:ancient_thaumium_ingot", json("/data/tc4modern/tc4_intermediate_crucible/ancient_thaumium_ingot.json"));
        assertEquals(key, ingot.research());
        assertEquals(2, ingot.ingredients().size());
        assertEquals(Map.of("antiquitas", 2, "praecantatio", 4), ingot.aspects());
        assertEquals("tc4modern:ancient_thaumium_ingot", ingot.result().item());
    }

    private JsonObject json(String resource) throws Exception {
        try (var in = getClass().getResourceAsStream(resource)) {
            assertNotNull(in, resource);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
