package github.com.gengyoubo.tcmodern.tc4.client.entity;

import github.com.gengyoubo.tcmodern.tc4.menu.PechMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** The original TC4 pech trade texture and its dice button. */
public final class PechScreen extends AbstractContainerScreen<PechMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_pech.png");
    public PechScreen(PechMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = 175; imageHeight = 232;
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        if (menu.canTrade()) graphics.blit(TEXTURE, leftPos + 67, topPos + 24, 176, 0, 25, 25);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) { }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && x >= leftPos + 67 && x < leftPos + 92 && y >= topPos + 24 && y < topPos + 49 && menu.canTrade()) {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        github.com.gengyoubo.tcmodern.tc4.entity.Tc4Legacy.sound("pech_dice"), 0.95F, 0.5F));
            }
            return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public void render(GuiGraphics graphics, int x, int y, float partialTick) {
        renderBackground(graphics); super.render(graphics, x, y, partialTick); renderTooltip(graphics, x, y);
    }
}
