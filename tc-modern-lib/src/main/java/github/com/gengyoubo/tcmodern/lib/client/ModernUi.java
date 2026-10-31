package github.com.gengyoubo.tcmodern.lib.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

/**
 * Vector drawing for the research desk and archive cabinet screens: true rounded corners, soft shadows, gradients,
 * hexagons and thick lines as quads, so edges stay clean at any GUI scale instead of stair-stepping in pixels.
 */
public final class ModernUi {
    // Palette: deep slate glass with a violet accent and a warm gold for anything fixed by the note.
    public static final int SHADOW = 0x000000, PANEL_TOP = 0xF61C1F2B, PANEL_BOTTOM = 0xF6121420, SURFACE = 0x14FFFFFF, SURFACE_HOVER = 0x24FFFFFF;
    public static final int WELL = 0x40000000, DIVIDER = 0x14FFFFFF, HIGHLIGHT = 0x1AFFFFFF;
    public static final int TEXT = 0xFFF1F2F7, MUTED = 0xFF9AA0B4, FAINT = 0xFF5F6578;
    public static final int ACCENT = 0xFF8B6CFF, ACCENT_LIGHT = 0xFFB39DFF, ACCENT_DEEP = 0xFF6A4BE0, GOLD = 0xFFF2C14E, GOOD = 0xFF4FD1A5, WARN = 0xFFFF7A6B, INK = 0xFF5B8CFF;

    private ModernUi() {}

    public static int alpha(int color, float factor) {
        int a = Math.round((color >>> 24) * Math.max(0, Math.min(1, factor)));
        return a << 24 | color & 0xFFFFFF;
    }

    public static int mix(int from, int to, float t) {
        t = Math.max(0, Math.min(1, t));
        int a = Math.round((from >>> 24) + ((to >>> 24) - (from >>> 24)) * t);
        int r = Math.round((from >> 16 & 255) + ((to >> 16 & 255) - (from >> 16 & 255)) * t);
        int gg = Math.round((from >> 8 & 255) + ((to >> 8 & 255) - (from >> 8 & 255)) * t);
        int b = Math.round((from & 255) + ((to & 255) - (from & 255)) * t);
        return a << 24 | r << 16 | gg << 8 | b;
    }

    // ---- Shapes ---------------------------------------------------------------------------------------------

