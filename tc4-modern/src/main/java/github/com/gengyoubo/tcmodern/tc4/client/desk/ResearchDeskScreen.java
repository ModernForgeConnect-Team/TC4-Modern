package github.com.gengyoubo.tcmodern.tc4.client.desk;

import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspect;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspects;
import github.com.gengyoubo.tcmodern.lib.client.AspectIcons;
import github.com.gengyoubo.tcmodern.tc4.content.ResearchDeskBlockEntity;
import github.com.gengyoubo.tcmodern.tc4.menu.ResearchDeskMenu;
import github.com.gengyoubo.tcmodern.tc4.network.DeskNetwork;
import github.com.gengyoubo.tcmodern.tc4.research.AspectOrder;
import github.com.gengyoubo.tcmodern.tc4.research.DeskAccessors;
import github.com.gengyoubo.tcmodern.tc4.research.DeskSnapshot;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;
import static github.com.gengyoubo.tcmodern.lib.client.ModernUi.*;

/**
 * Modern research desk view. Everything shown comes from the server snapshot and the menu slots; clicks only
 * send requests that the server checks against the original rules. Aspects move as on the original table: press to
 * pick one up, drop it on a blank hex to write it or on a combine slot to fill it, click it to fill the next combine
 * slot, click a written hex to erase it. Interface sounds are the original research table's own.
 */
public final class ResearchDeskScreen extends AbstractContainerScreen<ResearchDeskMenu> {
    // Left column: search, sort, palette, combiner.
    static final int LEFT_X = 8, LEFT_W = 128, CONTROL_X = 14, CONTROL_W = 116, SEARCH_Y = 36, SORT_Y = 58, SORT_H = 16;
    static final int GRID_X = 15, GRID_Y = 79, CELL = 19, COLUMNS = 6, ROWS = 6;
    static final int WELL_Y = 210, WELL = 20, FIRST_X = 16, SECOND_X = 52, RESULT_X = 96, COMBINE_Y = 236, COMBINE_H = 16;
    // Centre: the note.
    static final int BOARD_X = 142, BOARD_Y = 30, BOARD_W = 160, BOARD_H = 150, BOARD_CX = BOARD_X + BOARD_W / 2, BOARD_CY = 107;
    // Right column: workspace and archive.
    static final int RIGHT_X = 308, RIGHT_W = 124, BUTTON_Y = 84, BUTTON_H = 16, FILE_X = 316, COPY_X = 374, BUTTON_W = 54;
    static final int LIST_X = 314, LIST_Y = 122, LIST_W = 114, ROW = 18, LIST_ROWS = 7;
    private static final int[][] NEIGHBOURS = {{1, 0}, {1, -1}, {0, -1}, {-1, 0}, {-1, 1}, {0, 1}};
    private static final String[] SORT_KEYS = {"desk.tc4modern.sort_short.original", "desk.tc4modern.sort_short.amount",
            "desk.tc4modern.sort_short.tier", "desk.tc4modern.sort_short.alphabetical"};
    /** Kept for the session so the desk reopens as it was left. */
    static AspectOrder.Mode mode = AspectOrder.Mode.ORIGINAL;

    private final Map<String, TcAspect> aspects = new HashMap<>();
    private final Map<String, String> compounds = new HashMap<>();
    private final Map<String, Float> motion = new HashMap<>();
    private final Map<String, Long> written = new HashMap<>();
    private final Random random = new Random();
    private AspectOrder order;
    private EditBox search;
    private String query = "";
    private List<String> palette = List.of();
    private DeskSnapshot paletteSource, boardSource;
    private String paletteKey = "";
    private int paletteScroll, archiveScroll;
    /** The aspect being carried, and the palette entry it was picked up from. */
    private String dragged, pressed, first, second;
    private ItemStack inkStack = ItemStack.EMPTY;
    private int inkLeft = -1, inkCapacity;
    private float scale = 1, frame, sortSlide = -1;
    private long lastFrame;

    public ResearchDeskScreen(ResearchDeskMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = ResearchDeskMenu.WIDTH;
        imageHeight = ResearchDeskMenu.HEIGHT;
        var registry = TcModernApi.runtime().map(TcAspects::snapshot).orElse(List.of());
        order = new AspectOrder(registry);
        for (var aspect : registry) {
            aspects.put(aspect.tag(), aspect);
            if (aspect.components().size() == 2) compounds.put(pair(aspect.components().get(0), aspect.components().get(1)), aspect.tag());
        }
    }

    private static String pair(String a, String b) { return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a; }

