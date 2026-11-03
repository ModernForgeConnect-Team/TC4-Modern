package github.com.gengyoubo.tcmodern.tc4.client.deconstruction;

import github.com.gengyoubo.tcmodern.tc4.menu.ModernDeconstructionMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import static github.com.gengyoubo.tcmodern.lib.client.ModernUi.*;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.systems.RenderSystem;
import github.com.gengyoubo.tcmodern.tc4.content.ModernDeconstructionBlockEntity;

public final class ModernDeconstructionScreen extends AbstractContainerScreen<ModernDeconstructionMenu> {
    // Drawn by tools/generate_machine_textures.py; slot wells and result cells are baked in at the menu's positions.
    private static final ResourceLocation BACKGROUND=ResourceLocation.fromNamespaceAndPath("tc4modern","textures/gui/deconstruction.png");
    private static final int TITLE=0xFFF2E6C4;
    private Button claim;
    public ModernDeconstructionScreen(ModernDeconstructionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = ModernDeconstructionMenu.WIDTH; imageHeight = ModernDeconstructionMenu.HEIGHT;
    }
    @Override protected void init() {
        super.init();
        claim = addRenderableWidget(Button.builder(Component.translatable("deconstruction.tc4modern.claim"), b -> send(1))
                .bounds(leftPos+96, topPos+70, 72, 20).build());
    }
    private void send(int id) { if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id); }
    @Override public void containerTick() {
        super.containerTick();
        claim.active = menu.hasPending();
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g); super.render(g, mouseX, mouseY, partialTick); renderTooltip(g, mouseX, mouseY);
        int i=0;
        for (var result : menu.results()) {
            int x=leftPos+56+i++*19, y=topPos+37;
            if (mouseX>=x && mouseX<x+16 && mouseY>=y && mouseY<y+16)
                g.renderTooltip(font,Component.translatable("tc.aspect."+result.aspect().tag()),mouseX,mouseY);
        }
    }
    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) { }
    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x=leftPos, y=topPos;
        g.blit(BACKGROUND,x,y,0,0,imageWidth,imageHeight);
        text(g,font,title,x+12,y+9,TITLE);
        int width=Math.max(0,Math.min(70,menu.progress()*70/ModernDeconstructionBlockEntity.PROCESS_TICKS));
        g.fill(x+16,y+70,x+88,y+79,0xFF281E18);
        if (width>0) g.fill(x+17,y+71,x+17+width,y+78,0xFFD0A84B);
        String status=switch(menu.status()) {
            case ModernDeconstructionBlockEntity.WORKING -> "working";
            case ModernDeconstructionBlockEntity.INVALID -> "invalid";
            case ModernDeconstructionBlockEntity.FAILED -> "failed_short";
            case ModernDeconstructionBlockEntity.READY -> "ready";
            default -> "idle";
        };
        g.drawString(font,Component.translatable("deconstruction.tc4modern."+status),x+16,y+83,0xFF4A3826,false);
        int i=0;
        for (var result : menu.results()) {
            var aspect=result.aspect(); var texture=ResourceLocation.tryParse(aspect.texture());
            int px=x+56+i++*19, py=y+37;
            if (texture != null) {
                int color=aspect.color(); RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
                g.setColor((color>>16&255)/255F,(color>>8&255)/255F,(color&255)/255F,1);
                g.blit(texture,px,py,16,16,0,0,1,1,1,1); g.setColor(1,1,1,1);
            }
            if (result.amount()>1) g.drawString(font,Integer.toString(result.amount()),px+8,py+10,0xFFFFFF,true);
        }
        text(g,font,playerInventoryTitle,x+ModernDeconstructionMenu.INVENTORY_X,y+ModernDeconstructionMenu.INVENTORY_Y-11,0xFF404040);
    }
}
