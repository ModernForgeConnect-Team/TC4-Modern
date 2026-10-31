package github.com.gengyoubo.tcmodern.lib.client.book;

import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.tcmodern.api.recipe.TcRecipe;
import github.com.gengyoubo.tcmodern.api.research.TcResearchEntry;
import github.com.gengyoubo.tcmodern.api.research.TcResearchPage;
import java.util.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import static github.com.gengyoubo.tcmodern.lib.client.book.BookChrome.*;

/**
 * One research laid out as pages of a two-page spread. Text keeps the original markup ({@code <BR>}, {@code <LINE>},
 * {@code <IMG>}) and flows onto further pages when long; every other original page kind takes a page of its own.
 */
final class BookReader {
    static final int PAPER = 0xFFF4EFE6, PAPER_EDGE = 0xFFE4DCCD, INK = 0xFF2C2A33, INK_SOFT = 0xFF6E6876, INK_FAINT = 0xFFA79F95;
    private static final int LINE_HEIGHT = 10, TITLE_HEIGHT = 36;

    /** A laid-out page: free-flowing blocks, or one original page drawn whole. */
    private record Side(List<Block> blocks, TcResearchPage page) {}
    private sealed interface Block permits Title, Line, Rule, Picture, Gap {}
    private record Title() implements Block {}
    private record Line(FormattedCharSequence text) implements Block {}
    private record Rule() implements Block {}
    private record Gap(int height) implements Block {}
    private record Picture(ResourceLocation image, int u, int v, int width, int height, float scale) implements Block {}

    private final ThaumonomiconScreen screen;
    private final TcResearchEntry entry;
    private final List<Side> sides = new ArrayList<>();
    private final int width, height;
    ItemStack hoveredItem = ItemStack.EMPTY;
    String hoveredAspect;

    BookReader(ThaumonomiconScreen screen, TcResearchEntry entry, boolean cheat, Set<String> completed, int width, int height) {
        this.screen = screen;
        this.entry = entry;
        this.width = width;
        this.height = height;
        // The title opens the first page; every original page starts a new page, and long text flows on.
        var current = new ArrayList<Block>(List.of(new Title()));
        int used = TITLE_HEIGHT;
        for (var page : entry.pages()) {
            if (page instanceof TcResearchPage.Text text) {
                if (!text.requires().isEmpty() && !cheat && !completed.contains(text.requires())) continue;
                if (!onlyTitle(current)) { sides.add(new Side(current, null)); current = new ArrayList<>(); used = 0; }
                for (var block : blocks(screen.translate(text.text()))) {
                    int blockHeight = height(block);
                    if (used + blockHeight > height && !current.isEmpty() && !onlyTitle(current)) {
                        sides.add(new Side(current, null));
                        current = new ArrayList<>();
                        used = 0;
                        if (block instanceof Gap) continue;
                    }
                    current.add(block);
                    used += blockHeight;
                }
            } else {
                if (!current.isEmpty()) sides.add(new Side(current, null));
                sides.add(new Side(List.of(), page));
                current = new ArrayList<>();
                used = 0;
            }
        }
        if (!current.isEmpty()) sides.add(new Side(current, null));
    }

    private static boolean onlyTitle(List<Block> blocks) { return blocks.size() == 1 && blocks.get(0) instanceof Title; }

    TcResearchEntry entry() { return entry; }
    int sides() { return sides.size(); }

    private int height(Block block) {
        if (block instanceof Title) return TITLE_HEIGHT;
        if (block instanceof Line) return LINE_HEIGHT;
        if (block instanceof Rule) return 9;
        if (block instanceof Gap gap) return gap.height();
        var picture = (Picture) block;
        return Math.round(picture.height() * picture.scale()) + 4;
    }

