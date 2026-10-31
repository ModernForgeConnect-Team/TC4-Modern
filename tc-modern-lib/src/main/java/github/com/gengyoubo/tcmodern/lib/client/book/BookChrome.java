package github.com.gengyoubo.tcmodern.lib.client.book;

import com.mojang.blaze3d.systems.RenderSystem;
import github.com.gengyoubo.tcmodern.lib.client.ModernUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * The original Thaumonomicon's chrome for the modern book screen: the wooden runic frame (gui_research.png), the violet
 * starfield (gui_researchback.png), square wooden plates and flat panels, with no drop shadows or glass gradients.
 * Shapes that already match the original (nodes, hexes, lines) are delegated to {@link ModernUi}.
 */
public final class BookChrome {
    public static final int TEXT = 0xFFFFFFFF, MUTED = 0xFFD2C2E0, FAINT = 0xFF9C8AB0;
    public static final int ACCENT = 0xFFA848E0, ACCENT_LIGHT = 0xFFE6BCFF, ACCENT_DEEP = 0xFF5C1F8A;
    public static final int GOLD = 0xFFF2C84B, GOOD = 0xFF8ADB6A, WARN = 0xFFFF6A5A, SHADOW = 0x00000000;
    public static final int SURFACE = 0x33FFFFFF, SURFACE_HOVER = 0x55FFFFFF, WELL = 0x99000000, HIGHLIGHT = 0x55FFFFFF, DIVIDER = 0x44FFFFFF;
    public static final int PANEL = 0xD8140A1C, PANEL_EDGE = 0xFF6B4A2B, PLATE = 0xE0221409, PLATE_HOT = 0xF0382418, PLATE_EDGE = 0xFF8A6036;
    public static final int PANEL_TOP = PANEL, PANEL_BOTTOM = PANEL;

    private static final ResourceLocation FRAME = BookArt.tc("textures/gui/gui_research.png"),
            STARS = BookArt.tc("textures/gui/gui_researchback.png");
    private static final int CORNER = 16, FRAME_TEX = 256, STAR_TEX = 512, STAR_TILE = 256;

    private BookChrome() {}

    // ---- Delegates to the shared shape helpers ----------------------------------------------------------------

    public static int alpha(int color, float factor) { return ModernUi.alpha(color, factor); }
    public static int mix(int from, int to, float t) { return ModernUi.mix(from, to, t); }
    public static float approach(float value, float target, float seconds, float speed) { return ModernUi.approach(value, target, seconds, speed); }
    public static void round(GuiGraphics g, float x, float y, float w, float h, float r, int color) { ModernUi.round(g, x, y, w, h, r, color); }
    public static void round(GuiGraphics g, float x, float y, float w, float h, float r, int top, int bottom) { ModernUi.round(g, x, y, w, h, r, top, bottom); }
    public static void ring(GuiGraphics g, float x, float y, float w, float h, float r, float width, int color) { ModernUi.ring(g, x, y, w, h, r, width, color); }
    public static void circle(GuiGraphics g, float cx, float cy, float radius, int color) { ModernUi.circle(g, cx, cy, radius, color); }
    public static void hex(GuiGraphics g, float cx, float cy, float size, int color) { ModernUi.hex(g, cx, cy, size, color); }
    public static void hexRing(GuiGraphics g, float cx, float cy, float size, float width, int color) { ModernUi.hexRing(g, cx, cy, size, width, color); }
    public static void line(GuiGraphics g, float x1, float y1, float x2, float y2, float width, int color) { ModernUi.line(g, x1, y1, x2, y2, width, color); }

    /** The original book has no drop shadows. */
    public static void shadow(GuiGraphics g, float x, float y, float w, float h, float r, int layers, float strength) { }

    // ---- Text -------------------------------------------------------------------------------------------------

