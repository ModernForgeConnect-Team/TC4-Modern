package github.com.gengyoubo.tcmodern.tc4.client.practice;

import com.mojang.blaze3d.systems.RenderSystem;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspect;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspects;
import github.com.gengyoubo.tcmodern.tc4.client.AspectIcons;
import github.com.gengyoubo.tcmodern.tc4.practice.PracticeAspects;
import github.com.gengyoubo.tcmodern.tc4.practice.PracticePuzzle;
import github.com.gengyoubo.tcmodern.tc4.practice.PracticePuzzle.Hex;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Free, client-local practice: no research unlocks, resource use or server mutations.
 * Drawn on the original Thaumonomicon spread with TC4's own hexes and page ornaments when Thaumcraft's
 * textures are loaded, and on a hand-drawn imitation of it otherwise.
 */
public final class ElementWorkbookScreen extends Screen {
    private static final ResourceLocation BOOK = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_researchbook.png");
    private static final ResourceLocation HEX = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/hex1.png");
    private static final ResourceLocation GOLD_HEX = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/hex2.png");
    private static final String[] NUMERALS = {"I", "II", "III", "IV", "V", "VI", "VII"};
    private static final int INK = 0x3B2414, TEXT = 0x4F3B24, FAINT = 0x8A7458, RED = 0x8B1A10, GREEN = 0x2F6B3A;
    // The book is the original 256x181 spread at 1.5x; each page's writable area and centre line follow it.
    static final int WIDTH = 384, HEIGHT = 272, LEFT_PAGE = 104, RIGHT_PAGE = 280;
    static final int COLUMNS = 6, ROWS = 5, PAGE_SIZE = COLUMNS * ROWS, CELL = 20;
    static final int PALETTE_X = LEFT_PAGE - COLUMNS * CELL / 2, PALETTE_Y = 102, LEVEL_X = RIGHT_PAGE - 70, LEVEL_Y = 14;
    static final int ACTION_X = 207, ACTION_Y = 222, ACTION_WIDTH = 46, BOARD_Y = 128, HEX_SIZE = 12;
    private final PracticeAspects aspects;
    private final Random random = new Random();
    private final List<PageButton> levels = new ArrayList<>();
    private PracticePuzzle puzzle;
    private List<TcAspect> palette = List.of();
    private String selected;
    private int level = 1, page;
    private float scale, left, top;
    private boolean art;
    private PageButton previous, next, reset, hint;
    private Hex lastHint;

    public ElementWorkbookScreen(PracticeAspects aspects) {
        super(Component.translatable("item.tc4modern.element_workbook"));
        this.aspects = aspects;
        generate(1);
    }

    public static void open() {
        var registry = TcModernApi.runtime().map(TcAspects::snapshot).orElse(List.of());
        Minecraft.getInstance().setScreen(new ElementWorkbookScreen(new PracticeAspects(registry)));
    }

    private void generate(int requestedLevel) {
        level = requestedLevel;
        page = 0;
        lastHint = null;
        palette = aspects.available(level);
        selected = palette.isEmpty() ? null : palette.get(0).tag();
        try {
            puzzle = PracticePuzzle.generate(aspects, level, random);
        } catch (IllegalArgumentException missingTier) {
            puzzle = null;
        }
        updateButtons();
    }

    PracticePuzzle puzzle() { return puzzle; }

    /** Canvas to GUI coordinates, for the smoke probe's clicks. */
    double screenX(double x) { return left + x * scale; }
    double screenY(double y) { return top + y * scale; }

