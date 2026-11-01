package github.com.gengyoubo.tcmodern.lib.client.book.edit;

import github.com.gengyoubo.tcmodern.api.aspect.TcAspect;
import github.com.gengyoubo.tcmodern.lib.client.AspectIcons;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Search and edit a set of original TC4 aspects. */
final class AspectPickerScreen extends Screen {
    private final Screen parent;
    private final List<TcAspect> aspects;
    private final Consumer<String> save;
    private final LinkedHashMap<String, Integer> amounts = new LinkedHashMap<>();
    private List<TcAspect> filtered = List.of();
    private EditBox search;
    private int scroll;

    AspectPickerScreen(Screen parent, Collection<TcAspect> aspects, String current, Consumer<String> save) {
        super(Component.translatable("book.tcmodernlib.picker.aspect"));
        this.parent = parent; this.aspects = List.copyOf(aspects); this.save = save;
        ResearchForm.aspectsJson(current).entrySet().forEach(entry -> amounts.put(entry.getKey(), entry.getValue().getAsInt()));
    }
    @Override protected void init() {
        search = new EditBox(font, width / 2 - 140, 34, 280, 20, Component.translatable("book.tcmodernlib.picker.search_aspects"));
        search.setHint(Component.translatable("book.tcmodernlib.picker.search_aspects"));
        search.setResponder(ignored -> filter());
        addRenderableWidget(search);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> {
            var values = new ArrayList<String>();
            amounts.forEach((tag, amount) -> { if (amount > 0) values.add(tag + ":" + amount); });
            save.accept(String.join(" ", values)); minecraft.setScreen(parent);
        }).bounds(width / 2 + 5, height - 26, 135, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), ignored -> onClose())
                .bounds(width / 2 - 140, height - 26, 135, 20).build());
        filter();
    }
    private void filter() {
        String query = search.getValue().strip().toLowerCase(Locale.ROOT);
        filtered = aspects.stream().filter(a -> a.tag().toLowerCase(Locale.ROOT).contains(query)
                || AspectIcons.name(a.tag()).toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparing(a -> AspectIcons.name(a.tag()))).toList();
        scroll = 0;
    }
    private int rows() { return Math.max(1, (height - 112) / 22); }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float tick) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("book.tcmodernlib.picker.aspect_hint"), width / 2, height - 40, 0xAAAAAA);
        for (int i = 0; i < rows() && scroll + i < filtered.size(); i++) {
            var aspect = filtered.get(scroll + i); int x = width / 2 - 140, y = 59 + i * 22;
            g.fill(x, y, x + 280, y + 20, 0x66333333);
            AspectIcons.render(g, aspect, x + 2, y + 2);
            g.drawString(font, font.plainSubstrByWidth(AspectIcons.name(aspect.tag()) + " (" + aspect.tag() + ")", 205), x + 22, y + 6, 0xFFFFFF);
            g.drawString(font, "−  " + amounts.getOrDefault(aspect.tag(), 0) + "  +", x + 222, y + 6, 0xFFFFFF);
        }
        super.render(g, mouseX, mouseY, tick);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        int row = (int) Math.floor((y - 59) / 22), index = scroll + row;
        if (x >= width / 2 - 140 && x < width / 2 + 140 && row >= 0 && row < rows() && index < filtered.size() && (button == 0 || button == 1)) {
            String tag = filtered.get(index).tag();
            boolean decrease = button == 1 || x >= width / 2 + 82 && x < width / 2 + 99;
            int step = hasShiftDown() ? 10 : 1;
            int amount = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, (long) amounts.getOrDefault(tag, 0) + (decrease ? -step : step)));
            if (amount == 0) amounts.remove(tag); else amounts.put(tag, amount);
            return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double delta) {
        scroll = Math.max(0, Math.min(Math.max(0, filtered.size() - rows()), scroll + (delta > 0 ? -1 : 1))); return true;
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