    public static void text(GuiGraphics g, net.minecraft.client.gui.Font font, net.minecraft.network.chat.Component text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, true);
    }

    public static void text(GuiGraphics g, net.minecraft.client.gui.Font font, String text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, true);
    }

    public static void centered(GuiGraphics g, net.minecraft.client.gui.Font font, net.minecraft.network.chat.Component text, int x, int y, int color) {
        g.drawString(font, text, x - font.width(text) / 2, y, color, true);
    }

    public static String clip(net.minecraft.client.gui.Font font, String text, int width) { return ModernUi.clip(font, text, width); }

    public static void label(GuiGraphics g, net.minecraft.client.gui.Font font, net.minecraft.network.chat.Component text, int x, int y) {
        g.drawString(font, text, x, y, MUTED, true);
    }

    // ---- Frames and panels -------------------------------------------------------------------------------------

    /** The wooden runic frame, nine-sliced from the original texture; its interior is transparent. */
    public static void frame(GuiGraphics g, int x, int y, int w, int h) {
        RenderSystem.enableBlend();
        int c = CORNER, t = FRAME_TEX;
        int mw = w - 2 * c, mh = h - 2 * c, srcMid = t - 2 * c;
        g.blit(FRAME, x, y, c, c, 0, 0, c, c, t, t);
        g.blit(FRAME, x + w - c, y, c, c, t - c, 0, c, c, t, t);
        g.blit(FRAME, x, y + h - c, c, c, 0, t - c, c, c, t, t);
        g.blit(FRAME, x + w - c, y + h - c, c, c, t - c, t - c, c, c, t, t);
        g.blit(FRAME, x + c, y, mw, c, c, 0, srcMid, c, t, t);
        g.blit(FRAME, x + c, y + h - c, mw, c, c, t - c, srcMid, c, t, t);
        g.blit(FRAME, x, y + c, c, mh, 0, c, c, srcMid, t, t);
        g.blit(FRAME, x + w - c, y + c, c, mh, t - c, c, c, srcMid, t, t);
    }

    /** The violet starfield tiled over a rectangle, anchored at {@code ox, oy} so it scrolls with the map. */
    public static void starfield(GuiGraphics g, int x, int y, int w, int h, float ox, float oy) {
        if (w <= 0 || h <= 0) return;
        RenderSystem.enableBlend();
        float scale = (float) STAR_TEX / STAR_TILE;
        float phaseX = ((ox % STAR_TILE) + STAR_TILE) % STAR_TILE, phaseY = ((oy % STAR_TILE) + STAR_TILE) % STAR_TILE;
        for (float ty = y - phaseY - STAR_TILE; ty < y + h; ty += STAR_TILE)
            for (float tx = x - phaseX - STAR_TILE; tx < x + w; tx += STAR_TILE) {
                int x0 = Math.max(x, Math.round(tx)), y0 = Math.max(y, Math.round(ty));
                int x1 = Math.min(x + w, Math.round(tx) + STAR_TILE), y1 = Math.min(y + h, Math.round(ty) + STAR_TILE);
                if (x1 <= x0 || y1 <= y0) continue;
                g.blit(STARS, x0, y0, x1 - x0, y1 - y0, (x0 - Math.round(tx)) * scale, (y0 - Math.round(ty)) * scale,
                        Math.round((x1 - x0) * scale), Math.round((y1 - y0) * scale), STAR_TEX, STAR_TEX);
            }
    }

    /** The whole book: starfield behind the wooden frame. */
    public static void window(GuiGraphics g, int x, int y, int w, int h) {
        starfield(g, x + 8, y + 8, w - 16, h - 16, 0, 0);
        frame(g, x, y, w, h);
    }

    /** A flat section panel with a thin wooden edge. */
    public static void card(GuiGraphics g, int x, int y, int w, int h) {
        panel(g, x, y, w, h, PANEL);
    }

    public static void panel(GuiGraphics g, int x, int y, int w, int h, int fill) {
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x, y, x + w, y + 1, PANEL_EDGE);
        g.fill(x, y + h - 1, x + w, y + h, PANEL_EDGE);
        g.fill(x, y, x + 1, y + h, PANEL_EDGE);
        g.fill(x + w - 1, y, x + w, y + h, PANEL_EDGE);
    }

    /** A square wooden plate for buttons; {@code hover} 0..1 brightens it. */
    public static void plate(GuiGraphics g, int x, int y, int w, int h, float hover, boolean danger) {
        int fill = mix(PLATE, PLATE_HOT, hover);
        panel(g, x, y, w, h, fill);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, alpha(PLATE_EDGE, 0.5F + 0.5F * hover));
        if (danger) g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, WARN);
    }

    /** The Thaumonomicon item icon that stands for the book in its headers. */
    public static void bookIcon(GuiGraphics g, int x, int y) {
        RenderSystem.enableBlend();
        g.blit(BookArt.tc("textures/items/thaumonomicon.png"), x, y, 16, 16, 0F, 0F, 1, 1, 1, 1);
    }

    /** Thin progress bar in the original's flat style. */
    public static void progress(GuiGraphics g, float x, float y, float w, float h, float fraction, int color) {
        int x0 = Math.round(x), y0 = Math.round(y), x1 = Math.round(x + w), y1 = Math.round(y + h);
        g.fill(x0, y0, x1, y1, WELL);
        int fill = Math.round(x0 + (x1 - x0) * Math.max(0, Math.min(1, fraction)));
        if (fill > x0) g.fill(x0, y0, fill, y1, color);
    }
}
