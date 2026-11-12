package github.com.gengyoubo.tcmodern.lib.json;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.tcmodern.api.recipe.TcSmithingRecipes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code minecraft:smithing_transform} recipes of every mod JAR ({@code data/<namespace>/recipes/**.json}), for
 * research {@code smithing} pages. Research registers before modern recipes load, so the files are read directly; a
 * datapack's recipes are not seen. Ingredients may be an item or a list of items; a recipe that names a tag (not bound
 * yet) or a missing item is left out. Read once, on first use.
 */
public final class SmithingRecipeFiles {
    private static final String TYPE = "minecraft:smithing_transform";
    private static volatile List<TcSmithingRecipes.Recipe> recipes;

    private SmithingRecipeFiles() { }

    public static List<TcSmithingRecipes.Recipe> recipes(LegacyRuntime runtime) {
        var loaded = recipes;
        if (loaded == null) {
            synchronized (SmithingRecipeFiles.class) {
                if (recipes == null) recipes = load(runtime);
                loaded = recipes;
            }
        }
        return loaded;
    }

    private static List<TcSmithingRecipes.Recipe> load(LegacyRuntime runtime) {
        var hostItems = RecipeLoader.hostItems(runtime);
        var result = new ArrayList<TcSmithingRecipes.Recipe>();
        RecipeLoader.files("recipes").forEach((id, path) -> {
            String text;
            try {
                text = Files.readString(path, StandardCharsets.UTF_8);
            } catch (IOException unreadable) {
                return;
            }
            if (!text.contains(TYPE)) return;
            try {
                var json = JsonParser.parseString(text).getAsJsonObject();
                if (!TYPE.equals(json.get("type").getAsString())) return;
                var output = json.getAsJsonObject("result");
                // NBT as an SNBT string or a JSON object (which SNBT also reads).
                var tag = output.get("nbt");
                String nbt = tag == null ? null : tag.isJsonPrimitive() ? tag.getAsString() : tag.toString();
                result.add(new TcSmithingRecipes.Recipe(slot(json.get("template"), hostItems), slot(json.get("base"), hostItems),
                        slot(json.get("addition"), hostItems),
                        hostItems.stack(output.get("item").getAsString(), output.has("count") ? output.get("count").getAsInt() : 1, nbt)));
            } catch (RuntimeException unusable) {
                // A tag, a missing mod's item or a malformed file: that recipe cannot be shown.
                LogUtils.getLogger().debug("Smithing recipe {} cannot be shown on research pages: {}", id, unusable.toString());
            }
        });
        LogUtils.getLogger().info("TC4 research: {} smithing recipes available to research pages.", result.size());
        return List.copyOf(result);
    }

    /** An ingredient: one {@code {"item": ...}} or a list of them. */
    private static List<LegacyStack> slot(JsonElement value, RecipeJson.HostItems hostItems) {
        var choices = new ArrayList<LegacyStack>();
        if (value.isJsonArray()) for (var choice : value.getAsJsonArray()) choices.add(item(choice.getAsJsonObject(), hostItems));
        else choices.add(item(value.getAsJsonObject(), hostItems));
        return choices;
    }

    private static LegacyStack item(JsonObject json, RecipeJson.HostItems hostItems) {
        if (!json.has("item")) throw new IllegalArgumentException("only items are supported: " + json);
        return hostItems.stack(json.get("item").getAsString(), 1, null);
    }
}
