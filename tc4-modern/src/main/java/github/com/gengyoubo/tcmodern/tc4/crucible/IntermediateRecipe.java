package github.com.gengyoubo.tcmodern.tc4.crucible;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * A synthesis recipe of the intermediate crucible: one or two materials held in a crucible that contains the aspects.
 * Materials match in any order. Stored as {@code data/<namespace>/tc4_intermediate_crucible/**.json}:
 * <pre>{"type":"tc4modern:intermediate_crucible","research":"KEY",
 *  "ingredients":[{"legacy_item":"Thaumcraft:ItemResource","metadata":2},{"item":"minecraft:netherite_ingot"}],
 *  "aspects":{"antiquitas":2},"result":{"item":"tc4modern:ancient_thaumium_ingot"}}</pre>
 */
public record IntermediateRecipe(String id, String research, List<Ingredient> ingredients, Map<String, Integer> aspects, Output result) {
    public static final String TYPE = "tc4modern:intermediate_crucible";
    public static final int MAX_INGREDIENTS = 2;
    public static final int WILDCARD = 32767;

    public IntermediateRecipe {
        Objects.requireNonNull(id);
        if (Objects.requireNonNull(research).isBlank()) throw new IllegalArgumentException("Blank research key");
        ingredients = List.copyOf(ingredients);
        if (ingredients.isEmpty() || ingredients.size() > MAX_INGREDIENTS)
            throw new IllegalArgumentException("Intermediate crucible recipes take 1 or 2 materials");
        var copy = new LinkedHashMap<String, Integer>();
        aspects.forEach((tag, amount) -> {
            if (tag == null || !tag.matches("[a-z][a-z0-9_]*") || amount == null || amount < 1)
                throw new IllegalArgumentException("Invalid aspect cost " + tag + "=" + amount);
            copy.put(tag, amount);
        });
        if (copy.isEmpty()) throw new IllegalArgumentException("Intermediate crucible recipes need aspects");
        aspects = java.util.Collections.unmodifiableMap(copy);
        Objects.requireNonNull(result);
    }

    /** One material: a modern item, a modern item tag, or an original item (metadata {@link #WILDCARD} for any). */
    public sealed interface Ingredient {
        record Item(String id) implements Ingredient { public Item { requireId(id); } }
        record Tag(String id) implements Ingredient { public Tag { requireId(id); } }
        record Legacy(String key, int metadata) implements Ingredient {
            public Legacy { if (Objects.requireNonNull(key).isBlank()) throw new IllegalArgumentException("Blank original item"); }
        }
    }

    /** A modern item by id, or an original stack; {@code nbt} is SNBT (modern or original form respectively), may be empty. */
    public record Output(String item, String legacyItem, int metadata, int count, String nbt) {
        public Output {
            if ((item == null) == (legacyItem == null)) throw new IllegalArgumentException("Result needs exactly one of item, legacy_item");
            if (item != null) requireId(item);
            if (count < 1 || count > 64) throw new IllegalArgumentException("Result count must be 1 to 64");
            nbt = Objects.requireNonNullElse(nbt, "");
        }
    }

    /**
     * Whether the held materials are exactly this recipe's ingredients, in any order. {@code accepts} tests one
     * ingredient against one held material.
     */
    public <T> boolean matches(List<T> held, BiPredicate<Ingredient, T> accepts) {
        if (held.size() != ingredients.size()) return false;
        if (held.size() == 1) return accepts.test(ingredients.get(0), held.get(0));
        return accepts.test(ingredients.get(0), held.get(0)) && accepts.test(ingredients.get(1), held.get(1))
                || accepts.test(ingredients.get(0), held.get(1)) && accepts.test(ingredients.get(1), held.get(0));
    }

    public static IntermediateRecipe parse(String id, JsonObject json) {
        String type = string(json, "type");
        if (!TYPE.equals(type)) throw new JsonParseException("Not an intermediate crucible recipe: " + type);
        var ingredients = new ArrayList<Ingredient>();
        if (!json.has("ingredients") || !json.get("ingredients").isJsonArray()) throw new JsonParseException("Missing ingredients array");
        for (var value : json.getAsJsonArray("ingredients")) ingredients.add(ingredient(value));
        var aspects = new LinkedHashMap<String, Integer>();
        if (!json.has("aspects") || !json.get("aspects").isJsonObject()) throw new JsonParseException("Missing aspects object");
        for (var entry : json.getAsJsonObject("aspects").entrySet()) aspects.put(entry.getKey(), entry.getValue().getAsInt());
        if (!json.has("result") || !json.get("result").isJsonObject()) throw new JsonParseException("Missing result object");
        var result = json.getAsJsonObject("result");
        var output = new Output(result.has("item") ? string(result, "item") : null,
                result.has("legacy_item") ? string(result, "legacy_item") : null,
                result.has("metadata") ? result.get("metadata").getAsInt() : 0,
                result.has("count") ? result.get("count").getAsInt() : 1,
                result.has("nbt") ? string(result, "nbt") : "");
        try {
            return new IntermediateRecipe(id, string(json, "research"), ingredients, aspects, output);
        } catch (IllegalArgumentException invalid) {
            throw new JsonParseException(invalid.getMessage(), invalid);
        }
    }

    private static Ingredient ingredient(JsonElement value) {
        if (!value.isJsonObject()) throw new JsonParseException("Ingredients are objects");
        var json = value.getAsJsonObject();
        int kinds = (json.has("item") ? 1 : 0) + (json.has("tag") ? 1 : 0) + (json.has("legacy_item") ? 1 : 0);
        if (kinds != 1) throw new JsonParseException("An ingredient needs exactly one of item, tag, legacy_item");
        try {
            if (json.has("item")) return new Ingredient.Item(string(json, "item"));
            if (json.has("tag")) return new Ingredient.Tag(string(json, "tag"));
            int metadata = 0;
            if (json.has("metadata")) metadata = "*".equals(json.get("metadata").getAsString()) ? WILDCARD : json.get("metadata").getAsInt();
            return new Ingredient.Legacy(string(json, "legacy_item"), metadata);
        } catch (IllegalArgumentException invalid) {
            throw new JsonParseException(invalid.getMessage(), invalid);
        }
    }

    private static String string(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) throw new JsonParseException("Missing " + key);
        return json.get(key).getAsString();
    }

    private static void requireId(String id) {
        if (!Objects.requireNonNull(id).matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Invalid id: " + id);
    }
}
