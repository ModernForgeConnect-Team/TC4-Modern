package github.com.gengyoubo.tcmodern.tc4.client.desk;

import github.com.gengyoubo.tcmodern.tc4.menu.WritingToolMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import static github.com.gengyoubo.tcmodern.lib.client.ModernUi.*;

/** A leather pen case: the refill slot feeds the tool on its cradle, whose capacity shows on the gauge and the note slip. */
public final class WritingToolScreen extends AbstractContainerScreen<WritingToolMenu> {
    // Drawn by tools/generate_writing_textures.py: background at (0,0), gauge fills (88x7) at v=208 and v=216, empty slot marks (16x16) at v=208.
    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath("tc4modern", "textures/gui/writing_tool.png");
    private static final int GAUGE_X = 80, GAUGE_Y = 42, GAUGE_WIDTH = 88, GAUGE_HEIGHT = 7, INK_V = 208, GRAPHITE_V = 216;
    private static final int LEAD_MARK_U = 92, REFILL_MARK_U = 110, MARK_V = 208, TOOL_X = 57, TOOL_Y = 37;
    private static final int NOTE_X = 20, NOTE_WIDTH = 144, TITLE = 0xFFF2E6C4, READOUT = 0xFF3A2A1A, HINT = 0xFF665238;

    public WritingToolScreen(WritingToolMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = WritingToolMenu.WIDTH; imageHeight = WritingToolMenu.HEIGHT;
    }
    @Override public void render(GuiGraphics g, int x, int y, float partial) {
        renderBackground(g); super.render(g, x, y, partial); renderTooltip(g, x, y);
    }
    @Override protected void renderLabels(GuiGraphics g, int x, int y) { }
    @Override protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.blit(BACKGROUND, x, y, 0, 0, imageWidth, imageHeight);
        text(g, font, title, x + 12, y + 9, TITLE);
        if (!menu.getSlot(0).hasItem())
            g.blit(BACKGROUND, x + WritingToolMenu.REFILL_X, y + WritingToolMenu.REFILL_Y, menu.usesLead() ? LEAD_MARK_U : REFILL_MARK_U, MARK_V, 16, 16);
        g.renderItem(menu.tool(), x + TOOL_X, y + TOOL_Y);
        int capacity = menu.capacity(), remaining = menu.remaining();
        int fill = capacity <= 0 ? 0 : Math.min(GAUGE_WIDTH, remaining * GAUGE_WIDTH / capacity);
        if (remaining > 0) fill = Math.max(1, fill);
        if (fill > 0) g.blit(BACKGROUND, x + GAUGE_X, y + GAUGE_Y, 0, menu.usesLead() ? GRAPHITE_V : INK_V, fill, GAUGE_HEIGHT);
        text(g, font, Component.translatable("writing.tc4modern.remaining", remaining, capacity), x + NOTE_X, y + 65, READOUT);
        g.drawWordWrap(font, Component.translatable("writing.tc4modern.hint"), x + NOTE_X, y + 76, NOTE_WIDTH, HINT);
        text(g, font, playerInventoryTitle, x + WritingToolMenu.INVENTORY_X, y + WritingToolMenu.INVENTORY_Y - 11, 0xFF404040);
    }
}
