package github.com.gengyoubo.tcmodern.lib.client.book.edit;

import com.google.gson.*;
import java.util.*;

/**
 * The editor's view of one {@code tc4_research} file: plain text fields that convert back to the same JSON. What the
 * form does not show (triggers, NBT, other languages, unknown keys) is kept from the original object.
 */
public final class ResearchForm {
    public static final List<String> FLAGS = List.of("secondary", "concealed", "hidden", "lost", "round", "special", "stub", "auto_unlock", "virtual");
    public static final List<String> PAGE_TYPES = List.of("text", "image", "aspects", "arcane", "crucible", "infusion", "crafting", "smelting");

    /** A translatable text: Chinese and English, or a plain key or literal kept as it was. */
    public static final class Text {
        public String zh = "", en = "";
        private String raw;
        private final JsonObject others = new JsonObject();

        static Text of(JsonElement value) {
            var text = new Text();
            if (value == null || value.isJsonNull()) return text;
            if (value.isJsonPrimitive()) { text.raw = value.getAsString(); text.zh = text.raw; return text; }
            for (var entry : value.getAsJsonObject().entrySet()) {
                String language = entry.getKey().toLowerCase(Locale.ROOT);
                if (language.equals("zh_cn")) text.zh = entry.getValue().getAsString();
                else if (language.equals("en_us")) text.en = entry.getValue().getAsString();
                else text.others.add(entry.getKey(), entry.getValue());
            }
            return text;
        }

        /** Null when empty; the original key or literal when it was not edited. */
        JsonElement json() {
            if (raw != null && zh.equals(raw) && en.isEmpty() && others.size() == 0) return new JsonPrimitive(raw);
            if (zh.isEmpty() && en.isEmpty() && others.size() == 0) return null;
            var result = new JsonObject();
            if (!zh.isEmpty()) result.addProperty("zh_CN", zh);
            if (!en.isEmpty()) result.addProperty("en_US", en);
            others.entrySet().forEach(entry -> result.add(entry.getKey(), entry.getValue()));
            return result;
        }

        /** A plain key or literal rather than inline translations. */
        public boolean isRaw() { return raw != null && zh.equals(raw) && en.isEmpty(); }
    }

    public static final class Page {
        public String type = "text";
        public Text text = new Text(), caption = new Text();
        public String requires = "", image = "", aspects = "", item = "";
        private JsonObject itemOriginal;

        static Page of(JsonElement value) {
            var page = new Page();
            if (value.isJsonPrimitive()) { page.text = Text.of(value); return page; }
            var json = value.getAsJsonObject();
            for (String type : PAGE_TYPES) if (json.has(type)) { page.type = type; break; }
            switch (page.type) {
                case "text" -> {
                    page.text = Text.of(json.get("text"));
                    if (json.has("requires")) page.requires = json.get("requires").getAsString();
                }
                case "image" -> {
                    page.image = json.get("image").getAsString();
                    page.caption = Text.of(json.get("caption"));
                }
                case "aspects" -> page.aspects = aspectsText(json.getAsJsonObject("aspects"));
                default -> {
                    page.itemOriginal = json.getAsJsonObject(page.type);
                    page.item = itemText(page.itemOriginal);
                }
            }
            return page;
        }

        JsonElement json() {
            var result = new JsonObject();
            switch (type) {
                case "text" -> {
                    var value = text.json();
                    // A bare key or literal with no condition keeps the original string shorthand.
                    if (value != null && value.isJsonPrimitive() && requires.isBlank()) return value;
                    result.add("text", value == null ? new JsonPrimitive("") : value);
                    if (!requires.isBlank()) result.addProperty("requires", requires.strip());
                }
                case "image" -> {
                    result.addProperty("image", image.strip());
                    var value = caption.json();
                    if (value != null) result.add("caption", value);
                }
                case "aspects" -> result.add("aspects", aspectsJson(aspects));
                default -> result.add(type, itemJson(item, itemOriginal));
            }
            return result;
        }
    }

    private final JsonObject original;
    public final boolean category;
    public String key = "", categoryKey = "", icon = "", background = "", aspects = "", parents = "", hiddenParents = "", siblings = "";
    public int column, row, complexity = 1, warp;
    public Text name = new Text(), subtitle = new Text();
    public final Set<String> flags = new LinkedHashSet<>();
    public final List<Page> pages = new ArrayList<>();
    private JsonObject iconOriginal;

    private ResearchForm(JsonObject original, boolean category) {
        this.original = original;
        this.category = category;
    }

    /** A form over an existing file. */
    public static ResearchForm of(JsonObject json) {
        boolean category = "tc4modern:category".equals(string(json, "type"));
        var form = new ResearchForm(json.deepCopy(), category);
        form.key = string(json, "key");
        form.name = Text.of(json.get("name"));
        if (category) {
            form.icon = string(json, "icon");
            form.background = string(json, "background");
            return form;
        }
        form.categoryKey = string(json, "category");
        if (json.has("position")) {
            var position = json.getAsJsonArray("position");
            form.column = position.get(0).getAsInt();
            form.row = position.get(1).getAsInt();
        }
        if (json.has("complexity")) form.complexity = json.get("complexity").getAsInt();
        if (json.has("warp")) form.warp = json.get("warp").getAsInt();
        if (json.has("icon")) { form.iconOriginal = json.getAsJsonObject("icon"); form.icon = itemText(form.iconOriginal); }
        form.subtitle = Text.of(json.get("subtitle"));
        if (json.has("aspects")) form.aspects = aspectsText(json.getAsJsonObject("aspects"));
        form.parents = list(json, "parents");
        form.hiddenParents = list(json, "hidden_parents");
        form.siblings = list(json, "siblings");
        if (json.has("flags")) json.getAsJsonArray("flags").forEach(flag -> form.flags.add(flag.getAsString().toLowerCase(Locale.ROOT)));
        if (json.has("pages")) json.getAsJsonArray("pages").forEach(page -> form.pages.add(Page.of(page)));
        return form;
    }

