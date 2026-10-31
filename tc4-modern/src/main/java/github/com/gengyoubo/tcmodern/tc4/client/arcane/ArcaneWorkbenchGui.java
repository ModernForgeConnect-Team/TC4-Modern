package github.com.gengyoubo.tcmodern.tc4.client.arcane;

import com.mojang.blaze3d.systems.RenderSystem;
import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.tcmodern.api.aspect.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.AbstractContainerMenu;
import java.text.DecimalFormat;
import java.util.*;

/** Native modern view of the original arcane worktable. Slots, crafting and vis consumption stay original. */
public final class ArcaneWorkbenchGui implements LegacyClientScreens.Controller {
    private static final ResourceLocation TABLE=ResourceLocation.fromNamespaceAndPath("thaumcraft","textures/gui/gui_arcaneworkbench.png");
    /** Original ring of primal costs around the grid, in primal order. */
    private static final int[][] ASPECTS={{72,21},{24,43},{24,102},{72,124},{120,102},{120,43}};
    private static final String INSUFFICIENT="Insufficient vis";
    /** Menus opened as a worktable. The view itself closes while JEI's recipe screen covers it; the menu stays. */
    private static final Set<AbstractContainerMenu> MENUS=Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<AbstractContainerScreen<?>,ArcaneWorkbenchGui> OPEN=new IdentityHashMap<>();
    private final LegacyClientScreens.Context context;
    private final Map<String,TcAspect> aspects=new HashMap<>();
    private final DecimalFormat format=new DecimalFormat("#######.##");
    private ArcaneWorkbenchState state=ArcaneWorkbenchState.EMPTY;
    private final AbstractContainerScreen<?> host;
    private boolean closed,failed;
    public static AutoCloseable install(LegacyRuntime runtime){return runtime.clientScreens().register("thaumcraft.client.gui.GuiArcaneWorkbench",ArcaneWorkbenchGui::new);}
    ArcaneWorkbenchGui(LegacyClientScreens.Context context){
        this.context=context;host=context.hostScreen(AbstractContainerScreen.class);
        for(var aspect:TcAspects.snapshot(context.runtime()))aspects.put(aspect.tag(),aspect);
        MENUS.add(host.getMenu());OPEN.put(host,this);
    }
    static ArcaneWorkbenchGui active(net.minecraft.client.gui.screens.Screen screen){return OPEN.get(screen);}
    ArcaneWorkbenchState state(){return state;}
    /** True while {@code menu} is the open original worktable menu. */
    public static boolean isWorkbench(AbstractContainerMenu menu){
        var player=Minecraft.getInstance().player;
        return menu!=null&&MENUS.contains(menu)&&player!=null&&player.containerMenu==menu;
    }
    @Override public void tick(){
        if(closed)return;
        try{var next=ArcaneWorkbenchState.read(context);if(next!=null)state=next;}
        catch(RuntimeException failure){
            // Slots and crafting stay usable; only the cost overlay is lost.
            if(!failed){failed=true;com.mojang.logging.LogUtils.getLogger().error("TC4 native arcane worktable cannot read the original state",failure);}
            state=ArcaneWorkbenchState.EMPTY;
        }
    }
    @Override public void renderBackground(Object target,int left,int top,int mouseX,int mouseY,float partial){
        var graphics=(GuiGraphics)target;var mc=Minecraft.getInstance();
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();graphics.setColor(1,1,1,1);
        graphics.blit(TABLE,left,top,0,0,190,234);
        int ticks=mc.player==null?0:mc.player.tickCount;
        for(var cost:state.costs()){
            int x=left+ASPECTS[cost.position()][0]-8,y=top+ASPECTS[cost.position()][1]-8;
            float alpha=cost.ready()?1:.5F+(Mth.sin((ticks+cost.position()*10)/2.0F)*.2F-.2F);
            tag(graphics,cost.tag(),x,y,cost.amount(),alpha);
        }
        RenderSystem.disableBlend();
        if(state.insufficient()){
            var ghost=state.ghost();
            if(!ghost.isEmpty()){
                // The original turns item colouring off and draws the output dimmed under a 0.33 grey, 0.66 alpha colour.
                RenderSystem.enableBlend();graphics.setColor(.33F,.33F,.33F,.66F);
                graphics.renderItem(ghost,left+160,top+64);
                graphics.setColor(1,1,1,1);RenderSystem.disableBlend();
                graphics.renderItemDecorations(mc.font,ghost,left+160,top+64);
            }
            graphics.pose().pushPose();graphics.pose().translate(left+168,top+46,0);graphics.pose().scale(.5F,.5F,1);
            graphics.drawString(mc.font,INSUFFICIENT,-mc.font.width(INSUFFICIENT)/2,0,0xFFEE6E6E,false);
            graphics.pose().popPose();
        }
    }
    /** UtilsFX.drawTag: the tinted icon, then its amount at half size in the lower right with a black outline. */
    private void tag(GuiGraphics graphics,String tag,int x,int y,float amount,float alpha){
        var aspect=aspects.get(tag);if(aspect==null)return;
        int color=aspect.color();var texture=ResourceLocation.tryParse(aspect.texture());
        if(texture!=null){
            // Text batches end with blending off, so every icon re-enables it.
            RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
            graphics.setColor((color>>16&255)/255F,(color>>8&255)/255F,(color&255)/255F,alpha);
            graphics.blit(texture,x,y,16,16,0,0,1,1,1,1);
            graphics.setColor(1,1,1,1);
        }
        if(amount<=0)return;
        var font=Minecraft.getInstance().font;String label=format.format(amount);
        int tx=32-font.width(label)+x*2,ty=32-font.lineHeight+y*2;
        graphics.pose().pushPose();graphics.pose().scale(.5F,.5F,1);
        for(int[] offset:new int[][]{{-1,0},{1,0},{0,-1},{0,1}})graphics.drawString(font,label,tx+offset[0],ty+offset[1],0xFF000000,false);
        graphics.drawString(font,label,tx,ty,0xFFFFFFFF,false);
        graphics.pose().popPose();
    }
    @Override public void close(){if(!closed){closed=true;OPEN.remove(host,this);}}
}
