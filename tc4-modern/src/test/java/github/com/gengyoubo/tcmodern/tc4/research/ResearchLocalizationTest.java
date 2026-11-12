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

    @Test void ancientDebrisCrucibleRecipeRequiresTheBuiltInMetallicFossilsResearch() throws Exception {
        var research = json("/data/custom/tc4_research/metallic_fossils.json");
        var parsed = ResearchJson.parse(research, (id, count, nbt) -> new LegacyStack("host:" + id, 0, count, nbt == null ? "" : nbt));
        for (String key : List.of("tc.research_name.Metallic_fossils", "tc.research_text.Metallic_fossils", "tc.research_page.Metallic_fossils.1")) {
            assertFalse(parsed.texts().get("zh_CN").getOrDefault(key, "").isBlank(), key);
            assertFalse(parsed.texts().get("en_US").getOrDefault(key, "").isBlank(), key);
            assertNotEquals(parsed.texts().get("zh_CN").get(key), parsed.texts().get("en_US").get(key), key);
        }
        var pages = research.getAsJsonArray("pages");
        assertEquals(2, pages.size());
        assertEquals("minecraft:ancient_debris", pages.get(1).getAsJsonObject().getAsJsonObject("crucible").get("item").getAsString());
        var recipe = json("/data/tc4modern/tc4_recipes/ancient_debris.json");
        assertEquals("tc4modern:crucible", recipe.get("type").getAsString());
        assertEquals(research.get("key").getAsString(), recipe.get("research").getAsString());
        assertEquals("minecraft:crying_obsidian", recipe.getAsJsonObject("catalyst").get("item").getAsString());
        assertEquals("minecraft:ancient_debris", recipe.getAsJsonObject("result").get("item").getAsString());
        int total = 0;
        for (var amount : recipe.getAsJsonObject("aspects").entrySet()) total += amount.getValue().getAsInt();
        // Above TC4's original 100-essentia crucible; brewable only with CrucibleCapacity.
        assertEquals(220, total);
    }

    @Test void elytraInfusionNeedsTheWhyBirdsFlyResearch() throws Exception {
        var research = json("/data/custom/tc4_research/why_birds_fly.json");
        var parsed = ResearchJson.parse(research, (id, count, nbt) -> new LegacyStack("host:" + id, 0, count, nbt == null ? "" : nbt));
        for (String key : List.of("tc.research_name.why_birds_fly", "tc.research_text.why_birds_fly", "tc.research_page.why_birds_fly.1")) {
            assertFalse(parsed.texts().get("zh_CN").getOrDefault(key, "").isBlank(), key);
            assertFalse(parsed.texts().get("en_US").getOrDefault(key, "").isBlank(), key);
            assertNotEquals(parsed.texts().get("zh_CN").get(key), parsed.texts().get("en_US").get(key), key);
        }
        assertEquals("INFUSION", research.getAsJsonArray("parents").get(0).getAsString());
        assertEquals("minecraft:elytra", research.getAsJsonArray("pages").get(1).getAsJsonObject().getAsJsonObject("infusion").get("item").getAsString());
        var recipe = json("/data/tc4modern/tc4_recipes/elytra.json");
        assertEquals("tc4modern:infusion", recipe.get("type").getAsString());
        assertEquals(research.get("key").getAsString(), recipe.get("research").getAsString());
        assertEquals("minecraft:leather_chestplate", recipe.getAsJsonObject("input").get("item").getAsString());
        var components = recipe.getAsJsonArray("components");
        assertEquals(8, components.size());
        for (var component : components) assertEquals("minecraft:phantom_membrane", component.getAsJsonObject().get("item").getAsString());
        assertEquals(Map.of("iter", 30, "tutamen", 5, "volatus", 16), Map.of(
                "iter", recipe.getAsJsonObject("aspects").get("iter").getAsInt(),
                "tutamen", recipe.getAsJsonObject("aspects").get("tutamen").getAsInt(),
                "volatus", recipe.getAsJsonObject("aspects").get("volatus").getAsInt()));
        assertEquals(3, recipe.getAsJsonObject("aspects").size());
        assertEquals("minecraft:elytra", recipe.getAsJsonObject("result").get("item").getAsString());
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
