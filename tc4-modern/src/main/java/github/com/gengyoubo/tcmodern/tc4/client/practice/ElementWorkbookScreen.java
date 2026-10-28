package github.com.gengyoubo.tcmodern.tc4.client.practice;

import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspect;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspects;
import github.com.gengyoubo.tcmodern.tc4.client.AspectIcons;
import github.com.gengyoubo.tcmodern.tc4.practice.PracticeAspects;
import github.com.gengyoubo.tcmodern.tc4.practice.PracticePuzzle;
import github.com.gengyoubo.tcmodern.tc4.practice.PracticePuzzle.Hex;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Free, client-local practice: no research unlocks, resource use or server mutations. */
public final class ElementWorkbookScreen extends Screen {
    private static final int WIDTH = 360, HEIGHT = 280, COLUMNS = 5, ROWS = 6, PAGE_SIZE = COLUMNS * ROWS;
    private final PracticeAspects aspects;
    private final Random random = new Random();
    private final List<Button> levels = new ArrayList<>();
    private PracticePuzzle puzzle;
    private List<TcAspect> palette = List.of();
    private String selected;
    private int level = 1, page;
    private float scale, left, top;
    private Button previous, next, reset, hint;
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

    @Override protected void init() {
        scale = Math.min(1.5F, Math.min((width - 8F) / WIDTH, (height - 8F) / HEIGHT));
        left = (width - WIDTH * scale) / 2;
        top = (height - HEIGHT * scale) / 2;
        levels.clear();
        for (int i = 1; i <= 7; i++) {
            int requested = i;
            levels.add(addRenderableWidget(Button.builder(Component.translatable("workbook.tc4modern.level", i),
                    button -> generate(requested)).bounds(14 + (i - 1) * 30, 32, 28, 18).build()));
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(288, 12, 58, 18).build());
        previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> changePage(-1))
                .bounds(14, 226, 24, 18).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), button -> changePage(1))
                .bounds(99, 226, 24, 18).build());
        reset = addRenderableWidget(Button.builder(Component.translatable("workbook.tc4modern.reset"), button -> {
            puzzle.reset(); lastHint = null; updateButtons();
        }).bounds(170, 226, 54, 18).build());
        hint = addRenderableWidget(Button.builder(Component.translatable("workbook.tc4modern.hint"), button -> {
            lastHint = puzzle.hint().orElse(null); updateButtons();
        }).bounds(230, 226, 54, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("workbook.tc4modern.new"), button -> generate(level))
                .bounds(290, 226, 54, 18).build());
        updateButtons();
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

    private static int centerX(Hex h) { return 255 + (int) Math.round(Math.sqrt(3) * 12 * (h.q() + h.r() / 2.0)); }
    private static int centerY(Hex h) { return 151 + 18 * h.r(); }
    private double localX(double x) { return (x - left) / scale; }
    private double localY(double y) { return (y - top) / scale; }

    private Hex cellAt(double x, double y) {
        if (puzzle == null) return null;
        for (var cell : puzzle.cells()) {
            double dx = Math.abs(x - centerX(cell)), dy = Math.abs(y - centerY(cell));
            if (dx <= 10 && dy <= 12 - dx / Math.sqrt(3)) return cell;
        }
        return null;
    }

    private TcAspect paletteAt(double x, double y) {
        int column = (int) Math.floor((x - 14) / 22), row = (int) Math.floor((y - 90) / 22);
        if (column < 0 || column >= COLUMNS || row < 0 || row >= ROWS || x >= 124 || y >= 222) return null;
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
        if (localX(x) >= 14 && localX(x) < 124 && localY(y) >= 90 && localY(y) < 222) {
            changePage(delta > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(localX(x), localY(y), delta);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int x = (int) localX(mouseX), y = (int) localY(mouseY);
        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.fill(0, 0, WIDTH, HEIGHT, 0xFF302937);
        graphics.fill(3, 3, WIDTH - 3, HEIGHT - 3, 0xFFF0E4C7);
        graphics.drawString(font, title, 14, 13, 0x493852, false);
        graphics.drawString(font, Component.translatable("workbook.tc4modern.rules"), 14, 59, 0x554838, false);
        graphics.drawString(font, Component.translatable("workbook.tc4modern.controls"), 14, 71, 0x77634B, false);
        graphics.fill(10, 85, 128, 222, 0xFFDAC9A8);
        graphics.fill(136, 85, 348, 222, 0xFFE5D5B5);
        graphics.drawCenteredString(font, Component.translatable("workbook.tc4modern.page", page + 1,
                Math.max(1, (palette.size() + PAGE_SIZE - 1) / PAGE_SIZE)), 68, 231, 0x554838);
        for (int i = page * PAGE_SIZE; i < Math.min(palette.size(), (page + 1) * PAGE_SIZE); i++) {
            var aspect = palette.get(i);
            int px = 14 + (i % PAGE_SIZE % COLUMNS) * 22, py = 90 + (i % PAGE_SIZE / COLUMNS) * 22;
            if (aspect.tag().equals(selected)) graphics.fill(px, py, px + 21, py + 21, 0xFF9C814B);
            AspectIcons.render(graphics, aspect, px + 2, py + 2);
        }
        if (puzzle != null) renderBoard(graphics, x, y);
        else graphics.drawWordWrap(font, Component.translatable("workbook.tc4modern.unavailable"), 155, 132, 176, 0x884444);
        if (puzzle != null) {
            long count = puzzle.anchors().keySet().stream().filter(puzzle.connected()::contains).count();
            graphics.drawCenteredString(font, puzzle.solved() ? Component.translatable("workbook.tc4modern.solved")
                    : Component.translatable("workbook.tc4modern.progress", count, puzzle.anchors().size()),
                    242, 251, puzzle.solved() ? 0x337342 : 0x554838);
        }
        if (selected != null) graphics.drawString(font, font.plainSubstrByWidth(AspectIcons.name(selected), 110), 14, 251, 0x554838, false);
        super.render(graphics, x, y, partialTick);
        var hovered = paletteAt(x, y);
        var cell = cellAt(x, y);
        if (hovered == null && cell != null) hovered = aspects.aspect(puzzle.placed().get(cell));
        if (hovered != null) renderAspectTooltip(graphics, hovered, x, y, cell != null && puzzle.anchors().containsKey(cell));
        graphics.pose().popPose();
    }

    private void renderBoard(GuiGraphics graphics, int mouseX, int mouseY) {
        var hovered = cellAt(mouseX, mouseY);
        var connected = puzzle.connected();
        for (var cell : puzzle.cells()) {
            int color = puzzle.anchors().containsKey(cell) ? 0xFFAE8642 : 0xFFBAA887;
            if (cell.equals(hovered)) color = 0xFF80718D;
            if (cell.equals(lastHint)) color = 0xFF4F9E9B;
            hex(graphics, centerX(cell), centerY(cell), 12, color);
            hex(graphics, centerX(cell), centerY(cell), 10, 0xFFF5EACC);
        }
        for (var cell : puzzle.cells()) for (var neighbor : cell.neighbors()) {
            if (cell.q() > neighbor.q() || cell.q() == neighbor.q() && cell.r() >= neighbor.r()) continue;
            if (aspects.connects(puzzle.placed().get(cell), puzzle.placed().get(neighbor)))
                line(graphics, centerX(cell), centerY(cell), centerX(neighbor), centerY(neighbor),
                        connected.contains(cell) ? 0xFF64AC82 : 0xFFB8AAA3);
        }
        for (var entry : puzzle.placed().entrySet())
            AspectIcons.render(graphics, aspects.aspect(entry.getValue()), centerX(entry.getKey()) - 8, centerY(entry.getKey()) - 8);
    }

    private static void hex(GuiGraphics graphics, int x, int y, int size, int color) {
        for (int dy = -size; dy <= size; dy++) {
            int half = (int) Math.floor(Math.min(size * Math.sqrt(3) / 2, (size - Math.abs(dy)) * Math.sqrt(3)));
            graphics.fill(x - half, y + dy, x + half + 1, y + dy + 1, color);
        }
    }

    private static void line(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / steps, y = y1 + (y2 - y1) * i / steps;
            graphics.fill(x, y, x + 2, y + 2, color);
        }
    }

    private void renderAspectTooltip(GuiGraphics graphics, TcAspect aspect, int x, int y, boolean fixed) {
        var lines = new ArrayList<Component>();
        lines.add(Component.literal(AspectIcons.name(aspect.tag())));
        lines.add(Component.translatable("workbook.tc4modern.tier", aspects.tier(aspect.tag())));
        if (!aspect.primal()) lines.add(Component.literal(String.join(" + ", aspect.components().stream().map(AspectIcons::name).toList())));
        if (fixed) lines.add(Component.translatable("workbook.tc4modern.fixed"));
        // Keep tooltip layout inside the logical canvas even when the GUI is scaled down.
        int tooltipWidth = lines.stream().mapToInt(font::width).max().orElse(0);
        int tx = Math.max(6, Math.min(x + 10, WIDTH - tooltipWidth - 10));
        int ty = Math.max(6, Math.min(y + 12, HEIGHT - lines.size() * 11 - 8));
        graphics.fill(tx - 4, ty - 4, tx + tooltipWidth + 4, ty + lines.size() * 11 + 2, 0xF020182A);
        for (int i = 0; i < lines.size(); i++) graphics.drawString(font, lines.get(i), tx, ty + i * 11, 0xFFF0E4C7, false);
    }

    @Override public boolean isPauseScreen() { return false; }
}
