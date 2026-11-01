package github.com.gengyoubo.tcmodern.lib.client.book.edit;

import com.google.gson.JsonObject;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspect;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspects;
import github.com.gengyoubo.tcmodern.api.research.TcThaumonomicon;
import github.com.gengyoubo.tcmodern.lib.book.BookNetwork;
import github.com.gengyoubo.tcmodern.lib.client.AspectIcons;
import github.com.gengyoubo.tcmodern.lib.client.book.BookClient;
import github.com.gengyoubo.tcmodern.lib.json.RecipeJson;
import github.com.gengyoubo.tcmodern.lib.json.RecipeLoader;
import github.com.gengyoubo.tcmodern.lib.json.ResearchJson;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import static github.com.gengyoubo.tcmodern.lib.client.book.BookChrome.*;

/**
 * Form editor for one JSON research, opened from the Thaumonomicon's editing mode. Saving validates with the same
 * parser the loader uses, then the server checks, writes and reloads it.
 */
public final class ResearchEditorScreen extends EditorScreen {
    private static final int LEFT_X = 10, MID_X = 156, RIGHT_X = 310, COLUMN_W = 140, MID_W = 148, ROW_STEP = 25;

    private final String id;
    private ResearchForm form;
    private final Map<String, TcAspect> aspects = new HashMap<>();
    private final Map<String, ItemStack> previews = new HashMap<>();
    private int selected;
    private String language = net.minecraft.client.Minecraft.getInstance().getLanguageManager().getSelected();
    // Widgets of the current layout.
    private EditBox key, column, row, icon, warp, name, subtitle, aspectBox, parents, hidden, siblings;

    /** {@code id} is the file id, or empty for a new research. */
    public ResearchEditorScreen(Screen parent, String id, ResearchForm form) {
        super(Component.translatable(id.isEmpty() ? "book.tcmodernlib.editor.new_research" : "book.tcmodernlib.editor.research", form.key), parent);
        this.id = id;
        this.form = form;
        if (runtime != null) for (var aspect : TcAspects.snapshot(runtime)) aspects.put(aspect.tag(), aspect);
    }

    ResearchForm form() { return form; }

    void replace(ResearchForm replacement) {
        form = replacement;
        selected = Math.min(selected, Math.max(0, form.pages.size() - 1));
    }

    // ---- Layout -----------------------------------------------------------------------------------------------

    private int rowY(int index) { return 34 + index * ROW_STEP; }

    @Override protected void build() {
        key = field(LEFT_X, rowY(0) + 9, COLUMN_W, form.key, "KEY");
        key.setEditable(id.isEmpty());
        column = field(LEFT_X, rowY(2) + 9, 66, String.valueOf(form.column), "0");
        row = field(LEFT_X + 74, rowY(2) + 9, 66, String.valueOf(form.row), "0");
        icon = field(LEFT_X, rowY(4) + 9, COLUMN_W - 20, form.icon, "minecraft:book");
        warp = field(LEFT_X, rowY(5) + 9, 60, String.valueOf(form.warp), "0");
        name = field(MID_X, rowY(1) + 9, MID_W, form.name.get(language), "");
        subtitle = field(MID_X, rowY(2) + 9, MID_W, form.subtitle.get(language), "");
        aspectBox = field(MID_X, rowY(4) + 9, MID_W - 20, form.aspects, "cognitio:3 ordo:1");
        parents = field(MID_X, 172 + 9, MID_W, form.parents, "KEY, KEY");
        hidden = field(MID_X, 197 + 9, MID_W, form.hiddenParents, "KEY, KEY");
        siblings = field(MID_X, 222 + 9, MID_W, form.siblings, "KEY, KEY");

    }

    @Override protected void commit() {
        if (key == null) return;
        form.key = key.getValue().strip();
        form.column = number(column.getValue(), form.column);
        form.row = number(row.getValue(), form.row);
        form.icon = icon.getValue();
        form.warp = Math.max(0, number(warp.getValue(), 0));
        form.name.set(language, name.getValue());
        form.subtitle.set(language, subtitle.getValue());
        form.aspects = aspectBox.getValue();
        form.parents = parents.getValue();
        form.hiddenParents = hidden.getValue();
        form.siblings = siblings.getValue();

    }

