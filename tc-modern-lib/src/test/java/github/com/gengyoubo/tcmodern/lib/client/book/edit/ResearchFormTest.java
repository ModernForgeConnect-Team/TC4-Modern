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
        form.name.set("en_us", "Desk");
        form.icon = "minecraft:diamond";
        form.aspects = "aer:2, ignis×1";
        form.flags.remove("secondary");
        form.pages.get(0).text.set("en_us", "Page one");
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
        assertEquals("Page one", json.getAsJsonArray("pages").get(0).getAsString());
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

    @Test void selectedItemDataSurvivesSavingAndTextEditsReplaceIt() {
        var form = ResearchForm.newResearch("MODERN", 0, 0);
        form.key = "PICKED";
        var picked = JsonParser.parseString("{\"item\":\"example:variant\",\"nbt\":\"{variant:3}\"}").getAsJsonObject();
        form.selectIcon(picked);
        var page = form.pages.get(0);
        page.type = "smelting";
        page.selectItem(picked);
        assertEquals(picked, form.json().getAsJsonObject("icon"));
        assertEquals(picked, form.json().getAsJsonArray("pages").get(0).getAsJsonObject().getAsJsonObject("smelting"));
        form.icon = "minecraft:book";
        assertFalse(form.json().getAsJsonObject("icon").has("nbt"));
    }

    @Test void editingAnyLanguagePreservesOtherTranslationsAndSharedKeys() {
        var form = ResearchForm.of(JsonParser.parseString(RESEARCH).getAsJsonObject());
        assertEquals("デスク", form.name.get("ja_jp"));
        form.name.set("ja_jp", "研究机");
        form.name.set("fr_fr", "Bureau");
        form.name.set("tok", "poki");
        form.name.set("zh_cn", "现代研究台");
        form.subtitle.set("ja_jp", form.subtitle.get("ja_jp"));
        var json = form.json();
        assertEquals("研究机", json.getAsJsonObject("name").get("ja_JP").getAsString());
        assertEquals("Bureau", json.getAsJsonObject("name").get("fr_FR").getAsString());
        assertEquals("poki", json.getAsJsonObject("name").get("tok").getAsString());
        assertEquals("现代研究台", json.getAsJsonObject("name").get("zh_CN").getAsString());
        assertEquals("tc.research_text.CUSTOM", json.get("subtitle").getAsString());
        form.name.set("fr_FR", "");
        assertFalse(form.json().getAsJsonObject("name").has("fr_FR"));
    }

    @Test void pageDraftAndTranslationKeyModePreserveInactiveTranslations() {
        var form = ResearchForm.of(JsonParser.parseString(RESEARCH).getAsJsonObject());
        var original = form.pages.get(1);
        var draft = original.copy();
        draft.text.setRawMode(true);
        draft.text.set("ja_jp", "example.book.page");
        assertEquals("example.book.page", draft.json().getAsJsonObject().get("text").getAsString());
        assertEquals("第二页", original.text.get("zh_cn"));
        draft.text.setRawMode(false);
        assertEquals("第二页", draft.text.get("zh_cn"));
        draft.text.setRawMode(true);
        assertEquals("example.book.page", draft.text.get("en_us"));
        draft.text.setRawMode(false);
        draft.text.set("ja_jp", "追加");
        assertEquals("追加", draft.json().getAsJsonObject().getAsJsonObject("text").get("ja_JP").getAsString());
        assertEquals("第二页", original.text.get("zh_cn"));
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
        category.name.set("zh_cn", "章节");
        var categoryJson = category.json();
        assertEquals("tc4modern:category", categoryJson.get("type").getAsString());
        assertEquals("章节", categoryJson.getAsJsonObject("name").get("zh_CN").getAsString());
        assertFalse(categoryJson.has("background"));
    }
}