    public static ResearchForm newResearch(String category, int column, int row) {
        var form = new ResearchForm(new JsonObject(), false);
        form.categoryKey = category;
        form.column = column;
        form.row = row;
        form.icon = "minecraft:book";
        form.aspects = "cognitio:3";
        var page = new Page();
        form.pages.add(page);
        return form;
    }

    public static ResearchForm newCategory() {
        var form = new ResearchForm(new JsonObject(), true);
        form.icon = "thaumcraft:textures/misc/r_thaumaturgy.png";
        return form;
    }

    /** The file's JSON, keeping everything the form does not cover. */
    public JsonObject json() {
        var json = original.deepCopy();
        json.addProperty("type", category ? "tc4modern:category" : "tc4modern:research");
        json.addProperty("key", key.strip());
        set(json, "name", name.json());
        if (category) {
            json.addProperty("icon", icon.strip());
            if (background.isBlank()) json.remove("background"); else json.addProperty("background", background.strip());
            return json;
        }
        json.addProperty("category", categoryKey);
        var position = new JsonArray();
        position.add(column);
        position.add(row);
        json.add("position", position);
        json.addProperty("complexity", complexity);
        json.add("icon", itemJson(icon, iconOriginal));
        json.add("aspects", aspectsJson(aspects));
        setList(json, "parents", parents);
        setList(json, "hidden_parents", hiddenParents);
        setList(json, "siblings", siblings);
        if (flags.isEmpty()) json.remove("flags");
        else { var array = new JsonArray(); flags.forEach(array::add); json.add("flags", array); }
        if (warp > 0) json.addProperty("warp", warp); else json.remove("warp");
        set(json, "subtitle", subtitle.json());
        var array = new JsonArray();
        pages.forEach(page -> array.add(page.json()));
        json.add("pages", array);
        return json;
    }

    // ---- Field syntax -----------------------------------------------------------------------------------------

    /** {@code minecraft:diamond}, {@code Thaumcraft:ItemResource@3} (an original item, any metadata {@code @*}) or {@code texture:ns:path}. */
    static String itemText(JsonObject item) {
        if (item == null) return "";
        if (item.has("texture")) return "texture:" + item.get("texture").getAsString();
        if (item.has("item")) return item.get("item").getAsString();
        if (!item.has("legacy_item")) return "";
        String key = item.get("legacy_item").getAsString();
        if (!item.has("metadata")) return key;
        String metadata = item.get("metadata").getAsString();
        return metadata.equals("0") ? key : key + "@" + metadata;
    }

    static JsonObject itemJson(String text, JsonObject previous) {
        String value = text.strip();
        if (previous != null && itemText(previous).equals(value)) return previous;
        var json = new JsonObject();
        if (value.startsWith("texture:")) { json.addProperty("texture", value.substring("texture:".length())); return json; }
        int at = value.indexOf('@');
        String id = at < 0 ? value : value.substring(0, at);
        int colon = id.indexOf(':');
        boolean legacy = at >= 0 || colon > 0 && !id.substring(0, colon).equals(id.substring(0, colon).toLowerCase(Locale.ROOT));
        if (!legacy) { json.addProperty("item", id); return json; }
        json.addProperty("legacy_item", id);
        if (at >= 0) {
            String metadata = value.substring(at + 1);
            if (metadata.equals("*")) json.addProperty("metadata", "*");
            else json.addProperty("metadata", Integer.parseInt(metadata));
        }
        return json;
    }

    /** {@code cognitio:3 ordo:1} (commas, spaces and {@code ×} all separate). */
    static String aspectsText(JsonObject aspects) {
        if (aspects == null) return "";
        var parts = new ArrayList<String>();
        aspects.entrySet().forEach(entry -> parts.add(entry.getKey() + ":" + entry.getValue().getAsInt()));
        return String.join(" ", parts);
    }

    static JsonObject aspectsJson(String text) {
        var json = new JsonObject();
        for (String part : text.strip().split("[\\s,，;]+")) {
            if (part.isEmpty()) continue;
            var pieces = part.split("[:×*x=]", 2);
            if (pieces.length != 2) throw new JsonParseException("Aspects look like cognitio:3 ordo:1, not '" + part + "'");
            json.addProperty(pieces[0].toLowerCase(Locale.ROOT), Integer.parseInt(pieces[1].strip()));
        }
        return json;
    }

    private static String list(JsonObject json, String name) {
        if (!json.has(name)) return "";
        var parts = new ArrayList<String>();
        json.getAsJsonArray(name).forEach(value -> parts.add(value.getAsString()));
        return String.join(", ", parts);
    }

    private static void setList(JsonObject json, String name, String text) {
        var array = new JsonArray();
        for (String part : text.split("[\\s,，;]+")) if (!part.isBlank()) array.add(part.strip());
        if (array.isEmpty()) json.remove(name); else json.add(name, array);
    }

    private static void set(JsonObject json, String name, JsonElement value) {
        if (value == null) json.remove(name); else json.add(name, value);
    }

    private static String string(JsonObject json, String name) {
        return json.has(name) && json.get(name).isJsonPrimitive() ? json.get(name).getAsString() : "";
    }
}
