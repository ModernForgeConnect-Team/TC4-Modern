package github.com.gengyoubo.tcmodern.lib.json;

import com.google.gson.*;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.tcmodern.api.research.TcResearchDefinition;
import github.com.gengyoubo.tcmodern.api.research.TcResearchEntry;
import java.util.*;

/**
 * The {@code tc4_research} JSON format: one file is one category ({@code "type": "tc4modern:category"}) or one
 * research ({@code "type": "tc4modern:research"}). Names, subtitles and page text are either an original language
 * key (or literal text) or an object of texts by language ({@code {"en_US": "...", "zh_CN": "..."}}); inline texts
 * are published under the original's own keys. Items use the {@code tc4_recipes} item format.
 */
public final class ResearchJson {
    /** A parsed file: what to register and the texts it brings, by original language and key. */
    public record Parsed(TcResearchDefinition.Category category, TcResearchDefinition research,
                         Map<String, Map<String, String>> texts) {
        public String key() { return category != null ? category.key() : research.key(); }
    }

    private static final Map<String, TcResearchEntry.Flag> FLAGS = Map.of(
            "special", TcResearchEntry.Flag.SPECIAL, "secondary", TcResearchEntry.Flag.SECONDARY,
            "round", TcResearchEntry.Flag.ROUND, "stub", TcResearchEntry.Flag.STUB, "virtual", TcResearchEntry.Flag.VIRTUAL,
            "concealed", TcResearchEntry.Flag.CONCEALED, "hidden", TcResearchEntry.Flag.HIDDEN, "lost", TcResearchEntry.Flag.LOST,
            "auto_unlock", TcResearchEntry.Flag.AUTO_UNLOCK);
    private static final Map<String, TcResearchDefinition.RecipeKind> RECIPES = Map.of(
            "arcane", TcResearchDefinition.RecipeKind.ARCANE, "crucible", TcResearchDefinition.RecipeKind.CRUCIBLE,
            "infusion", TcResearchDefinition.RecipeKind.INFUSION, "crafting", TcResearchDefinition.RecipeKind.CRAFTING);

    private ResearchJson() { }

    public static Parsed parse(JsonObject json, RecipeJson.HostItems hostItems) {
        var texts = new LinkedHashMap<String, Map<String, String>>();
        String type = string(json, "type");
        String key = string(json, "key");
        if (!key.matches("[A-Za-z0-9_.@-]+")) throw new JsonParseException("Invalid key '" + key + "': letters, digits, _ . - only");
        if (type.equals("tc4modern:category")) {
            if (json.has("name")) text(json.get("name"), "tc.research_category." + key, texts);
            var icon = string(json, "icon");
            var background = json.has("background") ? string(json, "background") : null;
            return new Parsed(new TcResearchDefinition.Category(key, location(icon, "icon"),
                    background == null ? null : location(background, "background")), null, texts);
        }
        if (!type.equals("tc4modern:research")) throw new JsonParseException("Unknown type: " + type + " (tc4modern:category or tc4modern:research)");

        var flags = EnumSet.noneOf(TcResearchEntry.Flag.class);
        for (var flag : strings(json, "flags")) {
            var value = FLAGS.get(flag.toLowerCase(Locale.ROOT));
            if (value == null) throw new JsonParseException("Unknown flag '" + flag + "', expected one of " + new TreeSet<>(FLAGS.keySet()));
            flags.add(value);
        }
        boolean virtual = flags.contains(TcResearchEntry.Flag.VIRTUAL);
        int column = 0, row = 0;
        if (json.has("position")) {
            var position = array(json, "position");
            if (position.size() != 2) throw new JsonParseException("'position' is [column, row]");
            column = position.get(0).getAsInt();
            row = position.get(1).getAsInt();
        } else if (!virtual) throw new JsonParseException("Missing 'position' ([column, row])");
        LegacyStack iconItem = null;
        String iconTexture = "";
        if (json.has("icon")) {
            var icon = object(json, "icon");
            if (icon.has("texture")) iconTexture = location(string(icon, "texture"), "icon texture");
            else iconItem = RecipeJson.stack(icon, hostItems, true);
        } else if (!virtual) throw new JsonParseException("Missing 'icon' ({\"item\": ...}, {\"legacy_item\": ...} or {\"texture\": ...})");

        var triggerItems = new ArrayList<LegacyStack>();
        var triggerEntities = new ArrayList<String>();
        var triggerAspects = new ArrayList<String>();
        if (json.has("triggers")) {
            var triggers = object(json, "triggers");
            if (triggers.has("items")) for (var item : array(triggers, "items")) triggerItems.add(RecipeJson.stack(asObject(item, "trigger item"), hostItems, true));
            triggerEntities.addAll(strings(triggers, "entities"));
            triggerAspects.addAll(strings(triggers, "aspects"));
        }

        if (json.has("name")) text(json.get("name"), "tc.research_name." + key, texts);
        if (json.has("subtitle")) text(json.get("subtitle"), "tc.research_text." + key, texts);
        var pages = new ArrayList<TcResearchDefinition.Page>();
        if (json.has("pages")) {
            int number = 0;
            for (var value : array(json, "pages")) {
                number++;
                pages.add(page(value, key + "." + number, hostItems, texts));
            }
        }
        int complexity = json.has("complexity") ? json.get("complexity").getAsInt() : 1;
        int warp = json.has("warp") ? json.get("warp").getAsInt() : 0;
        try {
            return new Parsed(null, new TcResearchDefinition(key, string(json, "category"), column, row, complexity, iconItem, iconTexture,
                    aspects(json, "aspects"), strings(json, "parents"), strings(json, "hidden_parents"), strings(json, "siblings"),
                    flags, triggerItems, triggerEntities, triggerAspects, warp, pages), texts);
        } catch (IllegalArgumentException invalid) {
            throw new JsonParseException(invalid.getMessage(), invalid);
        }
    }

