package github.com.gengyoubo.tcmodern.tc4.crucible;

import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import github.com.gengyoubo.tcmodern.lib.json.RecipeLoader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The intermediate crucible's synthesis recipes: {@code data/<namespace>/tc4_intermediate_crucible/**.json} in every mod
 * JAR and {@code config/tc4modern/tc4_intermediate_crucible/<namespace>/**.json}, like the {@code tc4_recipes} JSON.
 * Read once per physical side, so the client (JEI) sees the same recipes as the server. A broken file is logged and skipped.
 */
public final class IntermediateRecipes {
    public static final String FOLDER = "tc4_intermediate_crucible";
    private static volatile List<IntermediateRecipe> recipes;

    private IntermediateRecipes() { }

    public static List<IntermediateRecipe> all() {
        var loaded = recipes;
        if (loaded == null) {
            synchronized (IntermediateRecipes.class) {
                if (recipes == null) recipes = load();
                loaded = recipes;
            }
        }
        return loaded;
    }

    private static List<IntermediateRecipe> load() {
        var files = RecipeLoader.files(FOLDER);
        var result = new ArrayList<IntermediateRecipe>();
        files.forEach((id, path) -> {
            try {
                var json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
                if (!json.isJsonObject()) throw new IllegalArgumentException("Not a JSON object");
                result.add(IntermediateRecipe.parse(id, json.getAsJsonObject()));
            } catch (IOException | RuntimeException invalid) {
                LogUtils.getLogger().error("Skipping intermediate crucible recipe {} ({}): {}", id, path, invalid.toString());
            }
        });
        LogUtils.getLogger().info("Loaded {} of {} intermediate crucible recipes", result.size(), files.size());
        return List.copyOf(result);
    }
}