    @Override protected void init() {
        scale = Math.min(1.5F, Math.min((width - 8F) / WIDTH, (height - 8F) / HEIGHT));
        left = (width - WIDTH * scale) / 2;
        top = (height - HEIGHT * scale) / 2;
        var resources = Minecraft.getInstance().getResourceManager();
        art = resources.getResource(BOOK).isPresent() && resources.getResource(HEX).isPresent()
                && resources.getResource(GOLD_HEX).isPresent();
        levels.clear();
        for (int i = 1; i <= 7; i++) {
            int requested = i;
            levels.add(addRenderableWidget(new PageButton(LEVEL_X + (i - 1) * CELL, LEVEL_Y, CELL, 18,
                    Component.literal(NUMERALS[i - 1]), Kind.HEX, b -> generate(requested))));
        }
        previous = addRenderableWidget(new PageButton(LEFT_PAGE - 56, 207, 18, 12, Component.literal("<"), Kind.PREVIOUS, b -> changePage(-1)));
        next = addRenderableWidget(new PageButton(LEFT_PAGE + 38, 207, 18, 12, Component.literal(">"), Kind.NEXT, b -> changePage(1)));
        reset = addRenderableWidget(action(0, "workbook.tc4modern.reset", b -> { puzzle.reset(); lastHint = null; updateButtons(); }));
        hint = addRenderableWidget(action(1, "workbook.tc4modern.hint", b -> { lastHint = puzzle.hint().orElse(null); updateButtons(); }));
        addRenderableWidget(action(2, "workbook.tc4modern.new", b -> generate(level)));
        updateButtons();
    }

    private PageButton action(int index, String key, Button.OnPress press) {
        return new PageButton(ACTION_X + index * (ACTION_WIDTH + 4), ACTION_Y, ACTION_WIDTH, 14, Component.translatable(key), Kind.TEXT, press);
    }

    private void updateButtons() {
        if (previous == null) return;
        previous.active = page > 0;
        next.active = (page + 1) * PAGE_SIZE < palette.size();
        reset.active = puzzle != null;
        hint.active = puzzle != null && !puzzle.solved();
        for (int i = 0; i < levels.size(); i++) levels.get(i).active = level != i + 1;
    }

    private void changePage(int delta) {
        page = Math.max(0, Math.min(Math.max(0, (palette.size() - 1) / PAGE_SIZE), page + delta));
        updateButtons();
    }

    // Flat-topped hexes, as on TC4 research notes.
    static int centerX(Hex h) { return RIGHT_PAGE + Math.round(1.5F * HEX_SIZE * h.q()); }
    static int centerY(Hex h) { return BOARD_Y + (int) Math.round(Math.sqrt(3) * HEX_SIZE * (h.r() + h.q() / 2.0)); }
    private double localX(double x) { return (x - left) / scale; }
    private double localY(double y) { return (y - top) / scale; }

    private Hex cellAt(double x, double y) {
        if (puzzle == null) return null;
        for (var cell : puzzle.cells()) {
            double dx = Math.abs(x - centerX(cell)), dy = Math.abs(y - centerY(cell));
            if (dy <= HEX_SIZE * Math.sqrt(3) / 2 - 0.5 && dy <= Math.sqrt(3) * (HEX_SIZE - 0.5 - dx)) return cell;
        }
        return null;
    }

    private TcAspect paletteAt(double x, double y) {
        if (x < PALETTE_X || y < PALETTE_Y) return null;
        int column = (int) ((x - PALETTE_X) / CELL), row = (int) ((y - PALETTE_Y) / CELL);
        if (column >= COLUMNS || row >= ROWS) return null;
        int index = page * PAGE_SIZE + row * COLUMNS + column;
        return index < palette.size() ? palette.get(index) : null;
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double x = localX(mouseX), y = localY(mouseY);
        if (super.mouseClicked(x, y, button)) return true;
        var chosen = paletteAt(x, y);
        if (chosen != null && button == 0) { selected = chosen.tag(); return true; }
        var cell = cellAt(x, y);
        if (cell != null && !puzzle.solved()) {
            if (button == 0 && selected != null) puzzle.place(cell, selected);
            else if (button == 1) puzzle.remove(cell);
            lastHint = null;
            updateButtons();
            return true;
        }
        return false;
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        return super.mouseReleased(localX(x), localY(y), button);
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return super.mouseDragged(localX(x), localY(y), button, dx / scale, dy / scale);
    }

