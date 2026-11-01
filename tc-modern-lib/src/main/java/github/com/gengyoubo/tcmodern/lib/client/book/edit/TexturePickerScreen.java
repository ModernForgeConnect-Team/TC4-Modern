package github.com.gengyoubo.tcmodern.lib.client.book.edit;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Chooses an installed texture without embedding or redistributing the resource. */
final class TexturePickerScreen extends Screen {
    private final Screen parent;
    private final Consumer<String> select;
    private final Runnable cancel;
    private List<ResourceLocation> all = List.of(), filtered = List.of();
    private EditBox search;
    private int scroll;
    TexturePickerScreen(Screen parent, Consumer<String> select) {
        this(parent, select, () -> { });
    }
    TexturePickerScreen(Screen parent, Consumer<String> select, Runnable cancel) {
        super(Component.translatable("book.tcmodernlib.writer.select_image"));
        this.parent = parent; this.select = select; this.cancel = cancel;
    }
    @Override protected void init() {
        all = minecraft.getResourceManager().listResources("textures", id -> id.getPath().endsWith(".png")).keySet().stream().sorted().toList();
        search = new EditBox(font, width / 2 - 145, 34, 290, 20, Component.translatable("book.tcmodernlib.writer.search_image"));
        search.setHint(Component.translatable("book.tcmodernlib.writer.search_image"));
        search.setResponder(ignored -> filter()); addRenderableWidget(search);
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(width / 2 - 70, height - 26, 140, 20).build());
        filter();
    }
    private void filter() { var query = search.getValue().toLowerCase(Locale.ROOT).strip(); filtered = all.stream().filter(id -> id.toString().toLowerCase(Locale.ROOT).contains(query)).toList(); scroll = 0; }
    private int rows() { return Math.max(1, (height - 94) / 22); }
    @Override public void render(GuiGraphics g, int x, int y, float tick) {
        renderBackground(g); g.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        for (int i = 0; i < rows() && scroll + i < filtered.size(); i++) {
            var id = filtered.get(scroll + i); int px = width / 2 - 145, py = 60 + i * 22;
            g.fill(px, py, px + 290, py + 21, 0x66333333);
            g.blit(id, px + 2, py + 2, 16, 16, 0F, 0F, 1, 1, 1, 1);
            g.drawString(font, font.plainSubstrByWidth(id.toString(), 265), px + 22, py + 6, 0xFFFFFF);
        }
        super.render(g, x, y, tick);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        int row = (int) Math.floor((y - 60) / 22), index = scroll + row;
        if (button == 0 && x >= width / 2 - 145 && x < width / 2 + 145 && row >= 0 && row < rows() && index < filtered.size()) {
            select.accept(filtered.get(index).toString()); minecraft.setScreen(parent); return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double delta) { scroll = Math.max(0, Math.min(Math.max(0, filtered.size() - rows()), scroll + (delta > 0 ? -1 : 1))); return true; }
    @Override public void onClose() { cancel.run(); minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