    /** Splits original research text into wrapped lines, rules and pictures. */
    private List<Block> blocks(String text) {
        var result = new ArrayList<Block>();
        String normalized = text.replace("<BR/>", "<BR>").replace("<LINE/>", "<LINE>");
        var paragraphs = normalized.split("<BR>", -1);
        Font font = screen.font();
        for (int p = 0; p < paragraphs.length; p++) {
            String paragraph = paragraphs[p];
            int index = 0;
            while (index <= paragraph.length()) {
                int line = paragraph.indexOf("<LINE>", index), image = paragraph.indexOf("<IMG>", index);
                int next = line < 0 ? image : image < 0 ? line : Math.min(line, image);
                String chunk = paragraph.substring(index, next < 0 ? paragraph.length() : next);
                if (!chunk.isBlank()) for (var wrapped : font.split(FormattedText.of(chunk.strip()), width)) result.add(new Line(wrapped));
                if (next < 0) break;
                if (next == line) { result.add(new Rule()); index = line + "<LINE>".length(); continue; }
                int end = paragraph.indexOf("</IMG>", image);
                if (end < 0) break;
                var picture = picture(paragraph.substring(image + "<IMG>".length(), end));
                if (picture != null) result.add(picture);
                index = end + "</IMG>".length();
            }
            if (p < paragraphs.length - 1) result.add(new Gap(4));
        }
        return result;
    }

    /** {@code namespace:path:u:v:width:height:scale}, the original markup; the texture is read as 256 wide. */
    private static Picture picture(String spec) {
        var parts = spec.split(":");
        if (parts.length != 7) return null;
        try {
            var image = ResourceLocation.tryParse(parts[0] + ":" + parts[1]);
            return image == null ? null : new Picture(image, Integer.parseInt(parts[2]), Integer.parseInt(parts[3]),
                    Integer.parseInt(parts[4]), Integer.parseInt(parts[5]), Float.parseFloat(parts[6]));
        } catch (NumberFormatException malformed) {
            return null;
        }
    }

    // ---- Drawing ----------------------------------------------------------------------------------------------

    /** Draws page {@code index} into a paper card at {@code x, y}; mouse position is on the same canvas. */
    void renderSide(GuiGraphics g, int index, int x, int y, int mouseX, int mouseY) {
        round(g, x - 10, y - 10, width + 20, height + 24, 6, PAPER, PAPER_EDGE);
        if (index < 0 || index >= sides.size()) return;
        var side = sides.get(index);
        Font font = screen.font();
        int cursor = y;
        for (var block : side.blocks()) {
            if (block instanceof Title) {
                renderTitle(g, x, cursor, mouseX, mouseY);
            } else if (block instanceof Line line) {
                g.drawString(font, line.text(), x, cursor, INK, false);
            } else if (block instanceof Rule) {
                g.fill(x + 10, cursor + 4, x + width - 10, cursor + 5, 0x40000000);
            } else if (block instanceof Picture picture) {
                int w = Math.round(picture.width() * picture.scale()), h = Math.round(picture.height() * picture.scale());
                g.blit(picture.image(), x + (width - w) / 2, cursor + 2, w, h, picture.u(), picture.v(), picture.width(), picture.height(), 256, 256);
            }
            cursor += height(block);
        }
        if (side.page() != null) renderPage(g, side.page(), x, cursor, width, y + height - cursor, mouseX, mouseY);
        String number = (index + 1) + " / " + sides.size();
        g.drawString(font, number, x + (width - font.width(number)) / 2, y + height + 4, INK_FAINT, false);
    }

    private void renderTitle(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        Font font = screen.font();
        screen.renderIcon(g, entry, x, y + 2, 1F);
        g.drawString(font, screen.clipText(screen.name(entry.key()), width - 22), x + 22, y + 2, INK, false);
        g.drawString(font, screen.clipText(screen.subtitle(entry.key()), width - 22), x + 22, y + 13, INK_SOFT, false);
        g.fill(x, y + 28, x + width, y + 29, 0x30000000);
    }

