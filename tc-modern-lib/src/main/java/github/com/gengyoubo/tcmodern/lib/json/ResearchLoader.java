package github.com.gengyoubo.tcmodern.lib.json;

import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.tcmodern.api.research.TcThaumonomicon;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Registers the JSON categories and research of {@code data/<namespace>/tc4_research/**.json} in every mod JAR and
 * of {@code config/tc4modern/tc4_research/<namespace>/**.json} (a config file replaces a JAR file of the same id),
 * after the JSON recipes their pages may show. Remembers what it added so a reload can take it back out, and the
 * text of every file so a server can hand its exact research to clients. A broken file or entry is logged and
 * skipped. The in-game editor writes config files through {@link #write} and {@link #delete}.
 */
public final class ResearchLoader {
    public static final String FOLDER = "tc4_research";
    /** Outcome of one load: counts and one line per skipped entry. */
    public record Result(int categories, int research, List<String> errors) {}

    private static final Map<String, String> SOURCES = new LinkedHashMap<>(), CONTENTS = new TreeMap<>();
    private static final List<String> CATEGORIES = new ArrayList<>(), RESEARCH = new ArrayList<>();
    private static Result last = new Result(0, 0, List.of());

    private ResearchLoader() { }

    /** The {@code namespace:path} of the JSON file that added a category or research key, or null. */
    public static synchronized String source(String key) { return SOURCES.get(key); }

    /** Every loaded file's text by id: what a server sends its clients. */
    public static synchronized Map<String, String> contents() { return Map.copyOf(CONTENTS); }

    /** One loaded file's text, or null. */
    public static synchronized String json(String id) { return CONTENTS.get(id); }

    public static synchronized Result last() { return last; }

    /** Reads every file from the mod JARs and the config folder and registers what they define. */
    public static synchronized Result load(LegacyRuntime runtime) {
        var contents = new TreeMap<String, String>();
        var errors = new ArrayList<String>();
        for (var file : RecipeLoader.files(FOLDER).entrySet()) {
            try {
                contents.put(file.getKey(), Files.readString(file.getValue(), StandardCharsets.UTF_8));
            } catch (IOException failure) {
                errors.add(file.getKey() + ": " + failure.getMessage());
            }
        }
        return register(runtime, contents, errors);
    }

    /** Takes out everything the last load added, then loads the files again. */
    public static synchronized Result reload(LegacyRuntime runtime) {
        unload(runtime);
        return load(runtime);
    }

    /** Replaces the loaded research with exactly these files, as a client does with its server's research. */
    public static synchronized Result reloadFrom(LegacyRuntime runtime, Map<String, String> contents) {
        unload(runtime);
        return register(runtime, new TreeMap<>(contents), new ArrayList<>());
    }

    // ---- Editing ----------------------------------------------------------------------------------------------

    /** Where the config copy of a file id lives; editing a JAR file writes its override here. */
    public static Path configPath(String id) {
        int colon = id.indexOf(':');
        if (colon <= 0 || colon == id.length() - 1 || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || id.contains(".."))
            throw new IllegalArgumentException("Invalid research file id: " + id);
        return FMLPaths.CONFIGDIR.get().resolve("tc4modern").resolve(FOLDER).resolve(id.substring(0, colon))
                .resolve(id.substring(colon + 1) + ".json");
    }

    /** True when the file exists in the config folder, so deleting it is possible. */
    public static boolean inConfig(String id) { return Files.isRegularFile(configPath(id)); }

    public static synchronized void write(String id, String json) throws IOException {
        var path = configPath(id);
        Files.createDirectories(path.getParent());
        Files.writeString(path, json, StandardCharsets.UTF_8);
    }

    /** Deletes the config file; false when the id only exists in a mod JAR. */
    public static synchronized boolean delete(String id) throws IOException {
        return Files.deleteIfExists(configPath(id));
    }

    // ---- Registration -----------------------------------------------------------------------------------------

    private static void unload(LegacyRuntime runtime) {
        for (int i = RESEARCH.size() - 1; i >= 0; i--) TcThaumonomicon.unregister(runtime, RESEARCH.get(i));
        for (int i = CATEGORIES.size() - 1; i >= 0; i--) TcThaumonomicon.unregisterCategory(runtime, CATEGORIES.get(i));
        RESEARCH.clear();
        CATEGORIES.clear();
        SOURCES.clear();
        CONTENTS.clear();
    }

    private static Result register(LegacyRuntime runtime, SortedMap<String, String> contents, List<String> errors) {
        var parsed = new ArrayList<Map.Entry<String, ResearchJson.Parsed>>();
        var hostItems = RecipeLoader.hostItems(runtime);
        for (var file : contents.entrySet()) {
            try {
                var json = JsonParser.parseString(file.getValue()).getAsJsonObject();
                parsed.add(Map.entry(file.getKey(), ResearchJson.parse(json, hostItems)));
            } catch (RuntimeException failure) {
                errors.add(file.getKey() + ": " + failure.getMessage());
            }
        }
        CONTENTS.putAll(contents);
        // Texts first, so names resolve as soon as entries exist.
        var texts = new LinkedHashMap<String, Map<String, String>>();
        for (var entry : parsed)
            entry.getValue().texts().forEach((language, values) -> texts.computeIfAbsent(language, unused -> new LinkedHashMap<>()).putAll(values));
        texts.forEach((language, values) -> {
            try {
                runtime.languages().override(language, values);
            } catch (RuntimeException unsupported) {
                errors.add("texts " + language + ": " + unsupported.getMessage());
            }
        });
        int categories = 0, research = 0;
        for (var entry : parsed) {
            var category = entry.getValue().category();
            if (category == null) continue;
            try {
                TcThaumonomicon.registerCategory(runtime, category);
                CATEGORIES.add(category.key());
                SOURCES.put(category.key(), entry.getKey());
                categories++;
            } catch (RuntimeException failure) {
                errors.add(entry.getKey() + ": " + failure.getMessage());
            }
        }
        for (var entry : parsed) {
            var definition = entry.getValue().research();
            if (definition == null) continue;
            try {
                TcThaumonomicon.register(runtime, definition);
                RESEARCH.add(definition.key());
                SOURCES.put(definition.key(), entry.getKey());
                research++;
            } catch (RuntimeException failure) {
                errors.add(entry.getKey() + ": " + failure.getMessage());
            }
        }
        errors.forEach(error -> LogUtils.getLogger().error("TC4 research: skipped {}", error));
        LogUtils.getLogger().info("TC4 research: added {} categories and {} research from {} JSON files.", categories, research, parsed.size());
        last = new Result(categories, research, List.copyOf(errors));
        return last;
    }
}