    private static int number(String text, int fallback) {
        try { return Integer.parseInt(text.strip()); } catch (NumberFormatException invalid) { return fallback; }
    }

    // ---- Header -----------------------------------------------------------------------------------------------

    @Override protected List<Button> buttons() {
        return List.of(new Button("save", Component.translatable("book.tcmodernlib.editor.save"), true),
                new Button("cancel", Component.translatable("book.tcmodernlib.editor.cancel"), false),
                new Button("json", Component.translatable("book.tcmodernlib.editor.json"), false));
    }

    @Override protected void pressed(String button) {
        commit();
        switch (button) {
            case "save" -> save();
            case "cancel" -> onClose();
            case "json" -> {
                try {
                    minecraft.setScreen(new JsonEditorScreen(this, form.json(), json -> replace(ResearchForm.of(json)), this::send));
                } catch (RuntimeException invalid) {
                    status(String.valueOf(invalid.getMessage()), true);
                }
            }
            default -> { }
        }
    }

    private void save() {
        JsonObject json;
        try {
            json = form.json();
            if (runtime != null) ResearchJson.parse(json, RecipeLoader.hostItems(runtime));
        } catch (RuntimeException invalid) {
            status(Component.translatable("book.tcmodernlib.editor.invalid", String.valueOf(invalid.getMessage())).getString(), true);
            return;
        }
        send(json);
    }

    /** Sends a validated file to the server and returns to the book, which refreshes when the server reloads. */
    private void send(JsonObject json) {
        BookNetwork.send(new BookNetwork.Action(BookNetwork.Kind.SAVE, id, false, json.toString(), 0, 0));
        minecraft.setScreen(parent);
    }

    // ---- Body -------------------------------------------------------------------------------------------------