    @Override public boolean mouseScrolled(double x, double y, double delta) {
        double lx = localX(x), ly = localY(y);
        if (lx >= PALETTE_X && lx < PALETTE_X + COLUMNS * CELL && ly >= PALETTE_Y && ly < PALETTE_Y + ROWS * CELL) {
            changePage(delta > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(lx, ly, delta);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int x = (int) localX(mouseX), y = (int) localY(mouseY);
        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0);
        graphics.pose().scale(scale, scale, 1);
        renderBook(graphics);

        graphics.drawCenteredString(font, title, LEFT_PAGE, 15, INK);
        ornament(graphics, LEFT_PAGE, 26);
        int textY = 35;
        for (var line : font.split(Component.translatable("workbook.tc4modern.rules"), 148))
            graphics.drawString(font, line, LEFT_PAGE - 74, (textY += 9) - 9, TEXT, false);
        textY += 3;
        for (var line : font.split(Component.translatable("workbook.tc4modern.controls"), 148))
            graphics.drawString(font, line, LEFT_PAGE - 74, (textY += 9) - 9, FAINT, false);
        renderPalette(graphics, x, y);
        int pages = Math.max(1, (palette.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        centered(graphics, Component.translatable("workbook.tc4modern.page", page + 1, pages), LEFT_PAGE, 209, TEXT);
        if (selected != null) {
            String name = font.plainSubstrByWidth(AspectIcons.name(selected), 120);
            int nameX = LEFT_PAGE - (font.width(name) + 20) / 2;
            AspectIcons.render(graphics, aspects.aspect(selected), nameX, 224);
            graphics.drawString(font, name, nameX + 20, 228, INK, false);
        }

        ornament(graphics, RIGHT_PAGE, 36);
        if (puzzle != null) {
            renderBoard(graphics, x, y);
            long count = puzzle.anchors().keySet().stream().filter(puzzle.connected()::contains).count();
            centered(graphics, puzzle.solved() ? Component.translatable("workbook.tc4modern.solved")
                    : Component.translatable("workbook.tc4modern.progress", count, puzzle.anchors().size()),
                    RIGHT_PAGE, 207, puzzle.solved() ? GREEN : TEXT);
        } else {
            int lineY = 110;
            for (var line : font.split(Component.translatable("workbook.tc4modern.unavailable"), 140))
                graphics.drawString(font, line, RIGHT_PAGE - font.width(line) / 2, (lineY += 10) - 10, RED, false);
        }
        super.render(graphics, x, y, partialTick);
        graphics.pose().popPose();

        var hovered = paletteAt(x, y);
        var cell = cellAt(x, y);
        if (hovered == null && cell != null) hovered = aspects.aspect(puzzle.placed().get(cell));
        if (hovered != null) graphics.renderComponentTooltip(font, tooltip(hovered, cell != null && puzzle.anchors().containsKey(cell)), mouseX, mouseY);
        // Widget tooltips would be placed in canvas coordinates, so level names are drawn here instead.
        for (int i = 0; i < levels.size(); i++)
            if (levels.get(i).isMouseOver(x, y)) graphics.renderTooltip(font, Component.translatable("workbook.tc4modern.level", i + 1), mouseX, mouseY);
    }

    private void centered(GuiGraphics graphics, Component text, int x, int y, int color) {
        graphics.drawString(font, text, x - font.width(text) / 2, y, color, false);
    }

    private void renderBook(GuiGraphics graphics) {
        if (art) {
            blit(graphics, BOOK, 0, 0, WIDTH, HEIGHT, 0, 0, 512, 362, 512, 0xFFFFFFFF);
            return;
        }
        // Leather boards, two parchment pages and a shaded gutter, after gui_researchbook.png.
        graphics.fill(4, 2, WIDTH - 4, HEIGHT - 2, 0xFF3A160C);
        graphics.fill(6, 4, WIDTH - 6, HEIGHT - 4, 0xFF6E2A18);
        graphics.fill(18, 8, 188, 258, 0xFFD9C8A0);
        graphics.fill(196, 8, 366, 258, 0xFFD9C8A0);
        graphics.fill(20, 10, 186, 256, 0xFFF0E4C6);
        graphics.fill(198, 10, 364, 256, 0xFFF0E4C6);
        graphics.fill(184, 10, 188, 256, 0x40503018);
        graphics.fill(196, 10, 200, 256, 0x40503018);
    }

    /** TC4's page divider, or a plain rule without the original art. */
    private void ornament(GuiGraphics graphics, int x, int y) {
        if (art) blit(graphics, BOOK, x - 68, y, 135, 7, 57, 366, 180, 10, 512, 0xFFFFFFFF);
        else graphics.fill(x - 60, y + 3, x + 60, y + 4, 0xFF6B4A2B);
    }

    private void renderPalette(GuiGraphics graphics, int mouseX, int mouseY) {
        var hovered = paletteAt(mouseX, mouseY);
        for (int i = page * PAGE_SIZE; i < Math.min(palette.size(), (page + 1) * PAGE_SIZE); i++) {
            var aspect = palette.get(i);
            int px = PALETTE_X + i % PAGE_SIZE % COLUMNS * CELL, py = PALETTE_Y + i % PAGE_SIZE / COLUMNS * CELL;
            if (aspect.tag().equals(selected)) {
                // Larger than the cell so the gold rim shows around the 16 px icon.
                hex(graphics, px + CELL / 2, py + CELL / 2, CELL / 2 + 1, 0x60E0B040);
                hexArt(graphics, true, px + CELL / 2, py + CELL / 2, CELL + 4, 0xFFFFFFFF);
            }
            else if (aspect == hovered) hexArt(graphics, false, px + CELL / 2, py + CELL / 2, CELL, 0x80FFFFFF);
            AspectIcons.render(graphics, aspect, px + 2, py + 2);
        }
    }

    private void renderBoard(GuiGraphics graphics, int mouseX, int mouseY) {
        var hovered = cellAt(mouseX, mouseY);
        var connected = puzzle.connected();
        for (var cell : puzzle.cells()) {
            int x = centerX(cell), y = centerY(cell);
            boolean anchor = puzzle.anchors().containsKey(cell);
            if (!art) {
                hex(graphics, x, y, HEX_SIZE, anchor ? 0xFFB07A1E : 0xFF6B4A2B);
                hex(graphics, x, y, HEX_SIZE - 1, 0xFFF0E4C6);
            }
            if (anchor) hex(graphics, x, y, HEX_SIZE - 1, 0x30E0B040);
            if (cell.equals(lastHint)) hex(graphics, x, y, HEX_SIZE - 1, 0x704FA0C8);
            else if (cell.equals(hovered) && !puzzle.solved()) hex(graphics, x, y, HEX_SIZE - 1, 0x306B4A2B);
        }
        for (var cell : puzzle.cells()) for (var neighbor : cell.neighbors()) {
            if (cell.q() > neighbor.q() || cell.q() == neighbor.q() && cell.r() >= neighbor.r()) continue;
            if (aspects.connects(puzzle.placed().get(cell), puzzle.placed().get(neighbor)))
                line(graphics, centerX(cell), centerY(cell), centerX(neighbor), centerY(neighbor),
                        connected.contains(cell) ? 0xC07A4FB0 : 0x90A08C6C);
        }
        if (art) for (var cell : puzzle.cells()) {
            boolean anchor = puzzle.anchors().containsKey(cell);
            hexArt(graphics, anchor, centerX(cell), centerY(cell), 2 * HEX_SIZE, anchor ? 0xFFFFFFFF : 0x90FFFFFF);
        }
        for (var entry : puzzle.placed().entrySet())
            AspectIcons.render(graphics, aspects.aspect(entry.getValue()), centerX(entry.getKey()) - 8, centerY(entry.getKey()) - 8);
    }

    /** One of TC4's research-note hexes (hex2 is the gold one) centred on a point, or a drawn outline without the art. */
    private void hexArt(GuiGraphics graphics, boolean gold, int x, int y, int size, int color) {
        if (art) {
            blit(graphics, gold ? GOLD_HEX : HEX, x - size / 2, y - size / 2, size, size, 0, 0, 32, 32, 32, color);
        } else {
            int alpha = color >>> 24;
            hex(graphics, x, y, size / 2, alpha << 24 | (gold ? 0xB07A1E : 0x6B4A2B));
            hex(graphics, x, y, size / 2 - 1, alpha << 24 | (gold ? 0xF1D88A : 0xE3D2A8));
        }
    }

    private static void blit(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height,
                             int u, int v, int uWidth, int vHeight, int textureSize, int color) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F, (color >>> 24) / 255F);
        graphics.blit(texture, x, y, width, height, u, v, uWidth, vHeight, textureSize, textureSize);
        graphics.setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    /** Filled flat-topped hexagon of circumradius {@code size}, one column at a time so translucent fills stay even. */
    private static void hex(GuiGraphics graphics, int x, int y, int size, int color) {
        for (int dx = -size; dx <= size; dx++) {
            int half = (int) Math.floor(Math.min(size * Math.sqrt(3) / 2, (size - Math.abs(dx)) * Math.sqrt(3)));
            graphics.fill(x + dx, y - half, x + dx + 1, y + half + 1, color);
        }
    }

    /** One pixel wide, each pixel once, so translucent links do not darken where steps would overlap. */
    private static void line(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / steps, y = y1 + (y2 - y1) * i / steps;
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    private List<Component> tooltip(TcAspect aspect, boolean fixed) {
        var lines = new ArrayList<Component>();
        lines.add(Component.literal(AspectIcons.name(aspect.tag())));
        lines.add(Component.translatable("workbook.tc4modern.tier", aspects.tier(aspect.tag())).withStyle(ChatFormatting.GRAY));
        if (!aspect.primal()) lines.add(Component.literal(String.join(" + ", aspect.components().stream().map(AspectIcons::name).toList()))
                .withStyle(ChatFormatting.DARK_PURPLE));
        if (fixed) lines.add(Component.translatable("workbook.tc4modern.fixed").withStyle(ChatFormatting.GOLD));
        return lines;
    }

    @Override public boolean isPauseScreen() { return false; }

    private enum Kind { TEXT, HEX, PREVIOUS, NEXT }

    /** Buttons inked onto the page instead of vanilla stone slabs; arrows are TC4's page-turn curls. */
    private final class PageButton extends Button {
        private final Kind kind;

        PageButton(int x, int y, int width, int height, Component label, Kind kind, OnPress press) {
            super(x, y, width, height, label, press, DEFAULT_NARRATION);
            this.kind = kind;
        }

        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean hot = active && isHovered();
            int x = getX(), y = getY(), cx = x + width / 2;
            switch (kind) {
                case HEX -> {
                    // The current level is the inactive one and wears the gold hex.
                    hexArt(graphics, !active, cx, y + height / 2, width, !active || hot ? 0xFFFFFFFF : 0x80FFFFFF);
                    centered(graphics, getMessage(), cx, y + (height - 8) / 2, !active ? INK : hot ? RED : FAINT);
                }
                case PREVIOUS, NEXT -> {
                    if (!active) return;
                    if (art) blit(graphics, BOOK, x, y, width, height, kind == Kind.PREVIOUS ? 0 : 24, 368, 24, 16, 512,
                            hot ? 0xFFFFFFFF : 0xFFC8B8B8);
                    else centered(graphics, getMessage(), cx, y + 2, hot ? RED : INK);
                }
                case TEXT -> {
                    int frame = active ? 0xFF9C7C52 : 0x609C7C52;
                    graphics.fill(x, y, x + width, y + height, hot ? 0x406B4A2B : 0x186B4A2B);
                    graphics.fill(x + 1, y, x + width - 1, y + 1, frame);
                    graphics.fill(x + 1, y + height - 1, x + width - 1, y + height, frame);
                    graphics.fill(x, y + 1, x + 1, y + height - 1, frame);
                    graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, frame);
                    centered(graphics, getMessage(), cx, y + (height - 8) / 2, !active ? FAINT : hot ? RED : INK);
                }
            }
        }
    }
}
