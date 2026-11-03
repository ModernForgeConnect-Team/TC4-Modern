package github.com.gengyoubo.tcmodern.tc4.research;

import com.google.gson.JsonParser;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.tcmodern.lib.json.ResearchJson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResearchLocalizationTest {
    @Test void decompositionResearchAndRecipesHaveRequiredParents() throws Exception {
        var theory = json("/data/custom/tc4_research/modern_decomposition.json");
        assertEquals("modern_decomposition", theory.get("key").getAsString());
        var modern = json("/data/custom/tc4_research/1.json");
        assertEquals("modern_decomposition", modern.getAsJsonArray("parents").get(0).getAsString());
        for (String key : List.of("deconstruction_x", "deconstruction_y")) {
            var research = json("/data/custom/tc4_research/" + key + ".json");
            assertEquals("modern_decomposition", research.getAsJsonArray("parents").get(0).getAsString());
            var recipe = json("/data/tc4modern/tc4_recipes/" + key + ".json");
            assertEquals(key, recipe.get("research").getAsString());
            assertEquals("tc4modern:" + key, recipe.getAsJsonObject("result").get("item").getAsString());
        }
        var reactor = json("/data/custom/tc4_research/node_reactor.json");
        assertEquals("deconstruction_y", reactor.getAsJsonArray("parents").get(0).getAsString());
        assertEquals("node_reactor", json("/data/tc4modern/tc4_recipes/node_reactor.json").get("research").getAsString());
        var furnace = json("/data/custom/tc4_research/primal_furnace.json");
        assertEquals("node_reactor", furnace.getAsJsonArray("parents").get(0).getAsString());
        assertEquals("primal_furnace", json("/data/tc4modern/tc4_recipes/primal_furnace.json").get("research").getAsString());
    }

    private com.google.gson.JsonObject json(String resource) throws Exception {
        try (var in = getClass().getResourceAsStream(resource)) {
            assertNotNull(in, resource);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test void builtInResearchPublishesChineseAndEnglishUnderOriginalTranslationKeys() throws Exception {
        var expected = Map.of(
                "category_modern", List.of("tc.research_category.Modern"),
                "modern_decomposition", List.of("tc.research_name.modern_decomposition", "tc.research_text.modern_decomposition", "tc.research_page.modern_decomposition.1"),
                "deconstruction_x", List.of("tc.research_name.deconstruction_x", "tc.research_text.deconstruction_x", "tc.research_page.deconstruction_x.1"),
                "deconstruction_y", List.of("tc.research_name.deconstruction_y", "tc.research_text.deconstruction_y", "tc.research_page.deconstruction_y.1"),
                "node_reactor", List.of("tc.research_name.node_reactor", "tc.research_text.node_reactor", "tc.research_page.node_reactor.1"),
                "primal_furnace", List.of("tc.research_name.primal_furnace", "tc.research_text.primal_furnace", "tc.research_page.primal_furnace.1"),
                "1", List.of("tc.research_name.1", "tc.research_text.1", "tc.research_page.1.1", "tc.research_page.1.2"),
                "modern1", List.of("tc.research_name.modern1", "tc.research_text.modern1", "tc.research_page.modern1.1"),
                "modern2", List.of("tc.research_name.modern2", "tc.research_text.modern2", "tc.research_page.modern2.1"),
                "modern3", List.of("tc.research_name.modern3", "tc.research_text.modern3", "tc.research_page.modern3.1"));
        for (var entry : expected.entrySet()) {
            String resource = "/data/custom/tc4_research/" + entry.getKey() + ".json";
            try (var in = getClass().getResourceAsStream(resource)) {
                assertNotNull(in, resource);
                var json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                var parsed = ResearchJson.parse(json, (id, count, nbt) -> new LegacyStack("host:" + id, 0, count, nbt == null ? "" : nbt));
                for (String key : entry.getValue()) {
                    assertTrue(parsed.texts().get("zh_CN").containsKey(key), key);
                    assertTrue(parsed.texts().get("en_US").containsKey(key), key);
                    if (!key.equals("tc.research_text.1"))
                        assertNotEquals(parsed.texts().get("zh_CN").get(key), parsed.texts().get("en_US").get(key), key);
                }
            }
        }
    }
}