    @Override protected void renderBody(GuiGraphics g, int mouseX, int mouseY) {
        card(g, left + LEFT_X - 4, top + 30, COLUMN_W + 8, 222);
        card(g, left + MID_X - 4, top + 30, MID_W + 8, 222);
        card(g, left + RIGHT_X - 4, top + 30, COLUMN_W + 8, 222);
        label(g, "book.tcmodernlib.editor.key", LEFT_X, rowY(0));
        label(g, "book.tcmodernlib.editor.category", LEFT_X, rowY(1));
        String categoryName = form.categoryKey.isEmpty() ? "—" : runtime == null ? form.categoryKey : TcThaumonomicon.categoryName(runtime, form.categoryKey);
        boolean hotCategory = inside(mouseX, mouseY, LEFT_X, rowY(1) + 9, COLUMN_W, 14);
        round(g, left + LEFT_X, top + rowY(1) + 9, COLUMN_W, 14, 7, hotCategory ? 0x33FFFFFF : 0x1FFFFFFF);
        text(g, font, clip(font, categoryName + " ⟳", COLUMN_W - 10), left + LEFT_X + 6, top + rowY(1) + 12, TEXT);
        label(g, "book.tcmodernlib.editor.position", LEFT_X, rowY(2));
        label(g, "book.tcmodernlib.editor.complexity", LEFT_X, rowY(3));
        int segment = COLUMN_W / 3;
        for (int i = 1; i <= 3; i++) {
            int sx = left + LEFT_X + (i - 1) * segment;
            round(g, sx + 1, top + rowY(3) + 9, segment - 2, 14, 7, form.complexity == i ? ACCENT : 0x1FFFFFFF);
            centered(g, font, Component.literal(String.valueOf(i)), sx + segment / 2, top + rowY(3) + 12, form.complexity == i ? 0xFF14121F : MUTED);
        }
        label(g, "book.tcmodernlib.editor.icon", LEFT_X, rowY(4));
        preview(g, icon.getValue(), left + LEFT_X + COLUMN_W - 17, top + rowY(4) + 8);
        label(g, "book.tcmodernlib.editor.warp", LEFT_X, rowY(5));
        label(g, "book.tcmodernlib.editor.flags", LEFT_X, rowY(6));
        for (int i = 0; i < ResearchForm.FLAGS.size(); i++) {
            String flag = ResearchForm.FLAGS.get(i);
            int fx = left + LEFT_X + (i % 3) * 47, fy = top + rowY(6) + 10 + (i / 3) * 15;
            boolean on = form.flags.contains(flag);
            round(g, fx, fy, 45, 13, 6.5F, on ? alpha(ACCENT, 0.55F) : 0x1AFFFFFF);
            var labelText = Component.translatable("book.tcmodernlib.flag." + flag);
            g.drawString(font, clip(font, labelText.getString(), 41), fx + 22 - Math.min(41, font.width(labelText)) / 2, fy + 3, on ? TEXT : FAINT, false);
        }

        label(g, "book.tcmodernlib.editor.language", MID_X, rowY(0));
        languageButton(g, mouseX, mouseY, MID_X, rowY(0) + 9, MID_W, language);
        label(g, form.name.isRaw() ? "book.tcmodernlib.editor.shared_text" : "book.tcmodernlib.editor.name", MID_X, rowY(1));
        label(g, form.subtitle.isRaw() ? "book.tcmodernlib.editor.shared_text" : "book.tcmodernlib.editor.subtitle", MID_X, rowY(2));
        label(g, "book.tcmodernlib.editor.aspects", MID_X, rowY(4));
        pickerButton(g, MID_X + MID_W - 18, rowY(4) + 9);
        aspectRow(g, aspectBox.getValue(), left + MID_X, top + rowY(4) + 25);
        label(g, "book.tcmodernlib.editor.parents", MID_X, 172);
        label(g, "book.tcmodernlib.editor.hidden_parents", MID_X, 197);
        label(g, "book.tcmodernlib.editor.siblings", MID_X, 222);

        label(g, "book.tcmodernlib.editor.text", RIGHT_X, 34);
        boolean hot = inside(mouseX, mouseY, RIGHT_X, 47, COLUMN_W, 190);
        round(g, left + RIGHT_X, top + 47, COLUMN_W, 190, 5, hot ? 0x663C2B49 : WELL);
        text(g, font, Component.translatable("book.tcmodernlib.writer.open"), left + RIGHT_X + 6, top + 54, ACCENT_LIGHT);
        text(g, font, Component.translatable("book.tcmodernlib.writer.page_count", form.pages.size()), left + RIGHT_X + 6, top + 70, MUTED);
    }

    private void pickerButton(GuiGraphics g, int x, int y) {
        round(g, left + x, top + y, 18, 14, 5, 0x33FFFFFF);
        centered(g, font, Component.literal("+"), left + x + 9, top + y + 3, TEXT);
    }

    @Override protected void renderOverlay(GuiGraphics g, int mouseX, int mouseY) {
        String hint = null;
        if (inside(mouseX, mouseY, LEFT_X + COLUMN_W - 18, rowY(4) + 8, 18, 18)) hint = "book.tcmodernlib.picker.item";
        else if (inside(mouseX, mouseY, MID_X + MID_W - 18, rowY(4) + 9, 18, 14)) hint = "book.tcmodernlib.picker.aspect";
        if (hint != null) g.renderTooltip(font, Component.translatable(hint), mouseX, mouseY);
    }

    private void chooseAspects() {
        commit();
        try {
            minecraft.setScreen(new AspectPickerScreen(this, aspects.values(), form.aspects, result -> form.aspects = result));
        } catch (RuntimeException invalid) {
            status(Component.translatable("book.tcmodernlib.editor.invalid", String.valueOf(invalid.getMessage())).getString(), true);
        }
    }

    /** The item (or texture) a field names, 16 px at {@code x, y}; a red mark when it names nothing. */
    private void preview(GuiGraphics g, String text, int x, int y) {
        round(g, x - 1, y - 1, 18, 18, 4, WELL);
        String value = text.strip();
        if (value.startsWith("texture:")) {
            var texture = ResourceLocation.tryParse(value.substring("texture:".length()));
            if (texture != null) g.blit(texture, x, y, 16, 16, 0F, 0F, 1, 1, 1, 1);
            return;
        }
        var stack = previews.computeIfAbsent(value, unused -> {
            if (runtime == null || value.isEmpty()) return ItemStack.EMPTY;
            try {
                var legacy = RecipeJson.stack(ResearchForm.itemJson(value, null), RecipeLoader.hostItems(runtime), true);
                return runtime.mappings().toHost(LegacyMappings.ITEM_STACK, runtime.stack(legacy), ItemStack.class).orElse(ItemStack.EMPTY);
            } catch (RuntimeException unknown) {
                return ItemStack.EMPTY;
            }
        });
        if (!stack.isEmpty()) g.renderItem(stack, x, y);
        else if (!value.isEmpty()) centered(g, font, Component.literal("?"), x + 8, y + 4, WARN);
    }

