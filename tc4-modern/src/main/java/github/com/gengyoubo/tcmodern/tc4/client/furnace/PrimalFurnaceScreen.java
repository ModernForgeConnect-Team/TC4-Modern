package github.com.gengyoubo.tcmodern.tc4.client.furnace;

import com.mojang.blaze3d.systems.RenderSystem;
import github.com.gengyoubo.tcmodern.tc4.menu.PrimalFurnaceMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import static github.com.gengyoubo.tcmodern.lib.client.ModernUi.*;

/** Material and fuel slots, processing indicators, and the primal essentia buffer. */
public final class PrimalFurnaceScreen extends AbstractContainerScreen<PrimalFurnaceMenu> {
    // Drawn by tools/generate_machine_textures.py: background at (0,0), progress fill (104x7) and lit flame (14x14) at v=220.
    private static final ResourceLocation BACKGROUND=ResourceLocation.fromNamespaceAndPath("tc4modern","textures/gui/primal_furnace.png");
    private static final int RESULT_X=63,RESULT_Y=65,RESULT_STEP=18;
    private static final int SPRITE_V=220,FLAME_U=110,FLAME_SIZE=14,PROGRESS_WIDTH=104,TITLE=0xFFF2E6C4,LABEL=0xFFC9BC98;
    public PrimalFurnaceScreen(PrimalFurnaceMenu menu,Inventory inventory,Component title) {
        super(menu,inventory,title); imageWidth=PrimalFurnaceMenu.WIDTH; imageHeight=PrimalFurnaceMenu.HEIGHT;
    }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        renderBackground(graphics); super.render(graphics,mouseX,mouseY,partialTick); renderTooltip(graphics,mouseX,mouseY);
        var results=menu.results();
        for(int i=0;i<results.size();i++) {
            int x=leftPos+RESULT_X+(i%6)*RESULT_STEP,y=topPos+RESULT_Y+(i/6)*RESULT_STEP;
            if(mouseX>=x && mouseX<x+16 && mouseY>=y && mouseY<y+16) {
                var result=results.get(i);
                graphics.renderTooltip(font,Component.translatable("primal_furnace.tc4modern.aspect_tooltip",
                        Component.translatable("tc.aspect."+result.aspect().tag()),result.amount()),mouseX,mouseY);
            }
        }
    }
    @Override protected void renderLabels(GuiGraphics graphics,int mouseX,int mouseY) { }
    @Override protected void renderBg(GuiGraphics g,float partialTick,int mouseX,int mouseY) {
        int x=leftPos,y=topPos; g.blit(BACKGROUND,x,y,0,0,imageWidth,imageHeight); text(g,font,title,x+12,y+9,TITLE);
        text(g,font,Component.translatable("primal_furnace.tc4modern.output"),x+RESULT_X,y+52,LABEL);
        int flame=menu.burnFraction()*FLAME_SIZE/1000;
        if(flame>0) g.blit(BACKGROUND,x+21,y+52+FLAME_SIZE-flame,FLAME_U,SPRITE_V+FLAME_SIZE-flame,FLAME_SIZE,flame);
        int progress=menu.cookFraction()*PROGRESS_WIDTH/1000;
        if(progress>0) g.blit(BACKGROUND,x+64,y+36,0,SPRITE_V,progress,7);
        int i=0;
        for(var result:menu.results()) {
            int px=x+RESULT_X+(i%6)*RESULT_STEP,py=y+RESULT_Y+(i/6)*RESULT_STEP; i++;
            var aspect=result.aspect(); var texture=ResourceLocation.tryParse(aspect.texture());
            if(texture!=null) {
                int color=aspect.color(); RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
                g.setColor((color>>16&255)/255F,(color>>8&255)/255F,(color&255)/255F,1);
                g.blit(texture,px,py,16,16,0,0,1,1,1,1); g.setColor(1,1,1,1);
            }
            if(result.amount()>1) {
                String amount=Integer.toString(result.amount());
                g.drawString(font,amount,px+16-font.width(amount),py+9,0xFFFFFF,true);
            }
        }
        text(g,font,playerInventoryTitle,x+PrimalFurnaceMenu.INVENTORY_X,y+PrimalFurnaceMenu.INVENTORY_Y-11,0xFF404040);
    }
}
