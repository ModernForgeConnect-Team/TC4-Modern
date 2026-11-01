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
    @Test void builtInResearchPublishesChineseAndEnglishUnderOriginalTranslationKeys() throws Exception {
        var expected = Map.of(
                "category_modern", List.of("tc.research_category.Modern"),
                "1", List.of("tc.research_name.1", "tc.research_text.1", "tc.research_page.1.1", "tc.research_page.1.2"),
                "modern1", List.of("tc.research_name.modern1", "tc.research_text.modern1", "tc.research_page.modern1.1"),
                "modern2", List.of("tc.research_name.modern2", "tc.research_text.modern2", "tc.research_page.modern2.1"));
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
