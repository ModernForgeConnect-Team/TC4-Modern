package github.com.gengyoubo.tcmodern.lib.book;

import com.google.gson.*;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.tcmodern.api.research.TcResearchEntry;
import github.com.gengyoubo.tcmodern.api.research.TcThaumonomicon;
import github.com.gengyoubo.tcmodern.lib.json.RecipeLoader;
import github.com.gengyoubo.tcmodern.lib.json.ResearchJson;
import github.com.gengyoubo.tcmodern.lib.json.ResearchLoader;
import java.io.IOException;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The editing half of the operators' editing mode, as in FTB Quests: saving, deleting and moving JSON research and
 * categories. Every change is validated with the same parser the loader uses, written to the config folder (an
 * edited JAR file gets a config override of the same id), then the server reloads and hands clients the result.
 * Research Thaumcraft or an addon registers in code is never written.
 */
final class BookEditing {
    static final String DEFAULT_NAMESPACE = "custom";
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private BookEditing() {}

    static void handle(LegacyRuntime runtime, ServerPlayer player, BookNetwork.Action action) {
        try {
            switch (action.kind()) {
                case SAVE -> save(runtime, player, action.key(), action.data());
                case DELETE -> delete(runtime, player, action.key());
                case MOVE -> move(runtime, player, action.key(), action.column(), action.row());
                default -> { }
            }
        } catch (IllegalArgumentException | JsonParseException | IllegalStateException refused) {
            fail(player, refused.getMessage());
        } catch (IOException failure) {
            fail(player, failure.toString());
        }
    }

    /** Saves a category or research; {@code id} empty for a new file. */
    private static void save(LegacyRuntime runtime, ServerPlayer player, String id, String data) throws IOException {
        var json = JsonParser.parseString(data).getAsJsonObject();
        var parsed = ResearchJson.parse(json, RecipeLoader.hostItems(runtime));
        String key = parsed.key();
        String source = ResearchLoader.source(key);
        if (id.isEmpty()) id = source != null ? source : newId(parsed);
        if (source != null && !source.equals(id)) throw new IllegalArgumentException(key + " is already defined in " + source);
        if (source == null && (parsed.research() != null ? TcThaumonomicon.find(runtime, key).isPresent()
                : TcThaumonomicon.snapshot(runtime).stream().anyMatch(c -> c.key().equals(key))))
            throw new IllegalArgumentException(key + " is registered by Thaumcraft or an addon and cannot be redefined");
        var research = parsed.research();
        if (research != null && !research.flags().contains(TcResearchEntry.Flag.VIRTUAL))
            checkFree(runtime, research.category(), research.column(), research.row(), key);
        ResearchLoader.write(id, PRETTY.toJson(json));
        reload(runtime, player, id, "book.tcmodernlib.edit.saved", key);
    }

    private static void delete(LegacyRuntime runtime, ServerPlayer player, String id) throws IOException {
        var text = ResearchLoader.json(id);
        if (text == null) throw new IllegalArgumentException("No JSON research file " + id);
        if (!ResearchLoader.inConfig(id)) throw new IllegalArgumentException(id + " comes from a mod JAR; edit it to override it instead");
        var json = JsonParser.parseString(text).getAsJsonObject();
        String key = json.has("key") ? json.get("key").getAsString() : id;
        if ("tc4modern:category".equals(json.has("type") ? json.get("type").getAsString() : "")
                && TcThaumonomicon.snapshot(runtime).stream().filter(c -> c.key().equals(key)).anyMatch(c -> !c.entries().isEmpty()))
            throw new IllegalArgumentException("Category " + key + " still has research; delete or move it first");
        ResearchLoader.delete(id);
        reload(runtime, player, null, "book.tcmodernlib.edit.deleted", key);
    }

    private static void move(LegacyRuntime runtime, ServerPlayer player, String key, int column, int row) throws IOException {
        String id = ResearchLoader.source(key);
        if (id == null) throw new IllegalArgumentException(key + " is registered in code and cannot be moved");
        var entry = TcThaumonomicon.find(runtime, key).orElseThrow(() -> new IllegalArgumentException("Unknown research " + key));
        checkFree(runtime, entry.category(), column, row, key);
        var json = JsonParser.parseString(ResearchLoader.json(id)).getAsJsonObject();
        var position = new JsonArray();
        position.add(column);
        position.add(row);
        json.add("position", position);
        ResearchLoader.write(id, PRETTY.toJson(json));
        reload(runtime, player, id, "book.tcmodernlib.edit.moved", key);
    }

    /** The original refuses two research in one slot of a category. */
    private static void checkFree(LegacyRuntime runtime, String category, int column, int row, String self) {
        for (var c : TcThaumonomicon.snapshot(runtime)) {
            if (!c.key().equals(category)) continue;
            for (var other : c.entries())
                if (!other.key().equals(self) && !other.has(TcResearchEntry.Flag.VIRTUAL) && other.column() == column && other.row() == row)
                    throw new IllegalArgumentException("Slot " + column + "," + row + " is taken by " + other.key());
        }
    }

    /** {@code custom:<key>}, or the namespace of the category's own file; categories get a {@code category_} prefix. */
    private static String newId(ResearchJson.Parsed parsed) {
        String namespace = DEFAULT_NAMESPACE;
        if (parsed.research() != null) {
            String categorySource = ResearchLoader.source(parsed.research().category());
            if (categorySource != null) namespace = categorySource.substring(0, categorySource.indexOf(':'));
        }
        String base = (parsed.category() != null ? "category_" : "") + parsed.key().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        String id = namespace + ":" + base;
        for (int n = 2; ResearchLoader.json(id) != null || ResearchLoader.inConfig(id); n++) id = namespace + ":" + base + "_" + n;
        return id;
    }

    private static void reload(LegacyRuntime runtime, ServerPlayer player, String id, String message, String key) {
        var result = ResearchLoader.reload(runtime);
        BookNetwork.reloaded(player.server, result);
        var own = result.errors().stream().filter(error -> id != null && error.startsWith(id + ":")).findFirst();
        if (own.isPresent()) fail(player, own.get());
        else player.displayClientMessage(Component.translatable(message, key).withStyle(ChatFormatting.GREEN), true);
    }

    private static void fail(ServerPlayer player, String reason) {
        player.displayClientMessage(Component.translatable("book.tcmodernlib.edit.failed", String.valueOf(reason)).withStyle(ChatFormatting.RED), false);
    }
}
