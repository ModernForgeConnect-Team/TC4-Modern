package github.com.gengyoubo.tcmodern.lib.client.book.edit;

import com.mojang.blaze3d.systems.RenderSystem;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspects;
import github.com.gengyoubo.tcmodern.lib.json.RecipeJson;
import github.com.gengyoubo.tcmodern.lib.json.RecipeLoader;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Transactional editor for every kind of research page, using the original book spread. */
final class BookTextEditorScreen extends Screen {
    private static final ResourceLocation BOOK = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_researchbook.png");
    private static final int WIDTH = 384, HEIGHT = 272;
    private final ResearchEditorScreen parent;
    private final ResearchForm form;
    private final String language;
    private final List<ResearchForm.Page> pages = new ArrayList<>();
    private final List<MultiLineEditBox> textBoxes = new ArrayList<>();
    private final Map<String, ItemStack> itemPreviews = new HashMap<>();
    private int first;
    private float scale, left, top;
    private String error;

    BookTextEditorScreen(ResearchEditorScreen parent, ResearchForm form, int first, String language) {
        super(Component.translatable("book.tcmodernlib.writer.title"));
        this.parent = parent; this.form = form; this.first = first; this.language = language;
        form.pages.forEach(page -> pages.add(page.copy()));
    }
    private static String display(String source) { return source.replace("<BR/>", "\n").replace("<BR>", "\n"); }
    private int px(int x) { return Math.round(left + x * scale); }
    private int py(int y) { return Math.round(top + y * scale); }
    private int size(int value) { return Math.max(1, Math.round(value * scale)); }
    private boolean exists(int index) { return index >= 0 && index < pages.size(); }