    private void renderPage(GuiGraphics g, TcResearchPage page, int x, int y, int w, int h, int mouseX, int mouseY) {
        Font font = screen.font();
        int cx = x + w / 2;
        long now = System.currentTimeMillis();
        if (page instanceof TcResearchPage.Recipes recipes) {
            var recipe = recipes.recipes().get((int) (now / 2000 % recipes.recipes().size()));
            renderRecipe(g, recipe, x, y, w, mouseX, mouseY);
        } else if (page instanceof TcResearchPage.Crafting crafting) {
            var grid = crafting.grids().get((int) (now / 2000 % crafting.grids().size()));
            heading(g, Component.translatable(grid.shapeless() ? "book.tcmodernlib.page.shapeless" : "book.tcmodernlib.page.crafting"), cx, y);
            slot(g, grid.output().count() > 0 ? List.of(grid.output()) : List.of(), cx - 9, y + 18, mouseX, mouseY, true);
            grid(g, grid.width(), grid.height(), grid.inputs(), cx, y + 46, mouseX, mouseY);
        } else if (page instanceof TcResearchPage.Smelting smelting) {
            heading(g, Component.translatable("book.tcmodernlib.page.smelting"), cx, y);
            slot(g, List.of(smelting.input()), cx - 40, y + 50, mouseX, mouseY, false);
            line(g, cx - 14, y + 59, cx + 14, y + 59, 1.5F, INK_SOFT);
            line(g, cx + 9, y + 55, cx + 14, y + 59, 1.5F, INK_SOFT);
            line(g, cx + 9, y + 63, cx + 14, y + 59, 1.5F, INK_SOFT);
            slot(g, List.of(smelting.output()), cx + 22, y + 50, mouseX, mouseY, true);
        } else if (page instanceof TcResearchPage.Aspects aspects) {
            heading(g, Component.translatable("book.tcmodernlib.page.aspects"), cx, y);
            int row = 0;
            for (var entry : aspects.aspects().entrySet()) {
                int ax = x + (row % 2) * (w / 2), ay = y + 18 + (row / 2) * 20;
                screen.renderAspect(g, entry.getKey(), ax, ay);
                if (mouseX >= ax && mouseX < ax + 16 && mouseY >= ay && mouseY < ay + 16) hoveredAspect = entry.getKey();
                g.drawString(font, screen.clipText(screen.aspectName(entry.getKey()), w / 2 - 22), ax + 19, ay + 4, INK, false);
                row++;
            }
        } else if (page instanceof TcResearchPage.Image image) {
            var location = ResourceLocation.tryParse(image.image());
            int size = Math.min(w, h - 24);
            if (location != null && size > 16) g.blit(location, cx - size / 2, y, size, size, 0F, 0F, 1, 1, 1, 1);
            int lineY = y + size + 4;
            for (var line : font.split(FormattedText.of(screen.translate(image.caption())), w)) {
                g.drawString(font, line, cx - font.width(line) / 2, lineY, INK_SOFT, false);
                lineY += LINE_HEIGHT;
            }
        } else if (page instanceof TcResearchPage.Unsupported unsupported) {
            var text = Component.translatable("book.tcmodernlib.page.unsupported", unsupported.type());
            int lineY = y + 40;
            for (var line : font.split(text, w)) { g.drawString(font, line, cx - font.width(line) / 2, lineY, INK_FAINT, false); lineY += LINE_HEIGHT; }
        }
    }