    /** A page: a string is a text page; otherwise exactly one of the page keys below. */
    private static TcResearchDefinition.Page page(JsonElement value, String id, RecipeJson.HostItems hostItems,
                                                  Map<String, Map<String, String>> texts) {
        if (value.isJsonPrimitive()) return new TcResearchDefinition.Text(value.getAsString(), "");
        var page = asObject(value, "page");
        var kinds = new ArrayList<String>();
        for (String kind : List.of("text", "image", "aspects", "arcane", "crucible", "infusion", "crafting", "smelting"))
            if (page.has(kind)) kinds.add(kind);
        if (kinds.size() != 1) throw new JsonParseException("Page " + id + " needs exactly one of text, image, aspects, arcane, crucible, infusion, crafting, smelting");
        String kind = kinds.get(0);
        return switch (kind) {
            case "text" -> new TcResearchDefinition.Text(text(page.get("text"), "tc.research_page." + id, texts),
                    page.has("requires") ? string(page, "requires") : "");
            case "image" -> new TcResearchDefinition.Image(location(string(page, "image"), "image"),
                    page.has("caption") ? text(page.get("caption"), "tc.research_page." + id + ".caption", texts) : "");
            case "aspects" -> new TcResearchDefinition.Aspects(aspects(page, "aspects"));
            case "smelting" -> new TcResearchDefinition.Smelting(RecipeJson.stack(object(page, "smelting"), hostItems, false));
            default -> new TcResearchDefinition.Recipe(RECIPES.get(kind), RecipeJson.stack(object(page, kind), hostItems, true));
        };
    }

    /**
     * A text field: a string is used as it is (a key, or literal text); an object of texts by language is published
     * under {@code key}, which is returned. The first language given also fills English when English is missing.
     */
    private static String text(JsonElement value, String key, Map<String, Map<String, String>> texts) {
        if (value.isJsonPrimitive()) return value.getAsString();
        var byLanguage = asObject(value, key);
        if (byLanguage.size() == 0) throw new JsonParseException("Empty translations for " + key);
        String first = null;
        for (var entry : byLanguage.entrySet()) {
            String language = language(entry.getKey());
            String text = entry.getValue().getAsString();
            texts.computeIfAbsent(language, unused -> new LinkedHashMap<>()).put(key, text);
            if (first == null) first = text;
        }
        texts.computeIfAbsent("en_US", unused -> new LinkedHashMap<>()).putIfAbsent(key, first);
        return key;
    }

    /** {@code zh_cn} and {@code zh_CN} both become the original's {@code zh_CN}. */
    static String language(String code) {
        int underscore = code.indexOf('_');
        if (underscore <= 0 || underscore == code.length() - 1) throw new JsonParseException("Language codes look like en_US: " + code);
        return code.substring(0, underscore).toLowerCase(Locale.ROOT) + "_" + code.substring(underscore + 1).toUpperCase(Locale.ROOT);
    }

    private static String location(String value, String what) {
        int colon = value.indexOf(':');
        if (colon <= 0 || colon == value.length() - 1) throw new JsonParseException(what + " must be namespace:path, got " + value);
        return value;
    }

    private static Map<String, Integer> aspects(JsonObject json, String name) {
        var aspects = new LinkedHashMap<String, Integer>();
        if (json.has(name)) for (var entry : object(json, name).entrySet()) aspects.put(entry.getKey(), entry.getValue().getAsInt());
        return aspects;
    }

    private static List<String> strings(JsonObject json, String name) {
        if (!json.has(name)) return List.of();
        var result = new ArrayList<String>();
        for (var value : array(json, name)) result.add(value.getAsString());
        return result;
    }

    private static String string(JsonObject json, String name) {
        if (!json.has(name)) throw new JsonParseException("Missing '" + name + "'");
        var value = json.get(name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new JsonParseException("'" + name + "' must be a string");
        return value.getAsString();
    }

    private static JsonObject object(JsonObject json, String name) {
        if (!json.has(name)) throw new JsonParseException("Missing '" + name + "'");
        return asObject(json.get(name), name);
    }

    private static JsonObject asObject(JsonElement value, String name) {
        if (!value.isJsonObject()) throw new JsonParseException("'" + name + "' must be an object");
        return value.getAsJsonObject();
    }

    private static List<JsonElement> array(JsonObject json, String name) {
        var value = json.get(name);
        if (value == null || !value.isJsonArray()) throw new JsonParseException("'" + name + "' must be an array");
        var result = new ArrayList<JsonElement>();
        value.getAsJsonArray().forEach(result::add);
        return result;
    }
}
