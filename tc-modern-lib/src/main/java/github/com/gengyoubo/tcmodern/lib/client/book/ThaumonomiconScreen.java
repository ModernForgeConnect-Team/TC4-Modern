package github.com.gengyoubo.tcmodern.lib.client.book;

import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspect;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspectAccessors;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspects;
import github.com.gengyoubo.tcmodern.api.research.TcResearchCategory;
import github.com.gengyoubo.tcmodern.api.research.TcResearchEntry;
import github.com.gengyoubo.tcmodern.api.research.TcThaumonomicon;
import github.com.gengyoubo.tcmodern.lib.client.AspectIcons;
import github.com.gengyoubo.tcmodern.lib.book.BookNetwork;
import github.com.gengyoubo.tcmodern.lib.book.BookLogic;
import github.com.gengyoubo.tcmodern.lib.json.ResearchLoader;
import github.com.gengyoubo.tcmodern.lib.client.book.edit.CategoryEditorScreen;
import github.com.gengyoubo.tcmodern.lib.client.book.edit.ResearchEditorScreen;
import github.com.gengyoubo.tcmodern.lib.client.book.edit.ResearchForm;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import static github.com.gengyoubo.tcmodern.lib.client.book.BookChrome.*;

/**
 * The modern Thaumonomicon: categories on the left, the research map on the right, a two-page reader on top.
 * Visibility, unlocking, buying and research notes follow the original book. Operators get an editing mode, as in
 * FTB Quests: every entry visible with its data; complete/revoke for one research, a category or everything; and
 * JSON research and categories created, edited, moved by dragging and deleted in place, then reloaded everywhere.
 */
public final class ThaumonomiconScreen extends Screen {
    static final int WIDTH = 460, HEIGHT = 280, HEADER = 30;
    // The wooden frame takes 16 px on every side; everything sits inside it.
    static final int INSET = 20;
    static final int SIDE_X = INSET, SIDE_Y = 38, SIDE_W = 104, SIDE_H = 216, ROW = 26;
    static final int MAP_X = 130, MAP_Y = 38, MAP_W = 300, MAP_H = 216, UNIT = 30, NODE = 24;
    static final int PAGE_W = 176, PAGE_H = 192, PAGE_Y = 52;
    private static final String ELDRITCH = "ELDRITCH", ELDRITCH_KEY = "ELDRITCHMINOR";

    /** Kept for the session, as the original book remembers its place. */
    private static String category;
    private static final Map<String, float[]> PANS = new HashMap<>();
    private static float zoom = 1;
    static boolean cheat;

    private final LegacyRuntime runtime;
    private final Map<String, TcAspect> aspects = new HashMap<>();
    private final Map<LegacyStack, ItemStack> items = new HashMap<>();
    private final Map<String, Float> motion = new HashMap<>();
    private final Random random = new Random();
    private List<TcResearchCategory> book = List.of();
    private Map<String, TcResearchEntry> byKey = Map.of();
    private Set<String> completed = Set.of();
    private String player = "";
    private float scale = 1, frame;
    private long lastFrame;
    private int left, top, sideScroll;
    private BookReader reader;
    private int spread;
    private Menu menu;
    private String toast;
    private long toastUntil;
    // Map dragging: the press point, and the node pressed if any.
    private boolean dragging, moved;
    private double pressX, pressY;
    private TcResearchEntry pressed;
    /** Editing mode: the JSON research being dragged to a new slot. */
    private boolean movingNode;

    private record MenuItem(Component label, Runnable action, boolean danger) {}
    private record Menu(int x, int y, List<MenuItem> items) {
        int width(Font font) { return items.stream().mapToInt(item -> font.width(item.label())).max().orElse(40) + 20; }
    }

    public ThaumonomiconScreen() {
        super(Component.translatable("book.tcmodernlib.title"));
        runtime = TcModernApi.runtime().orElse(null);
        if (runtime != null) for (var aspect : TcAspects.snapshot(runtime)) aspects.put(aspect.tag(), aspect);
    }

    // ---- Data -------------------------------------------------------------------------------------------------

    @Override protected void init() {
        scale = width < 64 || height < 64 ? 1 : Math.min(1F, Math.min((width - 4F) / WIDTH, (height - 4F) / HEIGHT));
        width = Math.round(width / scale);
        height = Math.round(height / scale);
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        refresh();
    }

    /** Re-reads the registry snapshot, after opening or a JSON reload. */
    void refresh() {
        book = BookClient.book();
        var keys = new HashMap<String, TcResearchEntry>();
        for (var c : book) for (var entry : c.entries()) keys.put(entry.key(), entry);
        byKey = keys;
        if (minecraft != null && minecraft.player != null) player = minecraft.player.getGameProfile().getName();
        readProgress();
        var visible = categories();
        if (category == null || visible.stream().noneMatch(c -> c.key().equals(category)))
            category = visible.isEmpty() ? null : visible.get(0).key();
        if (reader != null) {
            var entry = byKey.get(reader.entry().key());
            reader = entry == null ? null : new BookReader(this, entry, cheat, completed, PAGE_W, PAGE_H);
        }
    }

    private void readProgress() {
        if (runtime == null || player.isEmpty()) return;
        try { completed = TcThaumonomicon.completed(runtime, player); } catch (RuntimeException unavailable) { completed = Set.of(); }
    }

    @Override public void tick() {
        readProgress();
        if (cheat && !mayCheat()) cheat = false;
    }

    boolean mayCheat() { return minecraft != null && minecraft.player != null && minecraft.player.hasPermissions(BookLogic.CHEAT_PERMISSION); }

    /** Categories the player can open; the eldritch one needs its first research, as in the original. */
    private List<TcResearchCategory> categories() {
        var result = new ArrayList<TcResearchCategory>();
        for (var c : book) if (cheat || !c.key().equals(ELDRITCH) || completed.contains(ELDRITCH_KEY)) result.add(c);
        return result;
    }

