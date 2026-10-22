package github.com.gengyoubo.tcmodern.tc4.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.tcmodern.api.recipe.TcRecipeDefinition;
import github.com.gengyoubo.tcmodern.api.recipe.TcRecipeDefinition.Ingredient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@code tc4_recipes} JSON format. Items are either original ({@code legacy_item}: the original registry key,
 * {@code metadata} with {@code "*"} for any, {@code nbt} in 1.7.10 SNBT) or host ({@code item}: a modern item id,
 * {@code nbt} in modern SNBT); ingredients may instead be an ore dictionary name ({@code ore}).
 */
final class Tc4RecipeJson {
    static final int WILDCARD = 32767;

    /** Turns a modern item id, count and optional modern SNBT into the original stack MFCL represents it with. */
    interface HostItems {
        LegacyStack stack(String id, int count, String nbt);
    }

    private Tc4RecipeJson() { }

    static TcRecipeDefinition parse(JsonObject json, HostItems hostItems) {
        String type = string(json, "type");
        String research = string(json, "research");
        var aspects = aspects(json);
        return switch (type) {
            case "tc4modern:arcane_shaped" -> {
                var pattern = new ArrayList<String>();
                for (var row : array(json, "pattern")) pattern.add(row.getAsString());
                var key = new LinkedHashMap<Character, Ingredient>();
                for (var entry : object(json, "key").entrySet()) {
                    if (entry.getKey().length() != 1) throw new JsonParseException("Key symbols are single characters: '" + entry.getKey() + "'");
                    key.put(entry.getKey().charAt(0), ingredient(entry.getValue(), hostItems));
                }
                boolean mirrored = !json.has("mirrored") || json.get("mirrored").getAsBoolean();
                yield new TcRecipeDefinition.ArcaneShaped(research, pattern, key, aspects, item(object(json, "result"), hostItems, true), mirrored);
            }
            case "tc4modern:arcane_shapeless" -> {
                var ingredients = new ArrayList<Ingredient>();
                for (var value : array(json, "ingredients")) ingredients.add(ingredient(value, hostItems));
                yield new TcRecipeDefinition.ArcaneShapeless(research, ingredients, aspects, item(object(json, "result"), hostItems, true));
            }
            case "tc4modern:crucible" -> new TcRecipeDefinition.Crucible(research, ingredient(member(json, "catalyst"), hostItems),
                    aspects, item(object(json, "result"), hostItems, true));
            case "tc4modern:infusion" -> {
                var components = new ArrayList<LegacyStack>();
                for (var value : array(json, "components")) components.add(item(asObject(value, "component"), hostItems, false));
                int instability = json.has("instability") ? json.get("instability").getAsInt() : 0;
                yield new TcRecipeDefinition.Infusion(research, item(object(json, "input"), hostItems, false), components, aspects,
                        item(object(json, "result"), hostItems, true), instability);
            }
            default -> throw new JsonParseException("Unknown recipe type: " + type);
        };
    }

    private static Map<String, Integer> aspects(JsonObject json) {
        var aspects = new LinkedHashMap<String, Integer>();
        if (json.has("aspects")) for (var entry : object(json, "aspects").entrySet()) aspects.put(entry.getKey(), entry.getValue().getAsInt());
        return aspects;
    }

    private static Ingredient ingredient(JsonElement value, HostItems hostItems) {
        var json = asObject(value, "ingredient");
        if (json.has("ore")) {
            if (json.has("item") || json.has("legacy_item")) throw new JsonParseException("An ingredient is an ore name or an item, not both");
            return new Ingredient.Ore(string(json, "ore"));
        }
        return new Ingredient.Item(item(json, hostItems, false));
    }

    /** Results default to count 1 and accept a count; inputs are always single items, as the original matches them. */
    private static LegacyStack item(JsonObject json, HostItems hostItems, boolean result) {
        int count = json.has("count") ? json.get("count").getAsInt() : 1;
        if (!result && count != 1) throw new JsonParseException("Inputs are single items; count is only for results");
        String nbt = json.has("nbt") ? json.get("nbt").getAsString() : null;
        if (json.has("legacy_item") == json.has("item")) throw new JsonParseException("An item needs exactly one of legacy_item or item");
        if (json.has("item")) {
            if (json.has("metadata")) throw new JsonParseException("metadata belongs to legacy_item; a modern item has its own");
            return hostItems.stack(string(json, "item"), count, nbt);
        }
        int metadata = 0;
        if (json.has("metadata")) {
            var value = json.get("metadata");
            metadata = value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() && value.getAsString().equals("*") ? WILDCARD : value.getAsInt();
        }
        if (result && metadata == WILDCARD) throw new JsonParseException("A result cannot use the metadata wildcard");
        try {
            return new LegacyStack(string(json, "legacy_item"), metadata, count, nbt);
        } catch (IllegalArgumentException invalid) {
            throw new JsonParseException(invalid.getMessage(), invalid);
        }
    }

    private static JsonElement member(JsonObject json, String name) {
        if (!json.has(name)) throw new JsonParseException("Missing '" + name + "'");
        return json.get(name);
    }

    private static String string(JsonObject json, String name) {
        var value = member(json, name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new JsonParseException("'" + name + "' must be a string");
        return value.getAsString();
    }

    private static JsonObject object(JsonObject json, String name) {
        return asObject(member(json, name), name);
    }

    private static JsonObject asObject(JsonElement value, String name) {
        if (!value.isJsonObject()) throw new JsonParseException("'" + name + "' must be an object");
        return value.getAsJsonObject();
    }

    private static List<JsonElement> array(JsonObject json, String name) {
        var value = member(json, name);
        if (!value.isJsonArray()) throw new JsonParseException("'" + name + "' must be an array");
        var result = new ArrayList<JsonElement>();
        ((JsonArray) value).forEach(result::add);
        return result;
    }
}
