package github.com.gengyoubo.tcmodern.lib.client.book.edit;

import com.google.gson.JsonObject;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Search the same creative inventory variants available to item browsers, without requiring JEI. */
final class ItemPickerScreen extends Screen {
    private static final int CELL = 20;
    private final Screen parent;
    private final Consumer<JsonObject> select;
    private Consumer<ItemStack> stackSelection;
    private Runnable cancel = () -> { };
    private List<ItemStack> items = List.of(), filtered = List.of();
    private EditBox search;
    private int scroll, columns, rows, gridX;

    ItemPickerScreen(Screen parent, Consumer<JsonObject> select) {
        super(Component.translatable("book.tcmodernlib.picker.item"));
        this.parent = parent; this.select = select;
    }

    static ItemPickerScreen forStack(Screen parent, Consumer<ItemStack> select, Runnable cancel) {
        var picker = new ItemPickerScreen(parent, ignored -> { });
        picker.stackSelection = select; picker.cancel = cancel;
        return picker;
    }

    @Override protected void init() {
        columns = Math.max(1, Math.min(15, (width - 30) / CELL));
        rows = Math.max(1, (height - 100) / CELL);
        gridX = (width - columns * CELL) / 2;
        if (minecraft.level != null && minecraft.player != null) {
            CreativeModeTabs.tryRebuildTabContents(minecraft.level.enabledFeatures(), minecraft.player.canUseGameMasterBlocks(), minecraft.level.registryAccess());
            var unique = new LinkedHashMap<String, ItemStack>();
            for (var tab : CreativeModeTabs.allTabs()) for (var stack : tab.getSearchTabDisplayItems()) {
                var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (!stack.isEmpty() && id != null) unique.putIfAbsent(id + "|" + String.valueOf(stack.getTag()), stack.copyWithCount(1));
            }
            // Include registered items hidden from creative tabs as well as their exposed variants.
            for (var item : ForgeRegistries.ITEMS) {
                var stack = item.getDefaultInstance();
                var id = ForgeRegistries.ITEMS.getKey(item);
                if (!stack.isEmpty() && id != null) unique.putIfAbsent(id + "|" + String.valueOf(stack.getTag()), stack);
            }
            items = List.copyOf(unique.values());
        }
        search = new EditBox(font, width / 2 - 140, 34, 280, 20, Component.translatable("book.tcmodernlib.picker.search_items"));
        search.setHint(Component.translatable("book.tcmodernlib.picker.search_items"));
        search.setResponder(ignored -> filter());
        addRenderableWidget(search);
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), ignored -> onClose()).bounds(width / 2 - 75, height - 26, 150, 20).build());
        filter();
    }

    private void filter() {
        String query = search.getValue().strip().toLowerCase(Locale.ROOT);
        filtered = items.stream().filter(stack -> stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)
                || String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem())).toLowerCase(Locale.ROOT).contains(query)).toList();
        scroll = 0;
    }
    private int maxScroll() { return Math.max(0, (filtered.size() + columns - 1) / columns - rows); }
    private int index(double x, double y) {
        int col = (int) Math.floor((x - gridX) / CELL), row = (int) Math.floor((y - 60) / CELL);
        return col < 0 || col >= columns || row < 0 || row >= rows ? -1 : (scroll + row) * columns + col;
    }
    static JsonObject itemJson(ItemStack stack) {
        var result = new JsonObject();
        result.addProperty("item", Objects.requireNonNull(ForgeRegistries.ITEMS.getKey(stack.getItem())).toString());
        if (stack.hasTag()) result.addProperty("nbt", stack.getTag().toString());
        return result;
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float tick) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        for (int i = 0; i < columns * rows; i++) {
            int index = scroll * columns + i;
            if (index >= filtered.size()) break;
            int x = gridX + i % columns * CELL, y = 60 + i / columns * CELL;
            g.fill(x, y, x + 19, y + 19, 0x66333333);
            g.renderItem(filtered.get(index), x + 1, y + 1);
        }
        super.render(g, mouseX, mouseY, tick);
        int index = index(mouseX, mouseY);
        if (index >= 0 && index < filtered.size()) g.renderTooltip(font, filtered.get(index), mouseX, mouseY);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        int index = index(x, y);
        if (button == 0 && index >= 0 && index < filtered.size()) {
            var stack = filtered.get(index);
            if (stackSelection != null) stackSelection.accept(stack.copy()); else select.accept(itemJson(stack));
            minecraft.setScreen(parent); return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double delta) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll + (delta > 0 ? -1 : 1))); return true;
    }
    @Override public void onClose() { cancel.run(); minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