    private TcResearchCategory current() {
        for (var c : book) if (c.key().equals(category)) return c;
        return null;
    }

    boolean done(TcResearchEntry entry) { return completed.contains(entry.key()); }

    /** All shown and hidden parents complete; parents no one registered are ignored, as in the original. */
    boolean unlockable(TcResearchEntry entry) {
        for (String parent : entry.requisites()) if (byKey.containsKey(parent) && !completed.contains(parent)) return false;
        return true;
    }

    /** The original browser's visibility rule. */
    boolean visible(TcResearchEntry entry) {
        if (entry.has(TcResearchEntry.Flag.VIRTUAL)) return false;
        if (cheat || done(entry) || completed.contains("@" + entry.key())) return true;
        if (entry.has(TcResearchEntry.Flag.LOST) || entry.has(TcResearchEntry.Flag.HIDDEN)) return false;
        return !entry.has(TcResearchEntry.Flag.CONCEALED) || unlockable(entry);
    }

    /** Would the player see it without the debugging mode? */
    private boolean normallyVisible(TcResearchEntry entry) {
        boolean saved = cheat;
        cheat = false;
        try { return visible(entry); } finally { cheat = saved; }
    }

    boolean buyable(TcResearchEntry entry) {
        if (entry.aspects().isEmpty() || runtime == null) return false;
        int difficulty = TcThaumonomicon.difficulty(runtime);
        return difficulty == -1 || difficulty == 0 && entry.has(TcResearchEntry.Flag.SECONDARY);
    }

    int pool(String tag) {
        if (runtime == null || player.isEmpty()) return 0;
        try {
            var aspect = runtime.accessor(TcAspectAccessors.AspectLookup.class).find(tag);
            if (aspect == null) return 0;
            return runtime.accessor(TcAspectAccessors.Knowledge.class, runtime.accessor(TcAspectAccessors.Proxy.class,
                    runtime.accessor(TcAspectAccessors.Thaumcraft.class).proxy()).knowledge()).pool(player, aspect);
        } catch (RuntimeException unavailable) {
            return 0;
        }
    }

    private boolean affordable(TcResearchEntry entry) {
        for (var cost : entry.aspects().entrySet()) if (pool(cost.getKey()) < cost.getValue()) return false;
        return true;
    }

    // ---- Shared helpers for the reader ------------------------------------------------------------------------

    Font font() { return font; }
    String translate(String key) { return runtime == null ? key : TcThaumonomicon.translate(runtime, key); }
    String name(String key) { return translate("tc.research_name." + key); }
    String subtitle(String key) { return translate("tc.research_text." + key); }
    String aspectName(String tag) { return AspectIcons.name(tag); }
    String clipText(String text, int width) { return clip(font, text, width); }

    ItemStack item(LegacyStack stack) {
        return items.computeIfAbsent(stack, key -> {
            if (runtime == null) return ItemStack.EMPTY;
            try {
                var fixed = key.metadata() == TcThaumonomicon.WILDCARD ? new LegacyStack(key.key(), 0, Math.max(1, key.count()), key.snbt()) : key;
                return runtime.mappings().toHost(LegacyMappings.ITEM_STACK, runtime.stack(fixed), ItemStack.class).orElse(ItemStack.EMPTY);
            } catch (RuntimeException unmapped) {
                return ItemStack.EMPTY;
            }
        });
    }

    void renderAspect(GuiGraphics g, String tag, int x, int y) {
        var aspect = aspects.get(tag);
        AspectIcons.render(g, aspect == null ? new TcAspect(tag, 0x808080, "", List.of()) : aspect, x, y);
    }

