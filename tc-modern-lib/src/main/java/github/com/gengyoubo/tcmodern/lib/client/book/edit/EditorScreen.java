package github.com.gengyoubo.tcmodern.lib.client.book.edit;

import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import static github.com.gengyoubo.tcmodern.lib.client.ModernUi.*;

/**
 * Shared frame of the Thaumonomicon editors: the same glass window and canvas scaling as the book, header buttons,
 * text fields drawn as rounded wells and a status line. Subclasses lay out widgets in {@link #build()}.
 */
abstract class EditorScreen extends Screen {
    static final int WIDTH = 460, HEIGHT = 280;
    protected final Screen parent;
    protected final LegacyRuntime runtime;
    protected int left, top;
    private float scale = 1;
    private final List<int[]> wells = new ArrayList<>();
    private String status;
    private boolean error;

    protected record Button(String id, Component label, boolean primary) {}

    EditorScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;
        this.runtime = TcModernApi.runtime().orElse(null);
    }

    @Override protected final void init() {
        scale = width < 64 || height < 64 ? 1 : Math.min(1F, Math.min((width - 4F) / WIDTH, (height - 4F) / HEIGHT));
        width = Math.round(width / scale);
        height = Math.round(height / scale);
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        wells.clear();
        build();
    }

    /** Adds this editor's widgets; called again after a layout change by {@link #rebuild()}. */
    protected abstract void build();
    protected abstract List<Button> buttons();
    protected abstract void pressed(String id);
    protected abstract void renderBody(GuiGraphics g, int mouseX, int mouseY);
    /** Copies widget values into the editor's model before the widgets are rebuilt or saved. */
    protected abstract void commit();

    protected void rebuild() {
        commit();
        relayout();
    }

    /** Recreates the widgets from the model on the current canvas, without scaling again. */
    protected void relayout() {
        clearWidgets();
        wells.clear();
        setFocused(null);
        build();
    }

    protected void status(String message, boolean failure) {
        status = message;
        error = failure;
    }

    // ---- Widgets ----------------------------------------------------------------------------------------------

    /** A single-line field in a rounded well at canvas offset {@code x, y}. */
    protected EditBox field(int x, int y, int w, String value, String hint) {
        wells.add(new int[]{left + x, top + y, w, 14});
        var box = new EditBox(font, left + x + 4, top + y + 3, w - 8, 10, Component.literal(hint));
        box.setBordered(false);
        box.setMaxLength(4096);
        box.setTextColor(TEXT);
        box.setValue(value);
        if (!hint.isEmpty()) box.setHint(Component.literal(hint).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        return addRenderableWidget(box);
    }

    /** A multi-line field styled like the rest of the editor. */
    protected MultiLineEditBox area(int x, int y, int w, int h, String value, String hint) {
        var area = new MultiLineEditBox(font, left + x, top + y, w, h, Component.literal(hint), Component.literal(hint)) {
            @Override protected void renderBackground(GuiGraphics g) {
                round(g, getX(), getY(), width, height, 5, isFocused() ? 0x60000000 : WELL);
                if (isFocused()) ring(g, getX(), getY(), width, height, 5, 1, alpha(ACCENT, 0.8F));
            }
        };
        area.setCharacterLimit(16384);
        area.setValue(value);
        return addRenderableWidget(area);
    }

    // ---- Rendering --------------------------------------------------------------------------------------------

    private record Placed(Button button, int x, int w) {}

    private List<Placed> placed() {
        var result = new ArrayList<Placed>();
        int x = WIDTH - 12;
        for (var button : buttons()) {
            int w = font.width(button.label()) + 18;
            x -= w;
            result.add(new Placed(button, x, w));
            x -= 5;
        }
        return result;
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = Math.round(mouseX / scale), y = Math.round(mouseY / scale);
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1);
        renderBackground(g);
        window(g, left, top, WIDTH, HEIGHT);
        hex(g, left + 18, top + 15, 6.5F, ACCENT_LIGHT, ACCENT_DEEP);
        hex(g, left + 18, top + 15, 2.5F, 0xFF1A1630);
        text(g, font, title, left + 30, top + 11, TEXT);
        for (var place : placed()) {
            boolean hot = x >= left + place.x() && x < left + place.x() + place.w() && y >= top + 8 && y < top + 23;
            if (place.button().primary()) round(g, left + place.x(), top + 8, place.w(), 15, 7.5F, hot ? ACCENT_LIGHT : ACCENT);
            else round(g, left + place.x(), top + 8, place.w(), 15, 7.5F, hot ? 0x33FFFFFF : 0x1FFFFFFF);
            text(g, font, place.button().label(), left + place.x() + 9, top + 12, place.button().primary() ? 0xFF14121F : TEXT);
        }
        for (var well : wells) round(g, well[0], well[1], well[2], well[3], 5, WELL);
        renderBody(g, x, y);
        super.render(g, x, y, partialTick);
        if (status != null) {
            int w = Math.min(WIDTH - 24, font.width(status) + 16);
            round(g, left + 12, top + HEIGHT - 22, w, 15, 7.5F, error ? alpha(WARN, 0.25F) : alpha(GOOD, 0.22F));
            text(g, font, clip(font, status, w - 12), left + 20, top + HEIGHT - 18, error ? 0xFFFFB3A8 : GOOD);
        }
        renderOverlay(g, x, y);
        g.pose().popPose();
    }

    /** Tooltips and previews drawn above the widgets. */
    protected void renderOverlay(GuiGraphics g, int mouseX, int mouseY) { }

    protected void label(GuiGraphics g, String key, int x, int y) {
        g.drawString(font, Component.translatable(key), left + x, top + y, MUTED, false);
    }

    // ---- Input ------------------------------------------------------------------------------------------------

    protected boolean inside(double x, double y, int bx, int by, int w, int h) {
        return x >= left + bx && x < left + bx + w && y >= top + by && y < top + by + h;
    }

    /** Clicks on the editor's own controls, on canvas coordinates; true when handled. */
    protected boolean clicked(double x, double y, int button) { return false; }

    @Override public boolean mouseClicked(double rawX, double rawY, int button) {
        double x = rawX / scale, y = rawY / scale;
        for (var place : placed())
            if (x >= left + place.x() && x < left + place.x() + place.w() && y >= top + 8 && y < top + 23) {
                pressed(place.button().id());
                return true;
            }
        if (clicked(x, y, button)) return true;
        boolean handled = super.mouseClicked(x, y, button);
        if (!handled) setFocused(null);
        return handled;
    }

    @Override public boolean mouseReleased(double x, double y, int button) { return super.mouseReleased(x / scale, y / scale, button); }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return super.mouseDragged(x / scale, y / scale, button, dx / scale, dy / scale);
    }
    @Override public boolean mouseScrolled(double x, double y, double delta) { return super.mouseScrolled(x / scale, y / scale, delta); }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        // Typing must not fall through to inventory or other key bindings.
        if (getFocused() != null && getFocused().keyPressed(key, scan, modifiers)) return true;
        return super.keyPressed(key, scan, modifiers);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    float canvasScale() { return scale; }
}