    @Override protected void init() {
        scale = Math.max(0.1F, Math.min(1.5F, Math.min((width - 8F) / WIDTH, (height - 8F) / HEIGHT)));
        left = (width - WIDTH * scale) / 2F;
        top = (height - HEIGHT * scale) / 2F;
        build();
    }
    private Button button(int x, int y, int w, Component name, Runnable action) {
        return addRenderableWidget(Button.builder(name, b -> action.run()).bounds(px(x), py(y), size(w), size(16)).build());
    }
    private void build() {
        clearWidgets(); setFocused(null); textBoxes.clear();
        addPage(first, 25);
        addPage(first + 1, 200);
        button(25, 238, 24, Component.literal("◀"), () -> change(-2)).active = first > 0;
        button(335, 238, 24, Component.literal("▶"), () -> change(2)).active = first + 2 < pages.size();
        button(78, 238, 106, Component.translatable("gui.cancel"), () -> minecraft.setScreen(parent));
        button(200, 238, 106, Component.translatable("gui.done"), this::apply);
    }
    private void change(int delta) { first = Math.max(0, Math.min(Math.max(0, pages.size() - 1), first + delta)); build(); }
    private void apply() {
        form.pages.clear(); pages.forEach(page -> form.pages.add(page.copy()));
        parent.bookPagesChanged();
        minecraft.setScreen(parent);
    }
    private EditBox field(int x, int y, int width, String value, String hint, java.util.function.Consumer<String> changed) {
        var box = new EditBox(font, px(x), py(y), size(width), size(16), Component.translatable(hint));
        box.setMaxLength(4096); box.setValue(value); box.setHint(Component.translatable(hint)); box.setResponder(changed);
        return addRenderableWidget(box);
    }
    private void addPage(int index, int x) {
        button(x + 119, 14, 18, Component.literal("+"), () -> { pages.add(Math.min(index + 1, pages.size()), new ResearchForm.Page()); build(); });
        if (!exists(index)) {
            button(x, 38, 112, Component.translatable("book.tcmodernlib.writer.add"), () -> { pages.add(new ResearchForm.Page()); build(); });
            return;
        }
        var page = pages.get(index);
        button(x + 140, 14, 18, Component.literal("×"), () -> { pages.remove(index); first = Math.min(first, Math.max(0, pages.size() - 1)); build(); });
        button(x, 38, 158, Component.translatable("book.tcmodernlib.editor.page_type", Component.translatable("book.tcmodernlib.page_type." + page.type)), () -> {
            int pos = ResearchForm.PAGE_TYPES.indexOf(page.type); page.type = ResearchForm.PAGE_TYPES.get((pos + 1) % ResearchForm.PAGE_TYPES.size()); build();
        });
        switch (page.type) {
            case "text" -> addText(page, x);
            case "image" -> addImage(page, x);
            case "aspects" -> {
                field(x, 84, 128, page.aspects, "book.tcmodernlib.editor.aspects", value -> page.aspects = value);
                button(x + 132, 84, 26, Component.literal("+"), () -> pickAspects(page));
            }
            default -> {
                field(x, 84, 128, page.item, "book.tcmodernlib.writer.recipe_item", value -> page.item = value);
                button(x + 132, 84, 26, Component.literal("+"), () -> minecraft.setScreen(new ItemPickerScreen(this, page::selectItem)));
            }
        }
    }
    private void mode(ResearchForm.Text text, int x, int y) {
        button(x, y, 158, Component.translatable(text.isRaw() ? "book.tcmodernlib.writer.mode_key" : "book.tcmodernlib.writer.mode_inline"), () -> {
            text.setRawMode(!text.isRaw()); build();
        });
    }
    private void addText(ResearchForm.Page page, int x) {
        mode(page.text, x, 58);
        if (page.text.isRaw()) field(x, 80, 158, page.text.get(language), "book.tcmodernlib.writer.key_hint", value -> page.text.set(language, value));
        else {
            var box = new MultiLineEditBox(font, px(x), py(80), size(158), size(112), Component.empty(), Component.translatable("book.tcmodernlib.writer.placeholder")) {
                @Override protected void renderBackground(GuiGraphics g) { g.fill(getX(), getY(), getX() + width, getY() + height, isFocused() ? 0x184B2F16 : 0x084B2F16); }
                @Override protected void renderContents(GuiGraphics g, int mx, int my, float tick) {
                    g.flush(); RenderSystem.setShaderColor(0.19F, 0.12F, 0.07F, 1F);
                    super.renderContents(g, mx, my, tick); g.flush(); RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
                }
                @Override protected void renderDecorations(GuiGraphics g) { }
            };
            box.setCharacterLimit(16384); box.setValue(display(page.text.get(language)));
            box.setValueListener(value -> page.text.set(language, value.replace("\n", "<BR>")));
            textBoxes.add(addRenderableWidget(box));
        }
        field(x, 211, 158, page.requires, "book.tcmodernlib.writer.requires_hint", value -> page.requires = value);
    }
    private void addImage(ResearchForm.Page page, int x) {
        field(x, 76, 128, page.image, "book.tcmodernlib.writer.image_hint", value -> page.image = value);
        button(x + 132, 76, 26, Component.literal("+"), () -> minecraft.setScreen(new TexturePickerScreen(this, value -> page.image = value)));
        mode(page.caption, x, 154);
        field(x, 176, 158, page.caption.get(language), page.caption.isRaw() ? "book.tcmodernlib.writer.key_hint" : "book.tcmodernlib.editor.caption", value -> page.caption.set(language, value));
    }
    private void pickAspects(ResearchForm.Page page) {
        try { minecraft.setScreen(new AspectPickerScreen(this, TcModernApi.runtime().map(TcAspects::snapshot).orElse(List.of()), page.aspects, value -> page.aspects = value)); }
        catch (RuntimeException invalid) { error = invalid.getMessage(); }
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float tick) {
        renderBackground(g);
        g.pose().pushPose(); g.pose().translate(left, top, 0); g.pose().scale(scale, scale, 1);
        if (minecraft.getResourceManager().getResource(BOOK).isPresent()) {
            RenderSystem.enableBlend(); g.blit(BOOK, 0, 0, WIDTH, HEIGHT, 0, 0, 512, 362, 512, 512); RenderSystem.disableBlend();
        } else { g.fill(0, 0, WIDTH, HEIGHT, 0xFF4B291A); g.fill(16, 8, 186, 264, 0xFFEAD8AE); g.fill(198, 8, 368, 264, 0xFFEAD8AE); }
        heading(g, first, 70); heading(g, first + 1, 245);
        image(g, first, 25); image(g, first + 1, 200);
        item(g, first, 25); item(g, first + 1, 200);
        labels(g, first, 25); labels(g, first + 1, 200);
        if (error != null) g.drawString(font, font.plainSubstrByWidth(error, 300), 40, 226, 0xFFAA0000, false);
        g.pose().popPose();
        super.render(g, mouseX, mouseY, tick);
    }
    private void heading(GuiGraphics g, int index, int x) {
        if (exists(index)) g.drawString(font, Component.translatable("book.tcmodernlib.writer.page", index + 1), x, 20, 0xFF3B2414, false);
    }
    private void image(GuiGraphics g, int index, int x) {
        if (!exists(index) || !pages.get(index).type.equals("image")) return;
        var resource = ResourceLocation.tryParse(pages.get(index).image);
        if (resource != null && minecraft.getResourceManager().getResource(resource).isPresent())
            g.blit(resource, x + 52, 99, 54, 48, 0F, 0F, 1, 1, 1, 1);
    }
    private void labels(GuiGraphics g, int index, int x) {
        if (!exists(index)) return;
        var page = pages.get(index);
        String key = switch (page.type) {
            case "text" -> "book.tcmodernlib.writer.requires_hint";
            case "image" -> "book.tcmodernlib.writer.image_hint";
            case "aspects" -> "book.tcmodernlib.editor.aspects";
            case "smelting" -> "book.tcmodernlib.editor.input";
            default -> "book.tcmodernlib.writer.recipe_item";
        };
        int y = page.type.equals("text") ? 201 : page.type.equals("image") ? 66 : 73;
        g.drawString(font, font.plainSubstrByWidth(Component.translatable(key).getString(), 158), x, y, 0xFF563C26, false);
    }
    private void item(GuiGraphics g, int index, int x) {
        if (!exists(index)) return;
        var page = pages.get(index);
        if (page.type.equals("text") || page.type.equals("image") || page.type.equals("aspects") || page.item.isBlank()) return;
        var stack = itemPreviews.computeIfAbsent(page.item, value -> TcModernApi.runtime().map(runtime -> {
            try {
                var legacy = RecipeJson.stack(ResearchForm.itemJson(value, null), RecipeLoader.hostItems(runtime), true);
                return runtime.mappings().toHost(LegacyMappings.ITEM_STACK, runtime.stack(legacy), ItemStack.class).orElse(ItemStack.EMPTY);
            } catch (RuntimeException invalid) { return ItemStack.EMPTY; }
        }).orElse(ItemStack.EMPTY));
        if (!stack.isEmpty()) g.renderItem(stack, x + 71, 115);
    }
    @Override public void tick() { textBoxes.forEach(MultiLineEditBox::tick); }
    @Override public void onClose() { apply(); }
    @Override public boolean isPauseScreen() { return false; }
}