    /** A research icon, item or texture, 16 px at {@code size} = 1, centred on nothing: top-left at {@code x, y}. */
    void renderIcon(GuiGraphics g, TcResearchEntry entry, float x, float y, float size) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(size, size, 1);
        if (entry.iconItem() != null) {
            var stack = item(entry.iconItem());
            if (!stack.isEmpty()) g.renderItem(stack, 0, 0);
        } else {
            var texture = ResourceLocation.tryParse(entry.iconTexture());
            if (texture != null) {
                com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                g.blit(texture, 0, 0, 16, 16, 0F, 0F, 1, 1, 1, 1);
                com.mojang.blaze3d.systems.RenderSystem.disableBlend();
            }
        }
        g.pose().popPose();
    }

    private float animate(String id, boolean on) {
        float value = approach(motion.getOrDefault(id, 0F), on ? 1 : 0, frame, 14);
        motion.put(id, value);
        return value;
    }

    private void sound(String id, float volume, float jitter) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("thaumcraft", id)), 1 + random.nextFloat() * jitter, volume));
    }

    private void show(Component message) {
        toast = message.getString();
        toastUntil = System.currentTimeMillis() + 2600;
    }

    // ---- Map geometry -----------------------------------------------------------------------------------------

    private float[] pan() {
        return PANS.computeIfAbsent(String.valueOf(category), key -> {
            var c = current();
            if (c == null) return new float[]{0, 0};
            int minC = Integer.MAX_VALUE, maxC = Integer.MIN_VALUE, minR = Integer.MAX_VALUE, maxR = Integer.MIN_VALUE;
            for (var entry : c.entries()) if (visible(entry)) {
                minC = Math.min(minC, entry.column()); maxC = Math.max(maxC, entry.column());
                minR = Math.min(minR, entry.row()); maxR = Math.max(maxR, entry.row());
            }
            if (minC == Integer.MAX_VALUE) return new float[]{0, 0};
            return new float[]{-(minC + maxC) / 2F * UNIT, -(minR + maxR) / 2F * UNIT};
        });
    }

    private float nodeX(TcResearchEntry entry) { return left + MAP_X + MAP_W / 2F + (pan()[0] + entry.column() * UNIT) * zoom; }
    private float nodeY(TcResearchEntry entry) { return top + MAP_Y + MAP_H / 2F + (pan()[1] + entry.row() * UNIT) * zoom; }

    private boolean inMap(double x, double y) {
        return x >= left + MAP_X && x < left + MAP_X + MAP_W && y >= top + MAP_Y && y < top + MAP_Y + MAP_H;
    }

    private TcResearchEntry nodeAt(double x, double y) {
        var c = current();
        if (c == null || !inMap(x, y)) return null;
        float half = NODE * zoom / 2;
        for (var entry : c.entries())
            if (visible(entry) && Math.abs(x - nodeX(entry)) <= half && Math.abs(y - nodeY(entry)) <= half) return entry;
        return null;
    }

    private String categoryAt(double x, double y) {
        if (x < left + SIDE_X || x >= left + SIDE_X + SIDE_W) return null;
        int index = (int) Math.floor((y - top - SIDE_Y - 14) / ROW) + sideScroll;
        var list = categories();
        if (y < top + SIDE_Y + 14 || index < 0 || index >= list.size() || index - sideScroll >= visibleRows()) return null;
        return list.get(index).key();
    }

    private int visibleRows() { return (SIDE_H - 18) / ROW; }

    private boolean inside(double x, double y, int bx, int by, int w, int h) {
        return x >= left + bx && x < left + bx + w && y >= top + by && y < top + by + h;
    }

    // ---- Header controls --------------------------------------------------------------------------------------

    private record Control(String id, Component label, int x, int w, boolean primary) {}

    /** Right-aligned header pills: the debugging switch for operators, and its global actions while on. */
    private List<Control> controls() {
        var result = new ArrayList<Control>();
        if (!mayCheat()) return result;
        int x = WIDTH - INSET;
        var toggle = Component.translatable(cheat ? "book.tcmodernlib.cheat.on" : "book.tcmodernlib.cheat.off");
        int w = font.width(toggle) + 14;
        x -= w;
        result.add(new Control("toggle", toggle, x, w, cheat));
        if (cheat) for (String id : List.of("reload", "all_reset", "all_complete")) {
            var label = Component.translatable("book.tcmodernlib.cheat." + id);
            int lw = font.width(label) + 12;
            x -= lw + 4;
            result.add(new Control(id, label, x, lw, false));
        }
        return result;
    }

    private void control(String id) {
        switch (id) {
            case "toggle" -> { cheat = !cheat; refresh(); sound("cameraclack", 0.4F, 0); }
            case "reload" -> { BookNetwork.send(new BookNetwork.Action(BookNetwork.Kind.RELOAD, "", false)); sound("cameraclack", 0.4F, 0); }
            case "all_complete" -> confirm(Component.translatable("book.tcmodernlib.cheat.all_complete"),
                    () -> BookNetwork.send(new BookNetwork.Action(BookNetwork.Kind.ALL_COMPLETE, "", false)));
            case "all_reset" -> confirm(Component.translatable("book.tcmodernlib.cheat.all_reset"),
                    () -> BookNetwork.send(new BookNetwork.Action(BookNetwork.Kind.ALL_RESET, "", false)));
            default -> { }
        }
    }

    /** A one-item menu asking again before an action that touches every research. */
    private void confirm(Component what, Runnable action) {
        menu = new Menu(WIDTH - 150, HEADER + 2, List.of(new MenuItem(Component.translatable("book.tcmodernlib.cheat.confirm", what), action, true)));
    }

    // ---- Rendering --------------------------------------------------------------------------------------------

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        frame = lastFrame == 0 ? 0 : Math.min(0.1F, (now - lastFrame) / 1e9F);
        lastFrame = now;
        int x = Math.round(mouseX / scale), y = Math.round(mouseY / scale);
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1);
        renderBackground(g);
        window(g, left, top, WIDTH, HEIGHT);
        renderHeader(g, x, y);
        renderSidebar(g, x, y);
        renderMap(g, x, y);
        if (reader != null) renderReader(g, x, y);
        if (menu != null) renderMenu(g, x, y);
        renderToast(g);
        if (menu == null) renderTooltips(g, x, y);
        g.pose().popPose();
    }

    private void renderHeader(GuiGraphics g, int mouseX, int mouseY) {
        g.blit(BookArt.tc("textures/items/thaumonomicon.png"), left + INSET, top + 12, 16, 16, 0F, 0F, 1, 1, 1, 1);
        text(g, font, title, left + INSET + 22, top + 16, TEXT);
        var c = current();
        if (c != null && runtime != null)
            text(g, font, "- " + TcThaumonomicon.categoryName(runtime, c.key()), left + INSET + 26 + font.width(title), top + 16, FAINT);
        for (var control : controls()) {
            boolean hot = menu == null && inside(mouseX, mouseY, control.x(), 12, control.w(), 15);
            float lift = animate("control:" + control.id(), hot);
            boolean warn = cheat && control.id().equals("toggle");
            plate(g, left + control.x(), top + 12, control.w(), 15, lift, warn);
            text(g, font, control.label(), left + control.x() + 6, top + 16, warn ? 0xFFFFB3A8 : TEXT);
        }
    }

    private void renderSidebar(GuiGraphics g, int mouseX, int mouseY) {
        card(g, left + SIDE_X, top + SIDE_Y, SIDE_W, SIDE_H);
        label(g, font, Component.translatable("book.tcmodernlib.categories"), left + SIDE_X + 8, top + SIDE_Y + 5);
        var list = categories();
        String hovered = menu == null && reader == null ? categoryAt(mouseX, mouseY) : null;
        for (int i = 0; i < visibleRows() && sideScroll + i < list.size(); i++) {
            var c = list.get(sideScroll + i);
            int rx = left + SIDE_X + 4, ry = top + SIDE_Y + 16 + i * ROW;
            boolean selected = c.key().equals(category);
            float hot = animate("cat:" + c.key(), c.key().equals(hovered));
            round(g, rx, ry, SIDE_W - 8, ROW - 3, 6, selected ? alpha(ACCENT, 0.30F) : mix(0x00FFFFFF, SURFACE_HOVER, hot));
            if (selected) round(g, rx, ry + 5, 2, ROW - 13, 1, ACCENT_LIGHT);
            var icon = ResourceLocation.tryParse(c.icon());
            if (icon != null) {
                com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                g.blit(icon, rx + 5, ry + 3, 16, 16, 0F, 0F, 1, 1, 1, 1);
                com.mojang.blaze3d.systems.RenderSystem.disableBlend();
            }
            String name = runtime == null ? c.key() : TcThaumonomicon.categoryName(runtime, c.key());
            g.drawString(font, clip(font, name, SIDE_W - 36), rx + 25, ry + 3, selected ? TEXT : mix(MUTED, TEXT, hot), false);
            int total = 0, have = 0;
            for (var entry : c.entries()) if (!entry.has(TcResearchEntry.Flag.VIRTUAL)) { total++; if (done(entry)) have++; }
            progress(g, rx + 25, ry + 15, SIDE_W - 38, 2, total == 0 ? 0 : have / (float) total, have == total && total > 0 ? GOOD : ACCENT);
        }
        int max = Math.max(0, list.size() - visibleRows());
        if (max > 0) {
            float trackY = top + SIDE_Y + 16, trackH = visibleRows() * ROW - 3;
            float thumb = Math.max(10, trackH * visibleRows() / list.size());
            round(g, left + SIDE_X + SIDE_W - 4, trackY + (trackH - thumb) * sideScroll / max, 2, thumb, 1, alpha(ACCENT_LIGHT, 0.6F));
        }
    }

    private void renderMap(GuiGraphics g, int mouseX, int mouseY) {
        var c = current();
        // The original map is the violet starfield inside the wooden edge, scrolled with the pan.
        starfield(g, left + MAP_X, top + MAP_Y, MAP_W, MAP_H, pan()[0] * zoom + MAP_W / 2F, pan()[1] * zoom + MAP_H / 2F);
        panel(g, left + MAP_X, top + MAP_Y, MAP_W, MAP_H, 0);
        if (c == null) {
            centered(g, font, Component.translatable("book.tcmodernlib.empty"), left + MAP_X + MAP_W / 2, top + MAP_Y + MAP_H / 2, FAINT);
            return;
        }
        g.enableScissor(Math.round((left + MAP_X + 1) * scale), Math.round((top + MAP_Y + 1) * scale),
                Math.round((left + MAP_X + MAP_W - 1) * scale), Math.round((top + MAP_Y + MAP_H - 1) * scale));

        var hovered = menu == null && reader == null ? nodeAt(mouseX, mouseY) : null;
        // Links from every shown parent in this category.
        for (var entry : c.entries()) {
            if (!visible(entry)) continue;
            for (String key : entry.parents()) {
                var parent = byKey.get(key);
                if (parent == null || !parent.category().equals(c.key()) || !visible(parent)) continue;
                boolean lit = done(parent);
                int color = done(entry) ? alpha(ACCENT_LIGHT, 0.75F) : lit ? alpha(GOLD, 0.55F) : 0x30FFFFFF;
                if (entry == hovered || parent == hovered) color = ACCENT_LIGHT;
                line(g, nodeX(parent), nodeY(parent), nodeX(entry), nodeY(entry), Math.max(1, 1.6F * zoom), color);
            }
        }
        float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 300.0));
        for (var entry : c.entries()) {
            if (!visible(entry)) continue;
            float cx = nodeX(entry), cy = nodeY(entry), size = NODE * zoom, half = size / 2;
            if (cx < left + MAP_X - size || cx > left + MAP_X + MAP_W + size || cy < top + MAP_Y - size || cy > top + MAP_Y + MAP_H + size) continue;
            boolean isDone = done(entry), open = !isDone && unlockable(entry), ghost = cheat && !normallyVisible(entry);
            float hot = animate("node:" + entry.key(), entry == hovered);
            if (entry.warp() > 0) circle(g, cx, cy, half + 5, alpha(0xFF9A2BD8, 0.25F));
            if (hot > 0.01F) circle(g, cx, cy, half + 4, alpha(ACCENT, 0.25F * hot));
            int fill = isDone ? 0xFF2A2740 : open ? 0xFF262331 : 0xFF17181F;
            int ring = isDone ? ACCENT_LIGHT : open ? alpha(GOLD, 0.55F + 0.45F * pulse) : 0x40FFFFFF;
            if (ghost) ring = alpha(WARN, 0.8F);
            if (entry.has(TcResearchEntry.Flag.ROUND)) {
                circle(g, cx, cy, half, fill);
                ring(g, cx - half, cy - half, size, size, half, 1.4F * zoom, ring);
            } else if (buyable(entry)) {
                hex(g, cx, cy, half + 1, fill);
                hexRing(g, cx, cy, half + 1, 1.4F * zoom, ring);
            } else {
                round(g, cx - half, cy - half, size, size, 6 * zoom, fill);
                ring(g, cx - half, cy - half, size, size, 6 * zoom, 1.4F * zoom, ring);
            }
            if (entry.has(TcResearchEntry.Flag.SPECIAL)) ring(g, cx - half - 3, cy - half - 3, size + 6, size + 6, 8 * zoom, 1, alpha(GOLD, 0.8F));
            renderIcon(g, entry, cx - 8 * zoom, cy - 8 * zoom, zoom);
            if (!isDone && !open) round(g, cx - half, cy - half, size, size, 6 * zoom, 0x99101118);
        }
        if (movingNode && pressed != null) {
            int[] cell = cellAt(mouseX, mouseY);
            float gx = left + MAP_X + MAP_W / 2F + (pan()[0] + cell[0] * UNIT) * zoom, gy = top + MAP_Y + MAP_H / 2F + (pan()[1] + cell[1] * UNIT) * zoom;
            float half = NODE * zoom / 2;
            boolean free = free(pressed, cell[0], cell[1]);
            round(g, gx - half, gy - half, NODE * zoom, NODE * zoom, 6 * zoom, alpha(free ? ACCENT : WARN, 0.25F));
            ring(g, gx - half, gy - half, NODE * zoom, NODE * zoom, 6 * zoom, 1.4F, free ? ACCENT_LIGHT : WARN);
            renderIcon(g, pressed, gx - 8 * zoom, gy - 8 * zoom, zoom);
        }
        g.disableScissor();
        text(g, font, Component.translatable(cheat ? "book.tcmodernlib.hint.cheat" : "book.tcmodernlib.hint"), left + MAP_X + 8, top + MAP_Y + MAP_H - 12, FAINT);
        if (cheat && hovered != null) renderInfo(g, hovered);
    }

    /** The debugging panel: every original field of the hovered research. */
    private void renderInfo(GuiGraphics g, TcResearchEntry entry) {
        var lines = new ArrayList<String>();
        lines.add(entry.key());
        String source = ResearchLoader.source(entry.key());
        lines.add(Component.translatable("book.tcmodernlib.info.source", source == null ? Component.translatable("book.tcmodernlib.info.code") : Component.literal(source)).getString());
        lines.add(Component.translatable("book.tcmodernlib.info.position", entry.column(), entry.row(), entry.complexity()).getString());
        if (!entry.flags().isEmpty()) lines.add(Component.translatable("book.tcmodernlib.info.flags",
                String.join(", ", entry.flags().stream().map(f -> f.name().toLowerCase(Locale.ROOT)).toList())).getString());
        if (!entry.aspects().isEmpty()) lines.add(Component.translatable("book.tcmodernlib.info.aspects",
                String.join(", ", entry.aspects().entrySet().stream().map(e -> e.getKey() + "×" + e.getValue()).toList())).getString());
        if (!entry.parents().isEmpty()) lines.add(Component.translatable("book.tcmodernlib.info.parents", String.join(", ", entry.parents())).getString());
        if (!entry.hiddenParents().isEmpty()) lines.add(Component.translatable("book.tcmodernlib.info.hidden_parents", String.join(", ", entry.hiddenParents())).getString());
        if (!entry.siblings().isEmpty()) lines.add(Component.translatable("book.tcmodernlib.info.siblings", String.join(", ", entry.siblings())).getString());
        var triggers = new ArrayList<String>();
        entry.itemTriggers().forEach(stack -> triggers.add(stack.key() + "@" + stack.metadata()));
        triggers.addAll(entry.entityTriggers());
        triggers.addAll(entry.aspectTriggers());
        if (!triggers.isEmpty()) lines.add(Component.translatable("book.tcmodernlib.info.triggers", String.join(", ", triggers)).getString());
        if (entry.warp() > 0) lines.add(Component.translatable("book.tcmodernlib.info.warp", entry.warp()).getString());
        lines.add(Component.translatable("book.tcmodernlib.info.pages", entry.pages().size()).getString());
        int w = 156, lineH = 10;
        var wrapped = new ArrayList<net.minecraft.util.FormattedCharSequence>();
        for (String line : lines) wrapped.addAll(font.split(Component.literal(line), w - 12));
        int h = wrapped.size() * lineH + 10, x = left + MAP_X + MAP_W - w - 6, y = top + MAP_Y + 6;
        round(g, x, y, w, h, 6, 0xE6101118);
        ring(g, x, y, w, h, 6, 1, alpha(WARN, 0.5F));
        for (int i = 0; i < wrapped.size(); i++) g.drawString(font, wrapped.get(i), x + 6, y + 5 + i * lineH, i == 0 ? 0xFFFFB3A8 : MUTED, false);
    }

    private void renderReader(GuiGraphics g, int mouseX, int mouseY) {
        round(g, left + 4, top + HEADER + 2, WIDTH - 8, HEIGHT - HEADER - 6, 8, 0xF0101118);
        int leftPage = (WIDTH - 2 * PAGE_W - 40) / 2 + 10, rightPage = leftPage + PAGE_W + 40;
        reader.hoveredItem = ItemStack.EMPTY;
        reader.hoveredAspect = null;
        reader.renderSide(g, spread * 2, left + leftPage, top + PAGE_Y, mouseX, mouseY);
        reader.renderSide(g, spread * 2 + 1, left + rightPage, top + PAGE_Y, mouseX, mouseY);
        // Back, previous and next.
        var back = Component.translatable("book.tcmodernlib.back");
        int bw = font.width(back) + 18;
        float hot = animate("back", inside(mouseX, mouseY, 10, HEADER + 6, bw, 15));
        round(g, left + 10, top + HEADER + 6, bw, 15, 7.5F, mix(0x1FFFFFFF, 0x33FFFFFF, hot));
        text(g, font, back, left + 19, top + HEADER + 10, TEXT);
        if (spread > 0) arrow(g, left + 14, top + PAGE_Y + PAGE_H / 2, -1, inside(mouseX, mouseY, 6, PAGE_Y + PAGE_H / 2 - 12, 20, 24));
        if ((spread + 1) * 2 < reader.sides()) arrow(g, left + WIDTH - 14, top + PAGE_Y + PAGE_H / 2, 1, inside(mouseX, mouseY, WIDTH - 26, PAGE_Y + PAGE_H / 2 - 12, 20, 24));
    }

    private void arrow(GuiGraphics g, float x, float y, int direction, boolean hot) {
        int color = hot ? ACCENT_LIGHT : MUTED;
        line(g, x + 4 * direction, y, x - 3 * direction, y - 7, 2, color);
        line(g, x + 4 * direction, y, x - 3 * direction, y + 7, 2, color);
    }

    private void renderMenu(GuiGraphics g, int mouseX, int mouseY) {
        int w = menu.width(font), h = menu.items().size() * 16 + 6, x = left + Math.min(menu.x(), WIDTH - w - 4), y = top + Math.min(menu.y(), HEIGHT - h - 4);
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        shadow(g, x, y, w, h, 6, 4, 0.4F);
        round(g, x, y, w, h, 6, 0xF51A1C26);
        ring(g, x, y, w, h, 6, 1, HIGHLIGHT);
        for (int i = 0; i < menu.items().size(); i++) {
            var item = menu.items().get(i);
            int iy = y + 3 + i * 16;
            boolean hot = mouseX >= x && mouseX < x + w && mouseY >= iy && mouseY < iy + 16;
            if (hot) round(g, x + 3, iy, w - 6, 16, 4, item.danger() ? alpha(WARN, 0.25F) : alpha(ACCENT, 0.3F));
            g.drawString(font, item.label(), x + 10, iy + 4, item.danger() ? 0xFFFFB3A8 : TEXT, false);
        }
        g.pose().popPose();
    }

    private void renderToast(GuiGraphics g) {
        if (toast == null) return;
        long remaining = toastUntil - System.currentTimeMillis();
        if (remaining <= 0) { toast = null; return; }
        float fade = Math.min(1, remaining / 400F);
        int w = font.width(toast) + 20, x = left + (WIDTH - w) / 2, y = top + HEIGHT - 28;
        g.pose().pushPose();
        g.pose().translate(0, 0, 350);
        round(g, x, y, w, 16, 8, alpha(0xF0202230, fade));
        g.drawString(font, toast, x + 10, y + 4, alpha(TEXT, fade), false);
        g.pose().popPose();
    }

    private void renderTooltips(GuiGraphics g, int mouseX, int mouseY) {
        if (reader != null) {
            if (!reader.hoveredItem.isEmpty()) g.renderTooltip(font, reader.hoveredItem, mouseX, mouseY);
            else if (reader.hoveredAspect != null) g.renderTooltip(font, Component.literal(aspectName(reader.hoveredAspect)), mouseX, mouseY);
            return;
        }
        var entry = nodeAt(mouseX, mouseY);
        if (entry == null) return;
        var lines = new ArrayList<Component>();
        lines.add(Component.literal(name(entry.key())).withStyle(ChatFormatting.AQUA));
        String subtitle = subtitle(entry.key());
        if (!subtitle.isEmpty() && !subtitle.equals("tc.research_text." + entry.key())) lines.add(Component.literal(subtitle).withStyle(ChatFormatting.GRAY));
        boolean isDone = done(entry), open = unlockable(entry);
        if (isDone) lines.add(Component.translatable("book.tcmodernlib.state.done").withStyle(ChatFormatting.GREEN));
        else if (!open) lines.add(Component.translatable("book.tcmodernlib.state.locked").withStyle(ChatFormatting.DARK_GRAY));
        else if (buyable(entry)) {
            lines.add(Component.translatable("book.tcmodernlib.state.buy").withStyle(ChatFormatting.GOLD));
            entry.aspects().forEach((tag, amount) -> lines.add(Component.literal("  " + aspectName(tag) + " " + pool(tag) + "/" + amount)
                    .withStyle(pool(tag) >= amount ? ChatFormatting.GRAY : ChatFormatting.RED)));
        } else lines.add(Component.translatable("book.tcmodernlib.state.note").withStyle(ChatFormatting.GOLD));
        if (entry.warp() > 0) lines.add(Component.translatable("book.tcmodernlib.state.warp", entry.warp()).withStyle(ChatFormatting.DARK_PURPLE));
        if (cheat) {
            if (!normallyVisible(entry)) lines.add(Component.translatable("book.tcmodernlib.state.ghost").withStyle(ChatFormatting.RED));
            lines.add(Component.translatable("book.tcmodernlib.state.cheat").withStyle(ChatFormatting.DARK_GRAY));
        }
        g.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    // ---- Input ------------------------------------------------------------------------------------------------

    private void open(TcResearchEntry entry) {
        reader = new BookReader(this, entry, cheat, completed, PAGE_W, PAGE_H);
        spread = 0;
        sound("page", 0.6F, 0.1F);
    }

    private void activate(TcResearchEntry entry) {
        if (cheat || done(entry)) { open(entry); return; }
        if (!unlockable(entry)) { show(Component.translatable("book.tcmodernlib.toast.locked")); return; }
        if (buyable(entry)) {
            if (!affordable(entry)) { show(Component.translatable("book.tcmodernlib.toast.points")); return; }
            BookNetwork.send(new BookNetwork.Action(BookNetwork.Kind.BUY, entry.key(), false));
            sound("cameraclack", 0.4F, 0);
        } else {
            BookNetwork.send(new BookNetwork.Action(BookNetwork.Kind.NOTE, entry.key(), false));
            show(Component.translatable("book.tcmodernlib.toast.note", name(entry.key())));
            sound("write", 0.3F, 0);
        }
    }

    private void researchMenu(TcResearchEntry entry, int x, int y) {
        var items = new ArrayList<MenuItem>();
        String key = entry.key();
        items.add(new MenuItem(Component.translatable("book.tcmodernlib.menu.open"), () -> open(entry), false));
        items.add(new MenuItem(Component.translatable("book.tcmodernlib.menu.complete"), () -> action(BookNetwork.Kind.COMPLETE, key, false), false));
        items.add(new MenuItem(Component.translatable("book.tcmodernlib.menu.complete_parents"), () -> action(BookNetwork.Kind.COMPLETE, key, true), false));
        items.add(new MenuItem(Component.translatable("book.tcmodernlib.menu.revoke"), () -> action(BookNetwork.Kind.REVOKE, key, false), true));
        items.add(new MenuItem(Component.translatable("book.tcmodernlib.menu.revoke_dependents"), () -> action(BookNetwork.Kind.REVOKE, key, true), true));
        items.add(new MenuItem(Component.translatable("book.tcmodernlib.menu.copy_key"), () -> {
            minecraft.keyboardHandler.setClipboard(key);
            show(Component.translatable("book.tcmodernlib.toast.copied", key));
        }, false));
        String source = ResearchLoader.source(key);
        if (source != null) {
            items.add(new MenuItem(Component.translatable("book.tcmodernlib.menu.edit"), () -> edit(source), false));
            if (ResearchLoader.inConfig(source))
                items.add(new MenuItem(Component.translatable("book.tcmodernlib.menu.delete"), () -> confirm(Component.translatable("book.tcmodernlib.menu.delete_named", key),
                        () -> BookNetwork.send(new BookNetwork.Action(BookNetwork.Kind.DELETE, source, false))), true));
        } else items.add(new MenuItem(Component.translatable("book.tcmodernlib.menu.code"), () -> show(Component.translatable("book.tcmodernlib.toast.code")), false));
        menu = new Menu(x - left, y - top, items);
    }

    private void categoryMenu(String key, int x, int y) {
        var items = new ArrayList<MenuItem>(List.of(
                new MenuItem(Component.translatable("book.tcmodernlib.menu.category_complete"), () -> action(BookNetwork.Kind.CATEGORY_COMPLETE, key, false), false),
                new MenuItem(Component.translatable("book.tcmodernlib.menu.category_reset"), () -> action(BookNetwork.Kind.CATEGORY_RESET, key, false), true),
                new MenuItem(Component.translatable("book.tcmodernlib.menu.new_category"), () -> minecraft.setScreen(new CategoryEditorScreen(this, "", ResearchForm.newCategory())), false)));
        String source = ResearchLoader.source(key);
        if (source != null) {
            items.add(new MenuItem(Component.translatable("book.tcmodernlib.menu.edit_category"), () -> edit(source), false));
            if (ResearchLoader.inConfig(source))
                items.add(new MenuItem(Component.translatable("book.tcmodernlib.menu.delete_category"), () -> confirm(Component.translatable("book.tcmodernlib.menu.delete_named", key),
                        () -> BookNetwork.send(new BookNetwork.Action(BookNetwork.Kind.DELETE, source, false))), true));
        }
        menu = new Menu(x - left, y - top, items);
    }

    /** Right-click on an empty slot of the map in editing mode. */
    private void slotMenu(int column, int row, int x, int y) {
        var items = new ArrayList<MenuItem>();
        if (category != null) items.add(new MenuItem(Component.translatable("book.tcmodernlib.menu.new_research", column, row),
                () -> minecraft.setScreen(new ResearchEditorScreen(this, "", ResearchForm.newResearch(category, column, row))), false));
        items.add(new MenuItem(Component.translatable("book.tcmodernlib.menu.new_category"),
                () -> minecraft.setScreen(new CategoryEditorScreen(this, "", ResearchForm.newCategory())), false));
        menu = new Menu(x - left, y - top, items);
    }

    /** Opens the form editor for a JSON file by id. */
    private void edit(String source) {
        var text = ResearchLoader.json(source);
        if (text == null) { show(Component.translatable("book.tcmodernlib.toast.code")); return; }
        try {
            var form = ResearchForm.of(com.google.gson.JsonParser.parseString(text).getAsJsonObject());
            minecraft.setScreen(form.category ? new CategoryEditorScreen(this, source, form) : new ResearchEditorScreen(this, source, form));
        } catch (RuntimeException malformed) {
            show(Component.literal(String.valueOf(malformed.getMessage())));
        }
    }

    /** The grid slot under a canvas point of the map. */
    private int[] cellAt(double x, double y) {
        double column = ((x - left - MAP_X - MAP_W / 2.0) / zoom - pan()[0]) / UNIT, row = ((y - top - MAP_Y - MAP_H / 2.0) / zoom - pan()[1]) / UNIT;
        return new int[]{(int) Math.round(column), (int) Math.round(row)};
    }

    /** Whether a research could move to a slot: nothing else of its category sits there. */
    private boolean free(TcResearchEntry moving, int column, int row) {
        var c = current();
        if (c == null) return false;
        for (var other : c.entries())
            if (other != moving && !other.has(TcResearchEntry.Flag.VIRTUAL) && other.column() == column && other.row() == row) return false;
        return true;
    }

    private void action(BookNetwork.Kind kind, String key, boolean cascade) {
        BookNetwork.send(new BookNetwork.Action(kind, key, cascade));
        sound("cameraclack", 0.4F, 0);
    }

    @Override public boolean mouseClicked(double rawX, double rawY, int button) {
        double x = rawX / scale, y = rawY / scale;
        if (menu != null) {
            int w = menu.width(font), mx = left + Math.min(menu.x(), WIDTH - w - 4);
            int h = menu.items().size() * 16 + 6, my = top + Math.min(menu.y(), HEIGHT - h - 4);
            int index = (int) Math.floor((y - my - 3) / 16);
            var chosen = x >= mx && x < mx + w && index >= 0 && index < menu.items().size() ? menu.items().get(index) : null;
            menu = null;
            if (chosen != null) chosen.action().run();
            return true;
        }
        for (var control : controls())
            if (inside(x, y, control.x(), 8, control.w(), 15)) { control(control.id()); return true; }
        if (reader != null) {
            var back = Component.translatable("book.tcmodernlib.back");
            if (inside(x, y, 10, HEADER + 6, font.width(back) + 18, 15)) { reader = null; sound("page", 0.5F, 0.1F); return true; }
            if (spread > 0 && inside(x, y, 6, PAGE_Y + PAGE_H / 2 - 12, 20, 24)) { spread--; sound("page", 0.6F, 0.1F); return true; }
            if ((spread + 1) * 2 < reader.sides() && inside(x, y, WIDTH - 26, PAGE_Y + PAGE_H / 2 - 12, 20, 24)) { spread++; sound("page", 0.6F, 0.1F); return true; }
            return true;
        }
        String clickedCategory = categoryAt(x, y);
        if (clickedCategory != null) {
            if (button == 1 && cheat) categoryMenu(clickedCategory, (int) x, (int) y);
            else if (!clickedCategory.equals(category)) { category = clickedCategory; sound("page", 0.4F, 0.1F); }
            return true;
        }
        if (inMap(x, y)) {
            var entry = nodeAt(x, y);
            if (button == 1) {
                if (cheat && entry != null) researchMenu(entry, (int) x, (int) y);
                else if (cheat) { int[] cell = cellAt(x, y); slotMenu(cell[0], cell[1], (int) x, (int) y); }
                return true;
            }
            dragging = button == 0;
            moved = false;
            pressX = x;
            pressY = y;
            pressed = entry;
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseDragged(double rawX, double rawY, int button, double dx, double dy) {
        if (!dragging) return super.mouseDragged(rawX / scale, rawY / scale, button, dx / scale, dy / scale);
        double x = rawX / scale, y = rawY / scale;
        if (!moved && Math.abs(x - pressX) + Math.abs(y - pressY) > 3) {
            moved = true;
            // In editing mode a JSON research follows the pointer instead of the map.
            movingNode = cheat && pressed != null && ResearchLoader.source(pressed.key()) != null;
        }
        if (moved && !movingNode) {
            var pan = pan();
            pan[0] += (float) (dx / scale / zoom);
            pan[1] += (float) (dy / scale / zoom);
        }
        return true;
    }

    @Override public boolean mouseReleased(double rawX, double rawY, int button) {
        if (dragging && button == 0) {
            dragging = false;
            if (movingNode && pressed != null) {
                int[] cell = cellAt(rawX / scale, rawY / scale);
                if (cell[0] != pressed.column() || cell[1] != pressed.row()) {
                    if (free(pressed, cell[0], cell[1])) {
                        BookNetwork.send(new BookNetwork.Action(BookNetwork.Kind.MOVE, pressed.key(), false, "", cell[0], cell[1]));
                        sound("cameraclack", 0.4F, 0);
                    } else show(Component.translatable("book.tcmodernlib.toast.occupied"));
                }
                movingNode = false;
                pressed = null;
                return true;
            }
            if (!moved && pressed != null && pressed == nodeAt(rawX / scale, rawY / scale)) activate(pressed);
            pressed = null;
            return true;
        }
        return super.mouseReleased(rawX / scale, rawY / scale, button);
    }

    @Override public boolean mouseScrolled(double rawX, double rawY, double delta) {
        double x = rawX / scale, y = rawY / scale;
        if (reader != null) {
            int next = Math.max(0, Math.min((reader.sides() - 1) / 2, spread + (delta > 0 ? -1 : 1)));
            if (next != spread) { spread = next; sound("page", 0.6F, 0.1F); }
            return true;
        }
        if (x >= left + SIDE_X && x < left + SIDE_X + SIDE_W && y >= top + SIDE_Y && y < top + SIDE_Y + SIDE_H) {
            sideScroll = Math.max(0, Math.min(Math.max(0, categories().size() - visibleRows()), sideScroll + (delta > 0 ? -1 : 1)));
            return true;
        }
        if (inMap(x, y)) {
            // Zoom about the cursor.
            float before = zoom;
            zoom = Math.max(0.5F, Math.min(1.75F, zoom * (delta > 0 ? 1.12F : 1 / 1.12F)));
            var pan = pan();
            float cx = (float) (x - left - MAP_X - MAP_W / 2F), cy = (float) (y - top - MAP_Y - MAP_H / 2F);
            pan[0] += cx / zoom - cx / before;
            pan[1] += cy / zoom - cy / before;
            return true;
        }
        return super.mouseScrolled(x, y, delta);
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (menu != null) { menu = null; return true; }
            if (reader != null) { reader = null; sound("page", 0.5F, 0.1F); return true; }
        }
        if (reader != null) {
            if ((key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A) && spread > 0) { spread--; sound("page", 0.6F, 0.1F); return true; }
            if ((key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_D) && (spread + 1) * 2 < reader.sides()) { spread++; sound("page", 0.6F, 0.1F); return true; }
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override public boolean isPauseScreen() { return false; }

    // ---- Scripted access for the smoke test -------------------------------------------------------------------

    float canvasScale() { return scale; }
    int canvasLeft() { return left; }
    int canvasTop() { return top; }
    BookReader reader() { return reader; }
    Set<String> completedKeys() { return completed; }
    void select(String key) { category = key; }
    float[] cellCenter(int column, int row) {
        return new float[]{left + MAP_X + MAP_W / 2F + (pan()[0] + column * UNIT) * zoom, top + MAP_Y + MAP_H / 2F + (pan()[1] + row * UNIT) * zoom};
    }
    float[] nodeCenter(String key) { var entry = byKey.get(key); return entry == null ? null : new float[]{nodeX(entry), nodeY(entry)}; }
    List<TcResearchCategory> snapshot() { return book; }
    boolean cheating() { return cheat; }

    /** Opens the research's category and centres the map on it. */
    void focus(String key) {
        var entry = byKey.get(key);
        if (entry == null) return;
        category = entry.category();
        PANS.put(category, new float[]{-entry.column() * UNIT, -entry.row() * UNIT});
    }

    /** Canvas centre of an item of the open menu, or null. */
    float[] menuPoint(int index) {
        if (menu == null || index >= menu.items().size()) return null;
        int w = menu.width(font), h = menu.items().size() * 16 + 6;
        return new float[]{left + Math.min(menu.x(), WIDTH - w - 4) + 12, top + Math.min(menu.y(), HEIGHT - h - 4) + 3 + index * 16 + 8};
    }

    /** Canvas centre of a header control ({@code toggle}, {@code reload}, {@code all_complete}, {@code all_reset}), or null. */
    float[] controlPoint(String id) {
        for (var control : controls()) if (control.id().equals(id)) return new float[]{left + control.x() + control.w() / 2F, top + 15};
        return null;
    }

    /** Canvas centre of a category row, or null when it is not shown. */
    float[] categoryPoint(String key) {
        var list = categories();
        for (int i = 0; i < visibleRows() && sideScroll + i < list.size(); i++)
            if (list.get(sideScroll + i).key().equals(key)) return new float[]{left + SIDE_X + 40, top + SIDE_Y + 16 + i * ROW + 10};
        return null;
    }
}
