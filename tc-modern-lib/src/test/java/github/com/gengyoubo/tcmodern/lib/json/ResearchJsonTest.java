package github.com.gengyoubo.tcmodern.lib.json;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.tcmodern.api.research.TcResearchDefinition;
import github.com.gengyoubo.tcmodern.api.research.TcResearchEntry;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResearchJsonTest {
    /** Modern ids become a fake original key so tests need no game. */
    private static final github.com.gengyoubo.tcmodern.lib.json.RecipeJson.HostItems HOST =
            (id, count, nbt) -> new LegacyStack("host:" + id, 0, count, nbt == null ? "" : nbt);

    private static ResearchJson.Parsed parse(String json) { return ResearchJson.parse(JsonParser.parseString(json).getAsJsonObject(), HOST); }

    @Test void categoryWithInlineName() {
        var parsed = parse("""
                {"type": "tc4modern:category", "key": "MODERN", "icon": "tc4modern:textures/research/modern.png",
                 "name": {"zh_cn": "现代研究", "en_US": "Modern research"}}""");
        assertEquals("MODERN", parsed.category().key());
        assertEquals("thaumcraft:textures/gui/gui_researchback.png", parsed.category().background());
        assertEquals("现代研究", parsed.texts().get("zh_CN").get("tc.research_category.MODERN"));
        assertEquals("Modern research", parsed.texts().get("en_US").get("tc.research_category.MODERN"));
    }

    @Test void researchWithEveryPageKind() {
        var parsed = parse("""
                {"type": "tc4modern:research", "key": "DESK", "category": "MODERN", "position": [2, -1], "complexity": 2,
                 "icon": {"item": "tc4modern:research_desk"}, "aspects": {"cognitio": 3, "instrumentum": 2},
                 "parents": ["RESEARCH"], "hidden_parents": ["NITOR"], "siblings": ["CABINET"],
                 "flags": ["secondary", "Concealed"], "warp": 1,
                 "triggers": {"items": [{"legacy_item": "Thaumcraft:ItemResource", "metadata": 3}], "entities": ["Thaumcraft.Firebat"], "aspects": ["ignis"]},
                 "name": {"zh_CN": "现代研究台"}, "subtitle": "tc.research_text.CUSTOM",
                 "pages": ["tc.research_page.DESK.1",
                           {"text": {"zh_CN": "第二页<BR>继续"}, "requires": "CABINET"},
                           {"image": "tc4modern:textures/research/desk.png", "caption": {"en_US": "A desk"}},
                           {"aspects": {"cognitio": 1}},
                           {"arcane": {"item": "tc4modern:research_desk"}},
                           {"crucible": {"legacy_item": "Thaumcraft:ItemResource", "metadata": 1}},
                           {"infusion": {"item": "minecraft:diamond"}},
                           {"crafting": {"item": "tc4modern:archive_cabinet"}},
                           {"smelting": {"legacy_item": "Thaumcraft:blockCustomOre", "metadata": "*"}}]}""");
        var research = parsed.research();
        assertEquals("DESK", research.key());
        assertEquals(2, research.column());
        assertEquals(-1, research.row());
        assertEquals(2, research.complexity());
        assertEquals("host:tc4modern:research_desk", research.iconItem().key());
        assertEquals(Map.of("cognitio", 3, "instrumentum", 2), research.aspects());
        assertEquals(EnumSet.of(TcResearchEntry.Flag.SECONDARY, TcResearchEntry.Flag.CONCEALED), research.flags());
        assertEquals(List.of("NITOR"), research.hiddenParents());
        assertEquals(1, research.warp());
        assertEquals(3, research.itemTriggers().get(0).metadata());
        assertEquals(List.of("ignis"), research.aspectTriggers());
        var pages = research.pages();
        assertEquals(9, pages.size());
        assertEquals(new TcResearchDefinition.Text("tc.research_page.DESK.1", ""), pages.get(0));
        assertEquals(new TcResearchDefinition.Text("tc.research_page.DESK.2", "CABINET"), pages.get(1));
        assertInstanceOf(TcResearchDefinition.Image.class, pages.get(2));
        assertEquals(TcResearchDefinition.RecipeKind.ARCANE, ((TcResearchDefinition.Recipe) pages.get(4)).kind());
        assertEquals(TcResearchDefinition.RecipeKind.CRAFTING, ((TcResearchDefinition.Recipe) pages.get(7)).kind());
        assertEquals(32767, ((TcResearchDefinition.Smelting) pages.get(8)).input().metadata());
        // Inline texts are published under the original keys; Chinese-only texts also fill English.
        assertEquals("现代研究台", parsed.texts().get("zh_CN").get("tc.research_name.DESK"));
        assertEquals("现代研究台", parsed.texts().get("en_US").get("tc.research_name.DESK"));
        assertEquals("第二页<BR>继续", parsed.texts().get("zh_CN").get("tc.research_page.DESK.2"));
        assertEquals("A desk", parsed.texts().get("en_US").get("tc.research_page.DESK.3.caption"));
        assertFalse(parsed.texts().get("en_US").containsKey("tc.research_text.DESK"));
    }

    @Test void textureIconsAndVirtualResearch() {
        var textured = parse("""
                {"type": "tc4modern:research", "key": "T", "category": "C", "position": [0, 0],
                 "icon": {"texture": "tc4modern:textures/research/t.png"}, "aspects": {"ordo": 1}}""").research();
        assertNull(textured.iconItem());
        assertEquals("tc4modern:textures/research/t.png", textured.iconTexture());
        var virtual = parse("""
                {"type": "tc4modern:research", "key": "V", "category": "C", "flags": ["virtual", "auto_unlock"]}""").research();
        assertTrue(virtual.flags().contains(TcResearchEntry.Flag.VIRTUAL));
        assertTrue(virtual.aspects().isEmpty());
    }

    @Test void mistakesAreReportedWithTheirCause() {
        assertMessage("Missing 'position'", """
                {"type": "tc4modern:research", "key": "A", "category": "C", "icon": {"item": "x:y"}, "aspects": {"ordo": 1}}""");
        assertMessage("Unknown flag 'shiny'", """
                {"type": "tc4modern:research", "key": "A", "category": "C", "position": [0, 0], "icon": {"item": "x:y"},
                 "aspects": {"ordo": 1}, "flags": ["shiny"]}""");
        assertMessage("at least one aspect", """
                {"type": "tc4modern:research", "key": "A", "category": "C", "position": [0, 0], "icon": {"item": "x:y"}}""");
        assertMessage("exactly one of text, image", """
                {"type": "tc4modern:research", "key": "A", "category": "C", "position": [0, 0], "icon": {"item": "x:y"},
                 "aspects": {"ordo": 1}, "pages": [{"text": "a", "image": "x:y"}]}""");
        assertMessage("complexity is 1 to 3", """
                {"type": "tc4modern:research", "key": "A", "category": "C", "position": [0, 0], "icon": {"item": "x:y"},
                 "aspects": {"ordo": 1}, "complexity": 4}""");
        assertMessage("Unknown type", """
                {"type": "tc4modern:quest", "key": "A"}""");
        assertMessage("Invalid key", """
                {"type": "tc4modern:category", "key": "has space", "icon": "x:y"}""");
        assertMessage("namespace:path", """
                {"type": "tc4modern:category", "key": "C", "icon": "no-namespace"}""");
    }

    private static void assertMessage(String expected, String json) {
        var failure = assertThrows(JsonParseException.class, () -> parse(json));
        assertTrue(failure.getMessage().contains(expected), "Expected '" + expected + "' in: " + failure.getMessage());
    }
}
