package github.com.gengyoubo.tcmodern.lib.client.book.edit;

import com.google.gson.JsonObject;
import github.com.gengyoubo.tcmodern.lib.book.BookNetwork;
import github.com.gengyoubo.tcmodern.lib.json.RecipeLoader;
import github.com.gengyoubo.tcmodern.lib.json.ResearchJson;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import static github.com.gengyoubo.tcmodern.lib.client.book.BookChrome.*;

/** Form editor for one JSON category: key, tab icon, map background and names. */
public final class CategoryEditorScreen extends EditorScreen {
    /** The original category icons, offered by the cycle button next to the icon field. */
    private static final List<String> ICONS = List.of("thaumcraft:textures/misc/r_thaumaturgy.png", "thaumcraft:textures/misc/r_crucible.png",
            "thaumcraft:textures/misc/r_artifice.png", "thaumcraft:textures/misc/r_golemancy.png", "thaumcraft:textures/misc/r_eldritch.png",
            "thaumcraft:textures/items/thaumonomiconcheat.png");
    private static final int X = 120, W = 220;

    private final String id;
    private ResearchForm form;
    private EditBox key, icon, background, nameZh, nameEn;

    public CategoryEditorScreen(Screen parent, String id, ResearchForm form) {
        super(Component.translatable(id.isEmpty() ? "book.tcmodernlib.editor.new_category" : "book.tcmodernlib.editor.category_title", form.key), parent);
        this.id = id;
        this.form = form;
    }

    @Override protected void build() {
        key = field(X, 48, W, form.key, "KEY");
        key.setEditable(id.isEmpty());
        icon = field(X, 84, W - 44, form.icon, "ns:textures/...png");
        background = field(X, 120, W, form.background, "thaumcraft:textures/gui/gui_researchback.png");
        nameZh = field(X, 156, W, form.name.zh, "中文名称");
        nameEn = field(X, 192, W, form.name.en, "English name");
    }

    @Override protected void commit() {
        if (key == null) return;
        form.key = key.getValue().strip();
        form.icon = icon.getValue().strip();
        form.background = background.getValue().strip();
        form.name.zh = nameZh.getValue();
        form.name.en = nameEn.getValue();
    }

    @Override protected List<Button> buttons() {
        return List.of(new Button("save", Component.translatable("book.tcmodernlib.editor.save"), true),
                new Button("cancel", Component.translatable("book.tcmodernlib.editor.cancel"), false),
                new Button("json", Component.translatable("book.tcmodernlib.editor.json"), false));
    }

    @Override protected void pressed(String button) {
        commit();
        switch (button) {
            case "cancel" -> onClose();
            case "json" -> minecraft.setScreen(new JsonEditorScreen(this, form.json(), json -> form = ResearchForm.of(json), this::send));
            case "save" -> {
                try {
                    var json = form.json();
                    if (runtime != null) ResearchJson.parse(json, RecipeLoader.hostItems(runtime));
                    send(json);
                } catch (RuntimeException invalid) {
                    status(Component.translatable("book.tcmodernlib.editor.invalid", String.valueOf(invalid.getMessage())).getString(), true);
                }
            }
            default -> { }
        }
    }

    private void send(JsonObject json) {
        BookNetwork.send(new BookNetwork.Action(BookNetwork.Kind.SAVE, id, false, json.toString(), 0, 0));
        minecraft.setScreen(parent);
    }

    @Override protected void renderBody(GuiGraphics g, int mouseX, int mouseY) {
        card(g, left + X - 14, top + 32, W + 28, 190);
        label(g, "book.tcmodernlib.editor.key", X, 39);
        label(g, "book.tcmodernlib.editor.icon", X, 75);
        label(g, "book.tcmodernlib.editor.background", X, 111);
        label(g, "book.tcmodernlib.editor.name", X, 147);
        label(g, "book.tcmodernlib.editor.name_en", X, 183);
        int px = left + X + W - 38, py = top + 83;
        round(g, px - 1, py - 1, 18, 18, 4, WELL);
        var texture = ResourceLocation.tryParse(icon.getValue().strip());
        if (texture != null) g.blit(texture, px, py, 16, 16, 0F, 0F, 1, 1, 1, 1);
        boolean hot = inside(mouseX, mouseY, X + W - 18, 84, 18, 14);
        round(g, left + X + W - 18, top + 84, 18, 14, 7, hot ? 0x33FFFFFF : 0x1FFFFFFF);
        centered(g, font, Component.literal("⟳"), left + X + W - 9, top + 87, TEXT);
    }

    @Override protected boolean clicked(double x, double y, int button) {
        if (!inside(x, y, X + W - 18, 84, 18, 14)) return false;
        commit();
        int index = ICONS.indexOf(form.icon);
        form.icon = ICONS.get(Math.floorMod(index + 1, ICONS.size()));
        rebuild();
        return true;
    }

    /** Replaces the form shown, discarding unsaved widget values; for scripted editing. */
    public void load(ResearchForm replacement) {
        form = replacement;
        relayout();
    }

    /** Saves as the header button does. */
    public void submit() { pressed("save"); }
}
