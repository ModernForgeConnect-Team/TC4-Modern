package github.com.gengyoubo.tcmodern.lib.client.book.edit;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import github.com.gengyoubo.tcmodern.lib.json.RecipeLoader;
import github.com.gengyoubo.tcmodern.lib.json.ResearchJson;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The whole file as text, for what the form does not cover. Validates with the loader's own parser. */
final class JsonEditorScreen extends EditorScreen {
    private static final com.google.gson.Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Consumer<JsonObject> apply, save;
    private String text;
    private MultiLineEditBox area;

    JsonEditorScreen(Screen parent, JsonObject json, Consumer<JsonObject> apply, Consumer<JsonObject> save) {
        super(Component.translatable("book.tcmodernlib.editor.json_title"), parent);
        this.apply = apply;
        this.save = save;
        this.text = PRETTY.toJson(json);
    }

    @Override protected void build() { area = area(10, 32, WIDTH - 20, HEIGHT - 60, text, "{ }"); setFocused(area); }
    @Override protected void commit() { if (area != null) text = area.getValue(); }

    @Override protected List<Button> buttons() {
        return List.of(new Button("save", Component.translatable("book.tcmodernlib.editor.save"), true),
                new Button("apply", Component.translatable("book.tcmodernlib.editor.apply"), false),
                new Button("check", Component.translatable("book.tcmodernlib.editor.check"), false),
                new Button("format", Component.translatable("book.tcmodernlib.editor.format"), false),
                new Button("cancel", Component.translatable("book.tcmodernlib.editor.cancel"), false));
    }

    @Override protected void pressed(String id) {
        commit();
        switch (id) {
            case "cancel" -> onClose();
            case "format" -> { var json = parse(false); if (json != null) { text = PRETTY.toJson(json); rebuild(); } }
            case "check" -> { if (parse(true) != null) status(Component.translatable("book.tcmodernlib.editor.valid").getString(), false); }
            case "apply" -> {
                var json = parse(false);
                if (json == null) return;
                try {
                    apply.accept(json);
                    minecraft.setScreen(parent);
                } catch (RuntimeException unfit) {
                    status(String.valueOf(unfit.getMessage()), true);
                }
            }
            case "save" -> { var json = parse(true); if (json != null) save.accept(json); }
            default -> { }
        }
    }

    /** The text as JSON, or null with the problem shown; {@code full} also runs the research parser. */
    private JsonObject parse(boolean full) {
        try {
            var json = JsonParser.parseString(text).getAsJsonObject();
            if (full && runtime != null) ResearchJson.parse(json, RecipeLoader.hostItems(runtime));
            return json;
        } catch (RuntimeException invalid) {
            status(Component.translatable("book.tcmodernlib.editor.invalid", String.valueOf(invalid.getMessage())).getString(), true);
            return null;
        }
    }

    @Override protected void renderBody(GuiGraphics g, int mouseX, int mouseY) { }
}