    private void aspectRow(GuiGraphics g, String text, int x, int y) {
        int offset = 0;
        for (String part : text.strip().split("[\\s,，;]+")) {
            var pieces = part.split("[:×*x=]", 2);
            if (pieces.length != 2 || offset > MID_W - 30) continue;
            var aspect = aspects.get(pieces[0].toLowerCase(Locale.ROOT));
            if (aspect == null) { g.drawString(font, "?" + pieces[0], x + offset, y + 3, WARN, false); offset += font.width("?" + pieces[0]) + 6; continue; }
            g.pose().pushPose();
            g.pose().translate(x + offset, y, 0);
            g.pose().scale(0.75F, 0.75F, 1);
            AspectIcons.render(g, aspect, 0, 0);
            g.pose().popPose();
            g.drawString(font, pieces[1], x + offset + 13, y + 3, MUTED, false);
            offset += 14 + font.width(pieces[1]) + 6;
        }
    }

    // ---- Input ------------------------------------------------------------------------------------------------

    @Override protected boolean clicked(double x, double y, int button) {
        if (button == 0 && inside(x, y, RIGHT_X, 34, COLUMN_W, 203)) {
            openTextWriter();
            return true;
        }
        if (button == 0 && inside(x, y, LEFT_X + COLUMN_W - 18, rowY(4) + 8, 18, 18)) {
            commit();
            minecraft.setScreen(new ItemPickerScreen(this, form::selectIcon));
            return true;
        }
        if (button == 0 && inside(x, y, MID_X + MID_W - 18, rowY(4) + 9, 18, 14)) {
            chooseAspects();
            return true;
        }
        if (button == 0 && inside(x, y, MID_X, rowY(0) + 9, MID_W, 14)) {
            commit();
            minecraft.setScreen(new ContentLanguageScreen(this, selected -> language = selected));
            return true;
        }
        if (inside(x, y, LEFT_X, rowY(1) + 9, COLUMN_W, 14)) {
            var keys = BookClient.book().stream().map(c -> c.key()).toList();
            if (!keys.isEmpty()) {
                commit();
                int index = keys.indexOf(form.categoryKey);
                form.categoryKey = keys.get(Math.floorMod(index + (button == 1 ? -1 : 1), keys.size()));
            }
            return true;
        }
        int segment = COLUMN_W / 3;
        for (int i = 1; i <= 3; i++) if (inside(x, y, LEFT_X + (i - 1) * segment, rowY(3) + 9, segment, 14)) { form.complexity = i; return true; }
        for (int i = 0; i < ResearchForm.FLAGS.size(); i++) {
            if (!inside(x, y, LEFT_X + (i % 3) * 47, rowY(6) + 10 + (i / 3) * 15, 45, 13)) continue;
            String flag = ResearchForm.FLAGS.get(i);
            if (!form.flags.remove(flag)) form.flags.add(flag);
            return true;
        }
        return false;
    }

    /** Replaces the form shown, discarding unsaved widget values; for scripted editing. */
    public void load(ResearchForm replacement) {
        replace(replacement);
        relayout();
    }

    /** Saves as the header button does. */
    public void submit() {
        commit();
        save();
    }

    /** The form being edited. */
    public ResearchForm current() { return form; }

    void bookPagesChanged() { selected = Math.max(0, Math.min(selected, form.pages.size() - 1)); }

    /** Opens the research pages on the book spread. */
    public void openTextWriter() {
        commit();
        minecraft.setScreen(new BookTextEditorScreen(this, form, selected, language));
    }
}
