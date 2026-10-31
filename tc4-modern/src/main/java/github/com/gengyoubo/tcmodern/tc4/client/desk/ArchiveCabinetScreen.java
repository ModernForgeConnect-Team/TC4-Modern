package github.com.gengyoubo.tcmodern.tc4.client.desk;

import github.com.gengyoubo.tcmodern.tc4.menu.ArchiveCabinetMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import static github.com.gengyoubo.tcmodern.lib.client.ModernUi.*;

/** Three drawers in the same glass style as the research desk. */
public final class ArchiveCabinetScreen extends AbstractContainerScreen<ArchiveCabinetMenu> {
    private static final int DRAWERS = 3, DRAWER_STEP = 26;

    public ArchiveCabinetScreen(ArchiveCabinetMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = ArchiveCabinetMenu.WIDTH;
        imageHeight = ArchiveCabinetMenu.HEIGHT;
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) { }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        window(g, x, y, imageWidth, imageHeight);
        hex(g, x + 15, y + 12, 5.5F, ACCENT_LIGHT, ACCENT_DEEP);
        hex(g, x + 15, y + 12, 2, 0xFF1A1630);
        text(g, font, title, x + 26, y + 8, TEXT);
        var hint = Component.translatable("cabinet.tc4modern.notes_only");
        text(g, font, hint, x + imageWidth - font.width(hint) - 9, y + 8, FAINT);
        int used = 0;
        for (int i = 0; i < 27; i++) if (menu.getSlot(i).hasItem()) used++;
        for (int drawer = 0; drawer < DRAWERS; drawer++) {
            int dy = y + ArchiveCabinetMenu.DRAWER_Y + drawer * DRAWER_STEP;
            card(g, x + 6, dy - 4, imageWidth - 12, 24);
            // A brass pull centred under each drawer row.
            round(g, x + imageWidth / 2F - 7, dy + 17.5F, 14, 2, 1, alpha(GOLD, 0.8F));
        }
        for (var slot : menu.slots) slot(g, x + slot.x, y + slot.y);
        label(g, font, playerInventoryTitle, x + ArchiveCabinetMenu.INVENTORY_X, y + ArchiveCabinetMenu.INVENTORY_Y - 11);
        String count = used + "/27";
        text(g, font, count, x + imageWidth - font.width(count) - 12, y + ArchiveCabinetMenu.INVENTORY_Y - 11, FAINT);
    }
}