    /** Convex polygon as a fan of quads around its centroid; {@code top}/{@code bottom} colours make a vertical gradient. */
    public static void polygon(GuiGraphics g, float[] xs, float[] ys, int top, int bottom) {
        int n = xs.length;
        float cx = 0, cy = 0, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) { cx += xs[i]; cy += ys[i]; minY = Math.min(minY, ys[i]); maxY = Math.max(maxY, ys[i]); }
        cx /= n;
        cy /= n;
        float span = Math.max(1e-3F, maxY - minY);
        Matrix4f pose = g.pose().last().pose();
        var buffer = g.bufferSource().getBuffer(RenderType.gui());
        int center = mix(top, bottom, (cy - minY) / span);
        // The GUI render type culls back faces: wind each fan quad as the vanilla fill does (reverse of the outline order).
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            vertex(buffer, pose, cx, cy, center);
            vertex(buffer, pose, xs[j], ys[j], mix(top, bottom, (ys[j] - minY) / span));
            vertex(buffer, pose, xs[i], ys[i], mix(top, bottom, (ys[i] - minY) / span));
            vertex(buffer, pose, xs[i], ys[i], mix(top, bottom, (ys[i] - minY) / span));
        }
        g.flush();
    }

    private static void vertex(com.mojang.blaze3d.vertex.VertexConsumer buffer, Matrix4f pose, float x, float y, int color) {
        buffer.vertex(pose, x, y, 0).color(color >> 16 & 255, color >> 8 & 255, color & 255, color >>> 24).endVertex();
    }

    /** Rounded rectangle with true circular corners. */
    public static void round(GuiGraphics g, float x, float y, float w, float h, float r, int top, int bottom) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        int steps = r < 3 ? 3 : 6;
        float[] xs = new float[4 * (steps + 1)], ys = new float[xs.length];
        float[][] corners = {{x + w - r, y + r, -90}, {x + w - r, y + h - r, 0}, {x + r, y + h - r, 90}, {x + r, y + r, 180}};
        int k = 0;
        for (var corner : corners) for (int s = 0; s <= steps; s++) {
            double angle = Math.toRadians(corner[2] + 90.0 * s / steps);
            xs[k] = corner[0] + (float) Math.cos(angle) * r;
            ys[k++] = corner[1] + (float) Math.sin(angle) * r;
        }
        polygon(g, xs, ys, top, bottom);
    }

    public static void round(GuiGraphics g, float x, float y, float w, float h, float r, int color) { round(g, x, y, w, h, r, color, color); }

    /** A rounded outline {@code width} thick, drawn as the difference of two rounded fills over a known backdrop. */
    public static void ring(GuiGraphics g, float x, float y, float w, float h, float r, float width, int color) {
        // Four edge quads plus corner arcs; avoids overdrawing the inside.
        Matrix4f pose = g.pose().last().pose();
        var buffer = g.bufferSource().getBuffer(RenderType.gui());
        int steps = 24;
        float[] outer = perimeter(x, y, w, h, r, steps), inner = perimeter(x + width, y + width, w - 2 * width, h - 2 * width, Math.max(0, r - width), steps);
        int n = outer.length / 2;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            vertex(buffer, pose, outer[2 * i], outer[2 * i + 1], color);
            vertex(buffer, pose, inner[2 * i], inner[2 * i + 1], color);
            vertex(buffer, pose, inner[2 * j], inner[2 * j + 1], color);
            vertex(buffer, pose, outer[2 * j], outer[2 * j + 1], color);
        }
        g.flush();
    }

    private static float[] perimeter(float x, float y, float w, float h, float r, int steps) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        int per = steps / 4;
        float[] points = new float[2 * 4 * (per + 1)];
        float[][] corners = {{x + w - r, y + r, -90}, {x + w - r, y + h - r, 0}, {x + r, y + h - r, 90}, {x + r, y + r, 180}};
        int k = 0;
        for (var corner : corners) for (int s = 0; s <= per; s++) {
            double angle = Math.toRadians(corner[2] + 90.0 * s / per);
            points[k++] = corner[0] + (float) Math.cos(angle) * r;
            points[k++] = corner[1] + (float) Math.sin(angle) * r;
        }
        return points;
    }

    /** Soft drop shadow: stacked, widening translucent rounded rectangles. */
    public static void shadow(GuiGraphics g, float x, float y, float w, float h, float r, int layers, float strength) {
        for (int i = layers; i >= 1; i--) {
            int a = Math.round(255 * strength / layers);
            round(g, x - i, y - i + i / 2F, w + 2 * i, h + 2 * i, r + i, a << 24 | SHADOW);
        }
    }

    /** Flat-topped hexagon of circumradius {@code size}. */
    public static void hex(GuiGraphics g, float cx, float cy, float size, int top, int bottom) {
        float[] xs = new float[6], ys = new float[6];
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(60 * i);
            xs[i] = cx + (float) Math.cos(angle) * size;
            ys[i] = cy + (float) Math.sin(angle) * size;
        }
        polygon(g, xs, ys, top, bottom);
    }

    public static void hex(GuiGraphics g, float cx, float cy, float size, int color) { hex(g, cx, cy, size, color, color); }

    /** Hexagonal outline. */
    public static void hexRing(GuiGraphics g, float cx, float cy, float size, float width, int color) {
        Matrix4f pose = g.pose().last().pose();
        var buffer = g.bufferSource().getBuffer(RenderType.gui());
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(60 * i), b = Math.toRadians(60 * (i + 1));
            float ox1 = cx + (float) Math.cos(a) * size, oy1 = cy + (float) Math.sin(a) * size;
            float ox2 = cx + (float) Math.cos(b) * size, oy2 = cy + (float) Math.sin(b) * size;
            float inner = size - width;
            float ix1 = cx + (float) Math.cos(a) * inner, iy1 = cy + (float) Math.sin(a) * inner;
            float ix2 = cx + (float) Math.cos(b) * inner, iy2 = cy + (float) Math.sin(b) * inner;
            vertex(buffer, pose, ox1, oy1, color);
            vertex(buffer, pose, ix1, iy1, color);
            vertex(buffer, pose, ix2, iy2, color);
            vertex(buffer, pose, ox2, oy2, color);
        }
        g.flush();
    }

    /** Segment of {@code width} with square caps. */
    public static void line(GuiGraphics g, float x1, float y1, float x2, float y2, float width, int color) {
        float dx = x2 - x1, dy = y2 - y1, length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1e-3F) return;
        float nx = -dy / length * width / 2, ny = dx / length * width / 2;
        Matrix4f pose = g.pose().last().pose();
        var buffer = g.bufferSource().getBuffer(RenderType.gui());
        // Winding depends on the direction, and the GUI type culls back faces: emit both, exactly one survives.
        vertex(buffer, pose, x1 + nx, y1 + ny, color);
        vertex(buffer, pose, x1 - nx, y1 - ny, color);
        vertex(buffer, pose, x2 - nx, y2 - ny, color);
        vertex(buffer, pose, x2 + nx, y2 + ny, color);
        vertex(buffer, pose, x2 + nx, y2 + ny, color);
        vertex(buffer, pose, x2 - nx, y2 - ny, color);
        vertex(buffer, pose, x1 - nx, y1 - ny, color);
        vertex(buffer, pose, x1 + nx, y1 + ny, color);
        g.flush();
    }

    public static void circle(GuiGraphics g, float cx, float cy, float radius, int color) {
        int n = 16;
        float[] xs = new float[n], ys = new float[n];
        for (int i = 0; i < n; i++) {
            double angle = 2 * Math.PI * i / n;
            xs[i] = cx + (float) Math.cos(angle) * radius;
            ys[i] = cy + (float) Math.sin(angle) * radius;
        }
        polygon(g, xs, ys, color, color);
    }

    // ---- Components -----------------------------------------------------------------------------------------

    /** The window: shadow, gradient glass, a hairline top highlight. */
    public static void window(GuiGraphics g, int x, int y, int w, int h) {
        shadow(g, x, y, w, h, 10, 8, 0.45F);
        round(g, x, y, w, h, 10, PANEL_TOP, PANEL_BOTTOM);
        ring(g, x, y, w, h, 10, 1, HIGHLIGHT);
    }

    /** A section surface inside the window. */
    public static void card(GuiGraphics g, int x, int y, int w, int h) {
        round(g, x, y, w, h, 7, SURFACE, 0x0AFFFFFF);
    }

    /** An item slot: a recessed rounded well. */
    public static void slot(GuiGraphics g, int x, int y) {
        round(g, x - 1, y - 1, 18, 18, 4, WELL);
    }

    /** Pill button; {@code hover} 0..1 animates the lift. */
    public static void button(GuiGraphics g, Font font, int x, int y, int w, int h, Component label, boolean active, boolean primary, float hover) {
        if (!active) {
            ring(g, x, y, w, h, h / 2F, 1, 0x22FFFFFF);
            centered(g, font, label, x + w / 2, y + (h - 8) / 2 + 1, FAINT);
            return;
        }
        if (primary) {
            if (hover > 0) round(g, x - 2, y - 1, w + 4, h + 3, h / 2F + 2, alpha(ACCENT, 0.25F * hover));
            round(g, x, y - hover, w, h, h / 2F, mix(ACCENT_LIGHT, 0xFFC9B8FF, hover), mix(ACCENT, ACCENT_LIGHT, hover * 0.4F));
        } else {
            round(g, x, y - hover, w, h, h / 2F, mix(0x1FFFFFFF, 0x33FFFFFF, hover));
        }
        centered(g, font, label, x + w / 2, Math.round(y + (h - 8) / 2F + 1 - hover), primary ? 0xFF14121F : TEXT);
    }

    public static void text(GuiGraphics g, Font font, Component text, int x, int y, int color) { g.drawString(font, text, x, y, color, false); }
    public static void text(GuiGraphics g, Font font, String text, int x, int y, int color) { g.drawString(font, text, x, y, color, false); }

    public static void centered(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        g.drawString(font, text, x - font.width(text) / 2, y, color, false);
    }

    /** Text cut to {@code width} with an ellipsis. */
    public static String clip(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, width - font.width("…")) + "…";
    }

    /** A count in the corner, small and shadowed like a vanilla stack size, right-aligned to {@code right}. */
    public static void badge(GuiGraphics g, Font font, String text, float right, float y, int color) {
        float scale = 0.75F;
        g.pose().pushPose();
        g.pose().translate(right - font.width(text) * scale, y + 1, 150);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, text, 0, 0, color, true);
        g.pose().popPose();
    }

    /** Rounded progress bar with a lighter leading edge. */
    public static void progress(GuiGraphics g, float x, float y, float w, float h, float fraction, int color) {
        round(g, x, y, w, h, h / 2, WELL);
        float filled = w * Math.max(0, Math.min(1, fraction));
        if (filled >= h) round(g, x, y, filled, h, h / 2, mix(color, 0xFFFFFFFF, 0.25F), color);
        else if (filled > 0) circle(g, x + h / 2, y + h / 2, h / 2 * filled / h + 0.5F, color);
    }

    /** Small eyebrow label above a section. */
    public static void label(GuiGraphics g, Font font, Component text, int x, int y) {
        g.drawString(font, text, x, y, MUTED, false);
    }

    /** A magnifier glyph for the search field. */
    public static void magnifier(GuiGraphics g, float x, float y, int color) {
        ring(g, x, y, 7, 7, 3.5F, 1.2F, color);
        line(g, x + 6, y + 6, x + 9, y + 9, 1.5F, color);
    }

    /** Linear ease-out of a 0..1 animation value toward {@code target}. */
    public static float approach(float value, float target, float seconds, float speed) {
        float step = Math.min(1, seconds * speed);
        return value + (target - value) * step;
    }
}
