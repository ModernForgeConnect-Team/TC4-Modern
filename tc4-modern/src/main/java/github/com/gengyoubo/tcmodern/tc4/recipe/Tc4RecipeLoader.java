package github.com.gengyoubo.tcmodern.tc4.recipe;

import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyFeatures;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.tcmodern.api.recipe.TcRecipeRegistrar;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Adds the JSON recipes of {@code data/<namespace>/tc4_recipes/**.json} in every mod JAR and of
 * {@code config/tc4modern/tc4_recipes/<namespace>/**.json} (for modpacks; a config file replaces a JAR file of the
 * same id) to the original TC4 recipe list, once at load completion on each physical side. Not a datapack: recipes
 * are fixed for the session and must be identical on client and server. A broken file is logged and skipped.
 */
public final class Tc4RecipeLoader {
    private static final String FOLDER = "tc4_recipes";

    private Tc4RecipeLoader() { }

    public static void load(LegacyRuntime runtime) {
        if (!runtime.supports(LegacyFeatures.MLA_OBJECT_ARGUMENTS)) {
            LogUtils.getLogger().warn("TC4 recipes: this MFCL build cannot pass recipe inputs to the original; JSON recipes are not loaded.");
            return;
        }
        var files = new TreeMap<String, Path>();
        for (var info : ModList.get().getModFiles()) collect(info.getFile().findResource("data"), true, files);
        collect(FMLPaths.CONFIGDIR.get().resolve("tc4modern").resolve(FOLDER), false, files);
        HostItemsFromRuntime hostItems = new HostItemsFromRuntime(runtime);
        int added = 0;
        for (Map.Entry<String, Path> file : files.entrySet()) {
            try {
                var json = JsonParser.parseString(Files.readString(file.getValue(), StandardCharsets.UTF_8)).getAsJsonObject();
                TcRecipeRegistrar.register(runtime, Tc4RecipeJson.parse(json, hostItems));
                added++;
            } catch (IOException | RuntimeException failure) {
                LogUtils.getLogger().error("TC4 recipes: skipped {} ({}): {}", file.getKey(), file.getValue(), failure.toString());
            }
        }
        LogUtils.getLogger().info("TC4 recipes: added {} of {} JSON recipes.", added, files.size());
    }

    /**
     * {@code root} is a {@code data} folder ({@code <namespace>/tc4_recipes/...}) or the config folder
     * ({@code <namespace>/...}); ids are {@code namespace:path} without {@code .json}.
     */
    private static void collect(Path root, boolean data, Map<String, Path> files) {
        if (root == null || !Files.isDirectory(root)) return;
        try (Stream<Path> namespaces = Files.list(root)) {
            for (Path namespace : namespaces.filter(Files::isDirectory).toList()) {
                Path recipes = data ? namespace.resolve(FOLDER) : namespace;
                if (!Files.isDirectory(recipes)) continue;
                try (Stream<Path> walk = Files.walk(recipes)) {
                    for (Path file : walk.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json")).toList()) {
                        var path = new StringBuilder();
                        for (Path part : recipes.relativize(file)) path.append(path.isEmpty() ? "" : "/").append(part);
                        String name = namespace.getFileName().toString().replace("/", "");
                        files.put(name + ":" + path.substring(0, path.length() - ".json".length()), file);
                    }
                }
            }
        } catch (IOException failure) {
            LogUtils.getLogger().error("TC4 recipes: cannot list {}: {}", root, failure.toString());
        }
    }

    /** Modern items cross as the original identity MFCL gives them, keeping count and NBT. */
    private record HostItemsFromRuntime(LegacyRuntime runtime) implements Tc4RecipeJson.HostItems {
        @Override
        public LegacyStack stack(String id, int count, String nbt) {
            var location = ResourceLocation.tryParse(id);
            var item = location == null ? null : ForgeRegistries.ITEMS.getValue(location);
            if (item == null || item == Items.AIR) throw new IllegalArgumentException("Unknown item: " + id);
            // A host-only item has no original form until its identity has been requested.
            runtime.hostRepresentations().item(location.toString());
            var host = new ItemStack(item, count);
            if (nbt != null) {
                try {
                    host.setTag(TagParser.parseTag(nbt));
                } catch (CommandSyntaxException malformed) {
                    throw new IllegalArgumentException("Malformed nbt for " + id + ": " + malformed.getMessage(), malformed);
                }
            }
            var legacy = runtime.mappings().toLegacy(LegacyMappings.ITEM_STACK, host)
                    .orElseThrow(() -> new IllegalArgumentException("No original form for " + id));
            return runtime.snapshot(legacy);
        }
    }
}
