package github.com.gengyoubo.tcmodern.lib.client.book.edit;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.LanguageInfo;
import net.minecraft.network.chat.Component;

/** Selects a content language without changing the game's language. */
final class ContentLanguageScreen extends Screen {
    private final Screen parent;
    private final Consumer<String> select;
    private EditBox search;
    private int scroll;

    ContentLanguageScreen(Screen parent, Consumer<String> select) {
        super(Component.translatable("book.tcmodernlib.editor.language"));
        this.parent = parent;
        this.select = select;
    }

    @Override protected void init() {
        search = new EditBox(font, width / 2 - 150, 35, 300, 20, Component.translatable("book.tcmodernlib.editor.language_search"));
        search.setHint(Component.translatable("book.tcmodernlib.editor.language_search"));
        search.setResponder(ignored -> scroll = 0);
        addRenderableWidget(search);
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), ignored -> onClose())
                .bounds(width / 2 - 75, height - 28, 150, 20).build());
    }

    private List<Map.Entry<String, LanguageInfo>> languages() {
        String query = search.getValue().strip().toLowerCase(Locale.ROOT);
        return minecraft.getLanguageManager().getLanguages().entrySet().stream()
                .filter(entry -> entry.getKey().toLowerCase(Locale.ROOT).contains(query)
                        || entry.getValue().toComponent().getString().toLowerCase(Locale.ROOT).contains(query)).toList();
    }

    private int visibleRows() { return Math.max(1, (height - 94) / 20); }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 15, 0xFFFFFF);
        var choices = languages();
        scroll = Math.min(scroll, Math.max(0, choices.size() - visibleRows()));
        for (int i = 0; i < visibleRows() && scroll + i < choices.size(); i++) {
            var entry = choices.get(scroll + i);
            int y = 62 + i * 20;
            if (mouseX >= width / 2 - 150 && mouseX < width / 2 + 150 && mouseY >= y && mouseY < y + 19)
                g.fill(width / 2 - 150, y, width / 2 + 150, y + 19, 0x66555555);
            g.drawString(font, entry.getValue().toComponent().copy().append(" (" + entry.getKey() + ")"), width / 2 - 145, y + 5, 0xFFFFFF);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && x >= width / 2 - 150 && x < width / 2 + 150 && y >= 62 && y < 62 + visibleRows() * 20) {
            int index = scroll + (int) ((y - 62) / 20);
            var choices = languages();
            if (index < choices.size()) { select.accept(choices.get(index).getKey()); minecraft.setScreen(parent); return true; }
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseScrolled(double x, double y, double delta) {
        scroll = Math.max(0, Math.min(Math.max(0, languages().size() - visibleRows()), scroll + (delta > 0 ? -3 : 3)));
        return true;
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