    private void renderRecipe(GuiGraphics g, TcRecipe recipe, int x, int y, int w, int mouseX, int mouseY) {
        int cx = x + w / 2;
        switch (recipe.kind()) {
            case ARCANE_SHAPED, ARCANE_SHAPELESS -> {
                heading(g, Component.translatable("book.tcmodernlib.page.arcane"), cx, y);
                slot(g, List.of(recipe.output()), cx - 9, y + 16, mouseX, mouseY, true);
                if (recipe.kind() == TcRecipe.Kind.ARCANE_SHAPED) grid(g, recipe.width(), recipe.height(), recipe.inputs(), cx, y + 42, mouseX, mouseY);
                else grid(g, recipe.inputs().size(), 1, recipe.inputs(), cx, y + 42, mouseX, mouseY);
                costs(g, recipe, cx, y + 116, mouseX, mouseY);
            }
            case CRUCIBLE -> {
                heading(g, Component.translatable("book.tcmodernlib.page.crucible"), cx, y);
                slot(g, List.of(recipe.output()), cx - 9, y + 16, mouseX, mouseY, true);
                circle(g, cx, y + 64, 18, 0x18000000);
                slot(g, recipe.inputs().get(0), cx - 9, y + 55, mouseX, mouseY, false);
                costs(g, recipe, cx, y + 94, mouseX, mouseY);
            }
            case INFUSION -> {
                heading(g, Component.translatable("book.tcmodernlib.page.infusion"), cx, y);
                slot(g, List.of(recipe.output()), cx - 9, y + 14, mouseX, mouseY, true);
                int centerY = y + 82, ring = Math.min(46, w / 2 - 14);
                var components = recipe.inputs().subList(1, recipe.inputs().size());
                for (int i = 0; i < components.size(); i++) {
                    double angle = -Math.PI / 2 + 2 * Math.PI * i / components.size();
                    int sx = cx + (int) Math.round(Math.cos(angle) * ring), sy = centerY + (int) Math.round(Math.sin(angle) * ring);
                    line(g, cx, centerY, sx, sy, 1, 0x20000000);
                    slot(g, components.get(i), sx - 9, sy - 9, mouseX, mouseY, false);
                }
                slot(g, recipe.inputs().get(0), cx - 9, centerY - 9, mouseX, mouseY, true);
                costs(g, recipe, cx, centerY + ring + 16, mouseX, mouseY);
                var instability = Component.translatable("book.tcmodernlib.page.instability", recipe.instability());
                g.drawString(screen.font(), instability, cx - screen.font().width(instability) / 2, centerY + ring + 44, INK_SOFT, false);
            }
        }
    }

    private void heading(GuiGraphics g, Component text, int cx, int y) {
        g.drawString(screen.font(), text, cx - screen.font().width(text) / 2, y + 2, INK_SOFT, false);
    }

    /** A crafting grid centred on {@code cx}; one-row grids wrap into rows of three. */
    private void grid(GuiGraphics g, int width, int height, List<List<LegacyStack>> inputs, int cx, int y, int mouseX, int mouseY) {
        int columns = height == 1 ? Math.min(3, width) : width, rows = height == 1 ? (width + 2) / 3 : height;
        int left = cx - columns * 22 / 2;
        for (int i = 0; i < inputs.size(); i++) {
            int column = height == 1 ? i % 3 : i % width, row = height == 1 ? i / 3 : i / width;
            if (row >= rows) break;
            slot(g, inputs.get(i), left + column * 22 + 2, y + row * 22, mouseX, mouseY, false);
        }
    }

    /** Aspect costs in rows of five, amounts under the icons. */
    private void costs(GuiGraphics g, TcRecipe recipe, int cx, int y, int mouseX, int mouseY) {
        var costs = recipe.aspects();
        int perRow = 5, size = 22;
        for (int i = 0; i < costs.size(); i++) {
            int inRow = Math.min(perRow, costs.size() - i / perRow * perRow);
            int ax = cx - inRow * size / 2 + (i % perRow) * size + 3, ay = y + (i / perRow) * 24;
            screen.renderAspect(g, costs.get(i).tag(), ax, ay);
            if (mouseX >= ax && mouseX < ax + 16 && mouseY >= ay && mouseY < ay + 16) hoveredAspect = costs.get(i).tag();
            String amount = String.valueOf(costs.get(i).amount());
            g.drawString(screen.font(), amount, ax + 8 - screen.font().width(amount) / 2, ay + 16, INK, false);
        }
    }

    /** An item well; alternatives cycle once a second. */
    private void slot(GuiGraphics g, List<LegacyStack> choices, int x, int y, int mouseX, int mouseY, boolean highlight) {
        round(g, x - 1, y - 1, 20, 20, 4, highlight ? 0x30B39DFF : 0x16000000);
        if (choices.isEmpty()) return;
        var stack = screen.item(choices.get((int) (System.currentTimeMillis() / 1000 % choices.size())));
        if (stack.isEmpty()) return;
        g.renderItem(stack, x + 1, y + 1);
        g.renderItemDecorations(screen.font(), stack, x + 1, y + 1);
        if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) hoveredItem = stack;
    }
}
