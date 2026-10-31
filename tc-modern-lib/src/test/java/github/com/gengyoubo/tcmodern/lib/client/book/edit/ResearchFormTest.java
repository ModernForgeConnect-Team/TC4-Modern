package github.com.gengyoubo.tcmodern.lib.client.book.edit;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResearchFormTest {
    private static final String RESEARCH = """
            {"type": "tc4modern:research", "key": "DESK", "category": "MODERN", "position": [2, -1], "complexity": 2,
             "icon": {"legacy_item": "Thaumcraft:ItemResource", "metadata": 3, "nbt": "{a:1b}"}, "aspects": {"cognitio": 3, "ordo": 1},
             "parents": ["RESEARCH"], "flags": ["secondary"], "warp": 1,
             "triggers": {"entities": ["Thaumcraft.Firebat"]}, "custom": 7,
             "name": {"zh_CN": "现代研究台", "ja_JP": "デスク"}, "subtitle": "tc.research_text.CUSTOM",
             "pages": ["tc.research_page.DESK.1", {"text": {"zh_CN": "第二页"}, "requires": "NITOR"},
                       {"arcane": {"item": "tc4modern:research_desk"}}, {"aspects": {"ignis": 2}}]}""";

    @Test void unchangedFormWritesTheSameJson() {
        var original = JsonParser.parseString(RESEARCH).getAsJsonObject();
        assertEquals(original, ResearchForm.of(original).json());
    }

    @Test void editsChangeOnlyTheirFields() {
        var form = ResearchForm.of(JsonParser.parseString(RESEARCH).getAsJsonObject());
        assertEquals("Thaumcraft:ItemResource@3", form.icon);
        assertEquals("cognitio:3 ordo:1", form.aspects);
        assertTrue(form.subtitle.isRaw());
        form.column = 5;
        form.name.en = "Desk";
        form.icon = "minecraft:diamond";
        form.aspects = "aer:2, ignis×1";
        form.flags.remove("secondary");
        form.pages.get(0).text.en = "Page one";
        form.pages.remove(3);
        var json = form.json();
        assertEquals(5, json.getAsJsonArray("position").get(0).getAsInt());
        assertEquals("Desk", json.getAsJsonObject("name").get("en_US").getAsString());
        assertEquals("デスク", json.getAsJsonObject("name").get("ja_JP").getAsString());
        assertEquals("minecraft:diamond", json.getAsJsonObject("icon").get("item").getAsString());
        assertEquals(2, json.getAsJsonObject("aspects").get("aer").getAsInt());
        assertEquals(1, json.getAsJsonObject("aspects").get("ignis").getAsInt());
        assertFalse(json.has("flags"));
        assertEquals(7, json.get("custom").getAsInt());
        assertTrue(json.has("triggers"));
        var first = json.getAsJsonArray("pages").get(0).getAsJsonObject().getAsJsonObject("text");
        assertEquals("tc.research_page.DESK.1", first.get("zh_CN").getAsString());
        assertEquals("Page one", first.get("en_US").getAsString());
        assertEquals(3, json.getAsJsonArray("pages").size());
    }

    @Test void itemSyntax() {
        assertEquals("minecraft:chest", ResearchForm.itemJson("minecraft:chest", null).get("item").getAsString());
        var legacy = ResearchForm.itemJson("Thaumcraft:WandCap@1", null);
        assertEquals("Thaumcraft:WandCap", legacy.get("legacy_item").getAsString());
        assertEquals(1, legacy.get("metadata").getAsInt());
        assertEquals("Thaumcraft:ItemThaumonomicon", ResearchForm.itemJson("Thaumcraft:ItemThaumonomicon", null).get("legacy_item").getAsString());
        assertEquals("*", ResearchForm.itemJson("minecraft:wool@*", null).get("metadata").getAsString());
        assertEquals("tc4modern:textures/x.png", ResearchForm.itemJson("texture:tc4modern:textures/x.png", null).get("texture").getAsString());
        assertThrows(RuntimeException.class, () -> ResearchForm.aspectsJson("cognitio"));
    }

    @Test void newResearchAndCategoryAreValidShapes() {
        var research = ResearchForm.newResearch("MODERN", 3, 4);
        research.key = "NEW";
        var json = research.json();
        assertEquals("tc4modern:research", json.get("type").getAsString());
        assertEquals("MODERN", json.get("category").getAsString());
        assertEquals(4, json.getAsJsonArray("position").get(1).getAsInt());
        assertEquals(1, json.getAsJsonArray("pages").size());
        var category = ResearchForm.newCategory();
        category.key = "CAT";
        category.name.zh = "章节";
        var categoryJson = category.json();
        assertEquals("tc4modern:category", categoryJson.get("type").getAsString());
        assertEquals("章节", categoryJson.getAsJsonObject("name").get("zh_CN").getAsString());
        assertFalse(categoryJson.has("background"));
    }
}