    /**
     * The desk is larger than the 427x240 a default window gives at GUI scale 2, so on small screens it lays out on
     * a larger virtual canvas and is drawn scaled down; mouse input is mapped back onto that canvas.
     */
    @Override protected void init() {
        // Screen.init(Minecraft, int, int) has just set the real size; every (re)init starts from it.
        // A minimized window reports no size at all; keep the canvas unscaled then.
        scale = width < 64 || height < 64 ? 1 : Math.min(1F, Math.min((width - 4F) / imageWidth, (height - 4F) / imageHeight));
        width = Math.round(width / scale);
        height = Math.round(height / scale);
        super.init();
        search = new EditBox(font, leftPos + CONTROL_X + 18, topPos + SEARCH_Y + 5, CONTROL_W - 24, 10, Component.translatable("desk.tc4modern.search"));
        search.setBordered(false);
        search.setMaxLength(32);
        search.setTextColor(TEXT);
        search.setHint(Component.translatable("desk.tc4modern.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(query);
        search.setResponder(value -> { query = value; paletteScroll = 0; });
        addRenderableWidget(search);
    }

    /** Canvas to real GUI coordinates, for scripted input. */
    float scale() { return scale; }

    DeskSnapshot snapshot() { return menu.snapshot(); }

    List<String> palette() {
        var snapshot = snapshot();
        String key = mode + "\u0000" + query;
        if (snapshot != paletteSource || !key.equals(paletteKey)) {
            palette = order.apply(snapshot.pools(), mode, query, AspectIcons::name);
            paletteSource = snapshot;
            paletteKey = key;
        }
        return palette;
    }

    /** Left edge of the {@code index}-th sort segment, on the canvas. */
    static int sortSegmentX(int index) { return CONTROL_X + 2 + index * ((CONTROL_W - 4) / 4); }

    @Override protected void containerTick() {
        super.containerTick();
        var tools = menu.getSlot(ResearchDeskBlockEntity.TOOLS).getItem();
        if (!ItemStack.matches(tools, inkStack)) {
            inkStack = tools.copy();
            inkLeft = -1;
            if (!tools.isEmpty()) TcModernApi.runtime().ifPresent(runtime -> {
                try {
                    var legacy = runtime.mappings().toLegacy(LegacyMappings.ITEM_STACK, tools).orElse(null);
                    if (legacy == null) return;
                    var view = runtime.accessor(DeskAccessors.StackView.class, legacy);
                    inkCapacity = view.capacity();
                    inkLeft = Math.max(0, inkCapacity - view.damage());
                } catch (RuntimeException unreadable) { inkLeft = -1; }
            });
        }
    }

    // ---- Layout helpers -------------------------------------------------------------------------------------

    private boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= leftPos + x && mx < leftPos + x + w && my >= topPos + y && my < topPos + y + h;
    }

    private int maxPaletteScroll() { return Math.max(0, (palette().size() + COLUMNS - 1) / COLUMNS - ROWS); }
    private int maxArchiveScroll() { return Math.max(0, snapshot().archive().size() - LIST_ROWS); }

    private String paletteAt(double mx, double my) {
        if (!inside(mx, my, GRID_X, GRID_Y, COLUMNS * CELL, ROWS * CELL)) return null;
        int column = (int) ((mx - leftPos - GRID_X) / CELL), row = (int) ((my - topPos - GRID_Y) / CELL);
        int index = (paletteScroll + row) * COLUMNS + column;
        var list = palette();
        return index < list.size() ? list.get(index) : null;
    }

    private DeskSnapshot.Filed archiveAt(double mx, double my) {
        if (!inside(mx, my, LIST_X, LIST_Y, LIST_W, LIST_ROWS * ROW)) return null;
        int index = archiveScroll + (int) ((my - topPos - LIST_Y) / ROW);
        var archive = snapshot().archive();
        return index < archive.size() ? archive.get(index) : null;
    }

    /** Hex circumradius that fits the current note between the title row and the progress bar. */
    float hexSize(DeskSnapshot.Sheet sheet) {
        int radius = 1;
        for (var cell : sheet.cells()) radius = Math.max(radius, (Math.abs(cell.q()) + Math.abs(cell.r()) + Math.abs(cell.q() + cell.r())) / 2);
        float byWidth = (BOARD_W - 12F) / (3F * radius + 2), byHeight = (BOARD_H - 30F) / ((float) Math.sqrt(3) * (2 * radius + 1));
        return Math.min(12F, Math.min(byWidth, byHeight));
    }

    float cellX(DeskSnapshot.Cell cell, float size) { return leftPos + BOARD_CX + 1.5F * size * cell.q(); }
    float cellY(DeskSnapshot.Cell cell, float size) { return topPos + BOARD_CY + (float) (Math.sqrt(3) * size * (cell.r() + cell.q() / 2.0)); }

    private DeskSnapshot.Cell cellAt(double mx, double my) {
        var sheet = snapshot().sheet();
        if (sheet == null) return null;
        float size = hexSize(sheet);
        for (var cell : sheet.cells()) {
            double dx = Math.abs(mx - cellX(cell, size)), dy = Math.abs(my - cellY(cell, size));
            if (dy <= size * Math.sqrt(3) / 2 && dy <= Math.sqrt(3) * (size - dx)) return cell;
        }
        return null;
    }

    private String combination() {
        return first == null || second == null ? null : compounds.get(pair(first, second));
    }

    private boolean canCombine() {
        var result = combination();
        return result != null && snapshot().pool(first) > 0 && snapshot().pool(second) > 0;
    }

    private boolean hasInk() { return inkLeft > 0; }

    private boolean canFile() {
        var s = snapshot();
        return !menu.getSlot(ResearchDeskBlockEntity.NOTE).getItem().isEmpty() && s.cabinets() > 0 && s.archive().size() < s.capacity();
    }

    private boolean canCopy() {
        var s = snapshot();
        var sheet = s.sheet();
        if (sheet == null || !sheet.complete() || !s.duplicate() || sheet.copyCost().isEmpty()) return false;
        for (var cost : sheet.copyCost().entrySet()) if (s.pool(cost.getKey()) < cost.getValue()) return false;
        var inventory = minecraft.player.getInventory();
        return inventory.contains(new ItemStack(Items.PAPER)) && inventory.contains(new ItemStack(Items.INK_SAC));
    }

    /** Hexes reached from the first fixed hex through connected aspects, as the original completion check walks them. */
    private Set<String> connected(DeskSnapshot.Sheet sheet) {
        var byKey = new HashMap<String, DeskSnapshot.Cell>();
        sheet.cells().forEach(cell -> byKey.put(cell.key(), cell));
        var result = new HashSet<String>();
        var start = sheet.cells().stream().filter(cell -> cell.type() == 1).findFirst().orElse(null);
        if (start == null) return result;
        var queue = new ArrayDeque<DeskSnapshot.Cell>(List.of(start));
        result.add(start.key());
        while (!queue.isEmpty()) {
            var cell = queue.poll();
            for (var step : NEIGHBOURS) {
                var next = byKey.get((cell.q() + step[0]) + ":" + (cell.r() + step[1]));
                if (next != null && next.type() >= 1 && !result.contains(next.key()) && order.graph().connects(cell.aspect(), next.aspect())) {
                    result.add(next.key());
                    queue.add(next);
                }
            }
        }
        return result;
    }

    // ---- Animation ------------------------------------------------------------------------------------------

    /** Eases the named value toward 1 while {@code on}, toward 0 otherwise. */
    private float animate(String id, boolean on) {
        float value = approach(motion.getOrDefault(id, 0F), on ? 1 : 0, frame, 14);
        motion.put(id, value);
        return value;
    }

    /** Remembers when each aspect appeared on the note so it can pop in. */
    private void trackWrites(DeskSnapshot.Sheet sheet) {
        if (snapshot() == boardSource) return;
        var previous = boardSource == null || boardSource.sheet() == null ? null : boardSource.sheet();
        boardSource = snapshot();
        if (sheet == null || previous == null || !previous.key().equals(sheet.key())) { written.clear(); return; }
        var before = new HashMap<String, String>();
        previous.cells().forEach(cell -> before.put(cell.key(), cell.aspect()));
        long now = System.currentTimeMillis();
        for (var cell : sheet.cells())
            if (cell.aspect() != null && !cell.aspect().equals(before.get(cell.key()))) written.put(cell.key(), now);
    }

    private static float easeOutBack(float t) {
        float c = 1.7F, u = t - 1;
        return 1 + (c + 1) * u * u * u + c * u * u;
    }

    // ---- Rendering ------------------------------------------------------------------------------------------

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        frame = lastFrame == 0 ? 0 : Math.min(0.1F, (now - lastFrame) / 1e9F);
        lastFrame = now;
        int x = Math.round(mouseX / scale), y = Math.round(mouseY / scale);
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1);
        renderBackground(g);
        super.render(g, x, y, partialTick);
        if (dragged != null) renderCarried(g, x, y);
        else {
            renderTooltip(g, x, y);
            renderOwnTooltips(g, x, y);
        }
        g.pose().popPose();
    }

    /** Drops the carried aspect: a blank hex is written, a combine slot is filled, its own tile fills the next slot. */
    @Override public boolean mouseReleased(double mx, double my, int button) {
        mx /= scale;
        my /= scale;
        if (button != 0 || dragged == null) return super.mouseReleased(mx, my, button);
        String tag = dragged, source = pressed;
        dragged = pressed = null;
        var cell = cellAt(mx, my);
        var sheet = snapshot().sheet();
        if (cell != null && cell.type() == 0 && sheet != null && !sheet.complete()) {
            if (hasInk() && snapshot().pool(tag) > 0) {
                send(DeskNetwork.Kind.PLACE, cell.q(), cell.r(), tag, null);
                sound("write", 0.2F, 0);
            }
        } else if (inside(mx, my, FIRST_X - 4, WELL_Y - 4, WELL + 8, WELL + 8)) { first = tag; aspectSound(); }
        else if (inside(mx, my, SECOND_X - 4, WELL_Y - 4, WELL + 8, WELL + 8)) { second = tag; aspectSound(); }
        else if (tag.equals(paletteAt(mx, my)) && tag.equals(source)) fill(tag);
        return true;
    }

    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragged != null) return true;
        return super.mouseDragged(mx / scale, my / scale, button, dx / scale, dy / scale);
    }

    /** A plain click on an aspect fills the first empty combine slot. */
    private void fill(String tag) {
        if (first == null) first = tag;
        else if (second == null) second = tag;
        else return;
        aspectSound();
    }

    private void combine(String a, String b) {
        if (a == null || b == null || compounds.get(pair(a, b)) == null || snapshot().pool(a) <= 0 || snapshot().pool(b) <= 0) return;
        send(DeskNetwork.Kind.COMBINE, 0, 0, a, b);
        sound("hhon", 0.3F, 0);
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) { }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        window(g, x, y, imageWidth, imageHeight);
        // Header: a small violet hex mark, the title and the linked-cabinet count; the ink saving as a chip.
        hex(g, x + 18, y + 15, 6.5F, ACCENT_LIGHT, ACCENT_DEEP);
        hex(g, x + 18, y + 15, 2.5F, 0xFF1A1630);
        text(g, font, title, x + 30, y + 11, TEXT);
        var linked = Component.translatable("desk.tc4modern.linked", snapshot().cabinets());
        text(g, font, linked, x + 34 + font.width(title), y + 11, FAINT);
        var saving = Component.translatable("desk.tc4modern.ink_saving");
        int chipW = font.width(saving) + 16;
        round(g, x + imageWidth - chipW - 10, y + 8, chipW, 15, 7.5F, alpha(ACCENT, 0.22F));
        circle(g, x + imageWidth - chipW - 3, y + 15.5F, 2, ACCENT_LIGHT);
        text(g, font, saving, x + imageWidth - chipW + 2, y + 12, ACCENT_LIGHT);

        renderLeft(g, mouseX, mouseY);
        renderBoard(g, mouseX, mouseY);
        renderRight(g, mouseX, mouseY);
        for (int i = 2; i < menu.slots.size(); i++) slot(g, x + menu.slots.get(i).x, y + menu.slots.get(i).y);
    }

    private void renderLeft(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        card(g, x + LEFT_X, y + 30, LEFT_W, 228);

        // Search: a pill that lights its ring while focused.
        float focus = animate("search", search.isFocused());
        round(g, x + CONTROL_X, y + SEARCH_Y, CONTROL_W, 18, 9, WELL);
        if (focus > 0.01F) ring(g, x + CONTROL_X, y + SEARCH_Y, CONTROL_W, 18, 9, 1, alpha(ACCENT, focus));
        magnifier(g, x + CONTROL_X + 6, y + SEARCH_Y + 4.5F, mix(FAINT, ACCENT_LIGHT, focus));

        // Sort: a segmented control whose highlight slides between the four orders.
        int segment = (CONTROL_W - 4) / 4;
        round(g, x + CONTROL_X, y + SORT_Y, CONTROL_W, SORT_H, 8, WELL);
        sortSlide = sortSlide < 0 ? mode.ordinal() : approach(sortSlide, mode.ordinal(), frame, 16);
        round(g, x + CONTROL_X + 2 + sortSlide * segment, y + SORT_Y + 2, segment, SORT_H - 4, 6, ACCENT_LIGHT, ACCENT);
        for (int i = 0; i < 4; i++) {
            boolean active = mode.ordinal() == i;
            float hot = animate("sort" + i, !active && inside(mouseX, mouseY, sortSegmentX(i), SORT_Y, segment, SORT_H));
            var label = Component.translatable(SORT_KEYS[i]);
            centered(g, font, label, x + sortSegmentX(i) + segment / 2, y + SORT_Y + 4, active ? 0xFF14121F : mix(MUTED, TEXT, hot));
        }

        var list = palette();
        var snapshot = snapshot();
        var hovered = paletteAt(mouseX, mouseY);
        for (int row = 0; row < ROWS; row++) for (int column = 0; column < COLUMNS; column++) {
            int index = (paletteScroll + row) * COLUMNS + column;
            if (index >= list.size()) break;
            String tag = list.get(index);
            int px = x + GRID_X + column * CELL, py = y + GRID_Y + row * CELL;
            boolean available = snapshot.pool(tag) > 0, chosen = tag.equals(pressed);
            float hot = animate("tile:" + tag, tag.equals(hovered)), pick = animate("pick:" + tag, chosen);
            if (pick > 0.01F) round(g, px - 1, py - 1, CELL + 1, CELL + 1, 6, alpha(ACCENT, 0.35F * pick));
            round(g, px, py, CELL - 1, CELL - 1, 5, mix(SURFACE, SURFACE_HOVER, hot), mix(0x08FFFFFF, SURFACE, hot));
            if (pick > 0.01F) ring(g, px, py, CELL - 1, CELL - 1, 5, 1, alpha(ACCENT_LIGHT, pick));
            g.pose().pushPose();
            g.pose().translate(0, -hot, 0);
            icon(g, tag, px + 1, py + 1, 16);
            g.pose().popPose();
            if (!available) round(g, px, py, CELL - 1, CELL - 1, 5, 0xB0151720);
            badge(g, font, String.valueOf(snapshot.pool(tag)), px + CELL, py + CELL - 7, available ? TEXT : FAINT);
        }
        if (list.isEmpty()) centered(g, font, Component.translatable(snapshot.pools().isEmpty() ? "desk.tc4modern.no_aspects" : "desk.tc4modern.no_match"),
                x + CONTROL_X + CONTROL_W / 2, y + GRID_Y + 50, FAINT);
        scrollbar(g, GRID_X + COLUMNS * CELL + 1, GRID_Y, ROWS * CELL - 1, paletteScroll, maxPaletteScroll(), ROWS);

        g.fill(x + CONTROL_X, y + 197, x + CONTROL_X + CONTROL_W, y + 198, DIVIDER);
        label(g, font, Component.translatable("desk.tc4modern.combine_title"), x + CONTROL_X, y + 200);
        well(g, FIRST_X, first, mouseX, mouseY);
        text(g, font, "+", x + FIRST_X + WELL + 6, y + WELL_Y + 6, FAINT);
        well(g, SECOND_X, second, mouseX, mouseY);
        var result = combination();
        float ready = animate("result", result != null);
        int arrow = mix(MUTED, ACCENT_LIGHT, ready);
        line(g, x + SECOND_X + WELL + 6, y + WELL_Y + 10, x + RESULT_X - 6, y + WELL_Y + 10, 1.5F, arrow);
        line(g, x + RESULT_X - 10, y + WELL_Y + 6, x + RESULT_X - 6, y + WELL_Y + 10, 1.5F, arrow);
        line(g, x + RESULT_X - 10, y + WELL_Y + 14, x + RESULT_X - 6, y + WELL_Y + 10, 1.5F, arrow);
        round(g, x + RESULT_X, y + WELL_Y, WELL, WELL, 6, mix(WELL, alpha(ACCENT, 0.35F), ready));
        if (result != null) {
            ring(g, x + RESULT_X, y + WELL_Y, WELL, WELL, 6, 1, alpha(ACCENT_LIGHT, ready));
            icon(g, result, x + RESULT_X + 2, y + WELL_Y + 2, 16);
        }
        boolean combine = canCombine();
        Component label = combine || first != null || second != null ? Component.translatable("desk.tc4modern.combine")
                : Component.translatable("desk.tc4modern.combine_hint");
        button(g, font, x + CONTROL_X, y + COMBINE_Y, CONTROL_W, COMBINE_H, Component.literal(clip(font, label.getString(), CONTROL_W - 10)),
                combine, true, animate("combine", combine && inside(mouseX, mouseY, CONTROL_X, COMBINE_Y, CONTROL_W, COMBINE_H)));
    }

    private void well(GuiGraphics g, int wx, String tag, int mouseX, int mouseY) {
        int x = leftPos + wx, y = topPos + WELL_Y;
        boolean over = inside(mouseX, mouseY, wx, WELL_Y, WELL, WELL);
        float hot = animate("well" + wx, over && (tag != null || dragged != null));
        round(g, x, y, WELL, WELL, 6, mix(WELL, SURFACE_HOVER, hot));
        if (dragged != null) ring(g, x, y, WELL, WELL, 6, 1, alpha(ACCENT_LIGHT, 0.35F + 0.65F * hot));
        if (tag != null) {
            icon(g, tag, x + 2, y + 2, 16);
            badge(g, font, String.valueOf(snapshot().pool(tag)), x + WELL + 1, y + WELL - 6, snapshot().pool(tag) > 0 ? TEXT : WARN);
        } else ring(g, x + 4, y + 4, WELL - 8, WELL - 8, 3, 1, 0x26FFFFFF);
    }

    private void renderBoard(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        card(g, x + BOARD_X, y + BOARD_Y, BOARD_W, BOARD_H);
        var note = menu.getSlot(ResearchDeskBlockEntity.NOTE).getItem();
        var sheet = snapshot().sheet();
        trackWrites(sheet);
        if (note.isEmpty() || sheet == null) {
            // Empty state: a faint hexagon with a short explanation.
            hexRing(g, x + BOARD_CX, y + BOARD_CY - 14, 16, 1.5F, 0x30FFFFFF);
            hexRing(g, x + BOARD_CX, y + BOARD_CY - 14, 9, 1.5F, 0x20FFFFFF);
            centered(g, font, Component.translatable(note.isEmpty() ? "desk.tc4modern.no_note" : "desk.tc4modern.unreadable"), x + BOARD_CX, y + BOARD_CY + 10, MUTED);
            centered(g, font, Component.translatable("desk.tc4modern.no_note_hint"), x + BOARD_CX, y + BOARD_CY + 22, FAINT);
            return;
        }
        float size = hexSize(sheet);
        var reached = connected(sheet);
        long fixed = sheet.cells().stream().filter(cell -> cell.type() == 1).count();
        long joined = sheet.cells().stream().filter(cell -> cell.type() == 1 && reached.contains(cell.key())).count();
        String fraction = sheet.complete() ? "✔" : joined + " / " + fixed;
        int fractionW = font.width(fraction) + 12;
        g.drawString(font, clip(font, note.getHoverName().getString(), BOARD_W - fractionW - 20), x + BOARD_X + 8, y + BOARD_Y + 8, TEXT, false);
        round(g, x + BOARD_X + BOARD_W - fractionW - 6, y + BOARD_Y + 5, fractionW, 14, 7, alpha(sheet.complete() ? GOOD : ACCENT, 0.22F));
        text(g, font, fraction, x + BOARD_X + BOARD_W - fractionW, y + BOARD_Y + 8, sheet.complete() ? GOOD : ACCENT_LIGHT);

        var hovered = cellAt(mouseX, mouseY);
        var byKey = new HashMap<String, DeskSnapshot.Cell>();
        sheet.cells().forEach(cell -> byKey.put(cell.key(), cell));
        float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 380.0));
        // Cells: soft glass hexes; fixed ones glow gold, written ones carry the accent once they join the network.
        for (var cell : sheet.cells()) {
            float cx = cellX(cell, size), cy = cellY(cell, size);
            boolean lit = reached.contains(cell.key());
            boolean target = dragged != null ? cell.type() == 0 : cell.type() == 2;
            float hot = animate("cell:" + cell.key(), cell == hovered && !sheet.complete() && target);
            if (cell.type() == 1) {
                hex(g, cx, cy, size + 2.5F, alpha(GOLD, 0.12F + 0.10F * (lit ? pulse : 0)));
                hex(g, cx, cy, size - 0.6F, 0x40F2C14E, 0x18F2C14E);
                hexRing(g, cx, cy, size - 0.6F, 1.4F, GOLD);
            } else {
                hex(g, cx, cy, size - 0.6F, mix(0x16FFFFFF, 0x30FFFFFF, hot), mix(0x08FFFFFF, 0x20FFFFFF, hot));
                if (cell.type() == 2) hexRing(g, cx, cy, size - 0.6F, 1.2F, mix(lit ? ACCENT_LIGHT : alpha(INK, 0.8F), WARN, hot));
                else hexRing(g, cx, cy, size - 0.6F, 1, mix(dragged != null ? 0x30FFFFFF : 0x14FFFFFF, alpha(ACCENT_LIGHT, 0.9F), hot));
            }
        }
        // Links between neighbours whose aspects compose: a soft glow under a bright core when they reach the network.
        for (var cell : sheet.cells()) for (var step : NEIGHBOURS) {
            var next = byKey.get((cell.q() + step[0]) + ":" + (cell.r() + step[1]));
            if (next == null || cell.key().compareTo(next.key()) > 0 || !order.graph().connects(cell.aspect(), next.aspect())) continue;
            boolean lit = reached.contains(cell.key()) && reached.contains(next.key());
            float x1 = cellX(cell, size), y1 = cellY(cell, size), x2 = cellX(next, size), y2 = cellY(next, size);
            if (lit) line(g, x1, y1, x2, y2, 5, alpha(ACCENT, 0.18F + 0.12F * pulse));
            line(g, x1, y1, x2, y2, lit ? 1.6F : 1.2F, lit ? 0xF0D4C6FF : 0x60A0A6B8);
        }
        int iconSize = Math.min(16, Math.round(size * 1.45F));
        long now = System.currentTimeMillis();
        for (var cell : sheet.cells()) {
            String tag = cell.aspect();
            boolean ghost = false;
            if (tag == null && cell == hovered && cell.type() == 0 && dragged != null && !sheet.complete()) { tag = dragged; ghost = true; }
            if (tag == null) continue;
            float cx = cellX(cell, size), cy = cellY(cell, size), grow = 1;
            var since = written.get(cell.key());
            if (since != null) {
                float t = Math.min(1, (now - since) / 260F);
                grow = 0.55F + 0.45F * easeOutBack(t);
                if (t < 1) hexRing(g, cx, cy, size + 3 * t, 1.5F, alpha(ACCENT_LIGHT, 1 - t));
            }
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0);
            g.pose().scale(grow * iconSize / 16F, grow * iconSize / 16F, 1);
            icon(g, tag, -8, -8, 16);
            g.pose().popPose();
            if (ghost) hex(g, cx, cy, size - 0.6F, 0x90161822);
        }
        float progress = fixed == 0 ? 0 : joined / (float) fixed;
        progress(g, x + BOARD_X + 10, y + BOARD_Y + BOARD_H - 9, BOARD_W - 20, 3, sheet.complete() ? 1 : progress, sheet.complete() ? GOOD : ACCENT);
    }

    private void renderRight(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        var snapshot = snapshot();
        card(g, x + RIGHT_X, y + 30, RIGHT_W, 228);
        slot(g, x + ResearchDeskMenu.TOOLS_X, y + ResearchDeskMenu.TOOLS_Y);
        slot(g, x + ResearchDeskMenu.NOTE_X, y + ResearchDeskMenu.NOTE_Y);

        int infoX = x + ResearchDeskMenu.TOOLS_X + 22, infoW = RIGHT_X + RIGHT_W - ResearchDeskMenu.TOOLS_X - 30;
        text(g, font, Component.translatable("desk.tc4modern.ink"), infoX, y + ResearchDeskMenu.TOOLS_Y - 1, TEXT);
        if (inkLeft >= 0) {
            String amount = inkLeft + "/" + inkCapacity;
            text(g, font, amount, infoX + infoW - font.width(amount), y + ResearchDeskMenu.TOOLS_Y - 1, inkLeft > 0 ? FAINT : WARN);
            progress(g, infoX, y + ResearchDeskMenu.TOOLS_Y + 10, infoW - 34, 3, inkCapacity == 0 ? 0 : inkLeft / (float) inkCapacity, inkLeft > 0 ? INK : WARN);
        } else text(g, font, Component.translatable("desk.tc4modern.no_tools"), infoX, y + ResearchDeskMenu.TOOLS_Y + 8, WARN);
        // Five dots after the bar: the fifth written hex is free.
        int writes = menu.desk() == null ? 0 : menu.desk().writes();
        for (int i = 0; i < ResearchDeskBlockEntity.INK_FREE_EVERY; i++) {
            boolean last = i == ResearchDeskBlockEntity.INK_FREE_EVERY - 1;
            circle(g, infoX + infoW - 30 + i * 6.5F, y + ResearchDeskMenu.TOOLS_Y + 11.5F, last ? 2.2F : 1.8F,
                    i < writes ? INK : last ? alpha(GOOD, 0.9F) : 0x30FFFFFF);
        }

        var note = menu.getSlot(ResearchDeskBlockEntity.NOTE).getItem();
        var sheet = snapshot.sheet();
        text(g, font, Component.translatable("desk.tc4modern.note"), infoX, y + ResearchDeskMenu.NOTE_Y - 1, TEXT);
        Component state = note.isEmpty() ? Component.translatable("desk.tc4modern.note_empty")
                : sheet == null ? Component.translatable("desk.tc4modern.unreadable")
                : sheet.complete() ? Component.translatable("desk.tc4modern.note_done", sheet.copies())
                : Component.translatable("desk.tc4modern.note_working");
        int stateColor = sheet != null && sheet.complete() ? GOOD : note.isEmpty() ? FAINT : ACCENT_LIGHT;
        circle(g, infoX + 2, y + ResearchDeskMenu.NOTE_Y + 12.5F, 2, stateColor);
        g.drawString(font, clip(font, state.getString(), infoW - 8), infoX + 7, y + ResearchDeskMenu.NOTE_Y + 9, stateColor, false);

        boolean file = canFile(), copy = canCopy();
        button(g, font, x + FILE_X, y + BUTTON_Y, BUTTON_W, BUTTON_H, Component.translatable("desk.tc4modern.file"), file, false,
                animate("file", file && inside(mouseX, mouseY, FILE_X, BUTTON_Y, BUTTON_W, BUTTON_H)));
        button(g, font, x + COPY_X, y + BUTTON_Y, BUTTON_W, BUTTON_H, Component.translatable("desk.tc4modern.copy"), copy, true,
                animate("copy", copy && inside(mouseX, mouseY, COPY_X, BUTTON_Y, BUTTON_W, BUTTON_H)));

        g.fill(x + RIGHT_X + 8, y + 106, x + RIGHT_X + RIGHT_W - 8, y + 107, DIVIDER);
        label(g, font, Component.translatable("desk.tc4modern.archive_title"), x + LIST_X, y + 110);
        if (snapshot.cabinets() > 0) {
            String used = snapshot.archive().size() + "/" + snapshot.capacity();
            text(g, font, used, x + LIST_X + LIST_W - font.width(used), y + 110, FAINT);
        }
        var archive = snapshot.archive();
        if (snapshot.cabinets() == 0 || archive.isEmpty()) {
            // Empty state: a drawer glyph and a hint.
            float gx = x + LIST_X + LIST_W / 2F - 10, gy = y + LIST_Y + 18;
            ring(g, gx, gy, 20, 16, 3, 1.2F, 0x30FFFFFF);
            line(g, gx + 7, gy + 8, gx + 13, gy + 8, 1.2F, 0x30FFFFFF);
            int lineY = y + LIST_Y + 42;
            var hint = Component.translatable(snapshot.cabinets() == 0 ? "desk.tc4modern.no_cabinet" : "desk.tc4modern.archive_empty");
            for (var line : font.split(hint, LIST_W)) { g.drawString(font, line, x + LIST_X + (LIST_W - font.width(line)) / 2, lineY, FAINT, false); lineY += 10; }
        }
        var hovered = archiveAt(mouseX, mouseY);
        for (int i = 0; i < LIST_ROWS && archiveScroll + i < archive.size(); i++) {
            var filed = archive.get(archiveScroll + i);
            int rx = x + LIST_X, ry = y + LIST_Y + i * ROW;
            float hot = animate("row" + filed.cabinet() + ":" + filed.slot(), filed == hovered);
            round(g, rx, ry, LIST_W - 5, ROW - 2, 5, mix(0x0CFFFFFF, SURFACE_HOVER, hot));
            if (hot > 0.01F) round(g, rx, ry + 3, 2, ROW - 8, 1, alpha(ACCENT_LIGHT, hot));
            g.renderItem(filed.stack(), rx + 2, ry);
            g.drawString(font, clip(font, filed.stack().getHoverName().getString(), LIST_W - 30), rx + 21, ry + 5, mix(MUTED, TEXT, 0.5F + hot / 2), false);
        }
        scrollbar(g, LIST_X + LIST_W - 3, LIST_Y, LIST_ROWS * ROW - 2, archiveScroll, maxArchiveScroll(), LIST_ROWS);
    }

    /** The carried aspect: a glowing orb in its colour under the cursor, as the original table draws it. */
    private void renderCarried(GuiGraphics g, int mouseX, int mouseY) {
        var aspect = aspects.get(dragged);
        int color = 0xFF000000 | (aspect == null ? 0xFFFFFF : aspect.color());
        float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 160.0));
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        for (int i = 4; i >= 1; i--) circle(g, mouseX, mouseY, 6 + i * 2.5F + pulse, alpha(color, 0.10F));
        circle(g, mouseX, mouseY, 9, alpha(0xFF14121F, 0.85F));
        icon(g, dragged, mouseX - 8, mouseY - 8, 16);
        g.pose().popPose();
    }

    private void scrollbar(GuiGraphics g, int sx, int sy, int height, int scroll, int max, int visible) {
        if (max <= 0) return;
        float x = leftPos + sx, y = topPos + sy;
        round(g, x, y, 2, height, 1, 0x14FFFFFF);
        float thumb = Math.max(10, height * visible / (float) (max + visible));
        float top = y + (height - thumb) * scroll / max;
        round(g, x, top, 2, thumb, 1, alpha(ACCENT_LIGHT, 0.7F));
    }

    private void icon(GuiGraphics g, String tag, int x, int y, int size) {
        var aspect = aspects.get(tag);
        if (aspect == null) aspect = new TcAspect(tag, 0x808080, "", List.of());
        if (size == 16) { AspectIcons.render(g, aspect, x, y); return; }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(size / 16F, size / 16F, 1);
        AspectIcons.render(g, aspect, 0, 0);
        g.pose().popPose();
    }

    private void renderOwnTooltips(GuiGraphics g, int mouseX, int mouseY) {
        if (!menu.getCarried().isEmpty() || hoveredSlot != null && hoveredSlot.hasItem()) return;
        String tag = paletteAt(mouseX, mouseY);
        var cell = tag == null ? cellAt(mouseX, mouseY) : null;
        if (cell != null) tag = cell.aspect();
        if (tag == null && inside(mouseX, mouseY, FIRST_X, WELL_Y, WELL, WELL)) tag = first;
        if (tag == null && inside(mouseX, mouseY, SECOND_X, WELL_Y, WELL, WELL)) tag = second;
        if (tag == null && inside(mouseX, mouseY, RESULT_X, WELL_Y, WELL, WELL)) tag = combination();
        if (tag != null) { g.renderComponentTooltip(font, aspectTooltip(tag, cell), mouseX, mouseY); return; }
        for (int i = 0; i < 4; i++)
            if (inside(mouseX, mouseY, sortSegmentX(i), SORT_Y, (CONTROL_W - 4) / 4, SORT_H)) {
                g.renderTooltip(font, Component.translatable(AspectOrder.Mode.values()[i].key()), mouseX, mouseY);
                return;
            }
        var filed = archiveAt(mouseX, mouseY);
        if (filed != null) {
            var lines = new ArrayList<>(getTooltipFromContainerItem(filed.stack()));
            lines.add(Component.translatable("desk.tc4modern.take_hint").withStyle(ChatFormatting.DARK_PURPLE));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
            return;
        }
        var sheet = snapshot().sheet();
        if (inside(mouseX, mouseY, COPY_X, BUTTON_Y, BUTTON_W, BUTTON_H) && sheet != null && sheet.complete()) {
            var lines = new ArrayList<Component>();
            lines.add(Component.translatable("desk.tc4modern.copy_cost"));
            if (!snapshot().duplicate()) lines.add(Component.translatable("desk.tc4modern.copy_locked").withStyle(ChatFormatting.RED));
            sheet.copyCost().forEach((aspect, amount) -> lines.add(Component.literal(AspectIcons.name(aspect) + " ×" + amount)
                    .withStyle(snapshot().pool(aspect) >= amount ? ChatFormatting.GRAY : ChatFormatting.RED)));
            lines.add(Component.translatable("desk.tc4modern.copy_items").withStyle(ChatFormatting.GRAY));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    private List<Component> aspectTooltip(String tag, DeskSnapshot.Cell cell) {
        var lines = new ArrayList<Component>();
        lines.add(Component.literal(AspectIcons.name(tag)).withStyle(ChatFormatting.AQUA));
        int tier = order.tier(tag);
        lines.add(Component.translatable(tier == 0 ? "desk.tc4modern.primal" : "desk.tc4modern.tier", tier).withStyle(ChatFormatting.GRAY));
        var aspect = aspects.get(tag);
        if (aspect != null && !aspect.primal())
            lines.add(Component.literal(String.join(" + ", aspect.components().stream().map(AspectIcons::name).toList())).withStyle(ChatFormatting.DARK_PURPLE));
        lines.add(Component.translatable("desk.tc4modern.points", snapshot().pool(tag)).withStyle(ChatFormatting.GRAY));
        if (cell != null && cell.type() == 1) lines.add(Component.translatable("desk.tc4modern.fixed").withStyle(ChatFormatting.GOLD));
        if (cell != null && cell.type() == 2) lines.add(Component.translatable("desk.tc4modern.erase_hint").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    // ---- Sounds ---------------------------------------------------------------------------------------------

    /**
     * Plays one of the original research table's sounds, which MFCL serves under their original ids, at the volume
     * and pitch {@code GuiResearchTable} uses for the same action.
     */
    private void sound(String id, float volume, float pitchJitter) {
        var event = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("thaumcraft", id));
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(event, 1 + random.nextFloat() * pitchJitter, volume));
    }

    private void clickSound() { sound("cameraclack", 0.4F, 0); }
    private void aspectSound() { sound("hhoff", 0.2F, 0.1F); }

    // ---- Input ----------------------------------------------------------------------------------------------

    private void send(DeskNetwork.Kind kind, int q, int r, String a, String b) {
        DeskNetwork.send(new DeskNetwork.Action(menu.containerId, kind, q, r, a == null ? "" : a, b == null ? "" : b));
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        mx /= scale;
        my /= scale;
        if (search.isFocused() && !inside(mx, my, CONTROL_X, SEARCH_Y, CONTROL_W, 18)) search.setFocused(false);
        if (inside(mx, my, CONTROL_X, SEARCH_Y, CONTROL_W, 18)) {
            setFocused(search);
            search.setFocused(true);
            if (button == 1) search.setValue("");
            search.mouseClicked(mx, my, 0);
            return true;
        }
        for (int i = 0; i < 4; i++)
            if (inside(mx, my, sortSegmentX(i), SORT_Y, (CONTROL_W - 4) / 4, SORT_H)) {
                if (mode.ordinal() != i) {
                    mode = AspectOrder.Mode.values()[i];
                    paletteScroll = 0;
                    clickSound();
                }
                return true;
            }
        var tag = paletteAt(mx, my);
        if (tag != null) {
            var aspect = aspects.get(tag);
            if (hasShiftDown() && snapshot().expert() && aspect != null && aspect.components().size() == 2) {
                // As on the original table, an expert researcher shift-clicks a compound to combine its two parts.
                combine(aspect.components().get(0), aspect.components().get(1));
            } else if (button == 0 && snapshot().pool(tag) > 0) {
                dragged = pressed = tag;
                aspectSound();
            } else if (button == 1) fill(tag);
            return true;
        }
        if (inside(mx, my, FIRST_X, WELL_Y, WELL, WELL)) { if (first != null) aspectSound(); first = null; return true; }
        if (inside(mx, my, SECOND_X, WELL_Y, WELL, WELL)) { if (second != null) aspectSound(); second = null; return true; }
        if (inside(mx, my, CONTROL_X, COMBINE_Y, CONTROL_W, COMBINE_H)) {
            if (canCombine()) { combine(first, second); clickSound(); }
            return true;
        }
        var cell = cellAt(mx, my);
        var sheet = snapshot().sheet();
        if (cell != null && sheet != null && !sheet.complete()) {
            if (cell.type() == 2 && hasInk()) {
                send(DeskNetwork.Kind.ERASE, cell.q(), cell.r(), null, null);
                sound("erase", 0.2F, 0.1F);
            }
            return true;
        }
        if (inside(mx, my, FILE_X, BUTTON_Y, BUTTON_W, BUTTON_H)) {
            if (canFile()) { send(DeskNetwork.Kind.FILE, 0, 0, null, null); sound("page", 0.4F, 0.1F); }
            return true;
        }
        if (inside(mx, my, COPY_X, BUTTON_Y, BUTTON_W, BUTTON_H)) {
            if (canCopy()) { send(DeskNetwork.Kind.COPY, 0, 0, null, null); clickSound(); }
            return true;
        }
        var filed = archiveAt(mx, my);
        if (filed != null && button == 0) {
            send(DeskNetwork.Kind.TAKE, filed.cabinet(), filed.slot(), null, null);
            sound("page", 0.4F, 0.1F);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override public boolean mouseScrolled(double mx, double my, double delta) {
        mx /= scale;
        my /= scale;
        int step = delta > 0 ? -1 : 1;
        if (inside(mx, my, LEFT_X, GRID_Y, LEFT_W, ROWS * CELL)) {
            int next = Math.max(0, Math.min(maxPaletteScroll(), paletteScroll + step));
            if (next != paletteScroll) { paletteScroll = next; sound("key", 0.3F, 0); }
            return true;
        }
        if (inside(mx, my, RIGHT_X, LIST_Y, RIGHT_W, LIST_ROWS * ROW)) {
            int next = Math.max(0, Math.min(maxArchiveScroll(), archiveScroll + step));
            if (next != archiveScroll) { archiveScroll = next; sound("key", 0.3F, 0); }
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (search.isFocused()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) { search.setFocused(false); return true; }
            return search.keyPressed(key, scan, modifiers) || search.canConsumeInput();
        }
        // Ctrl+F, as in modern apps, jumps to the search field.
        if (key == GLFW.GLFW_KEY_F && hasControlDown()) { setFocused(search); search.setFocused(true); return true; }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override public boolean charTyped(char character, int modifiers) {
        return search.isFocused() ? search.charTyped(character, modifiers) : super.charTyped(character, modifiers);
    }
}
