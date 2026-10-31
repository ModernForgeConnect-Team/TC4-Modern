package github.com.gengyoubo.tcmodern.tc4.client.deconstruction;

import com.mojang.blaze3d.systems.RenderSystem;
import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.mfcl.api.mla.*;
import github.com.gengyoubo.tcmodern.api.aspect.*;
import github.com.gengyoubo.tcmodern.tc4.client.research.ResearchTableTooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Native modern view of the original deconstruction table. Breaking, the aspect roll and its reward stay original. */
public final class DeconstructionTableGui implements LegacyClientScreens.Controller {
    private static final ResourceLocation TABLE=ResourceLocation.fromNamespaceAndPath("thaumcraft","textures/gui/gui_decontable.png");
    @LegacyType("thaumcraft.client.gui.GuiDeconstructionTable") public interface Gui {
        @LegacyField(name="tableInventory",descriptor="Lthaumcraft/common/tiles/TileDeconstructionTable;") LegacyObject tile();
        /** The original "thaumcraft:hhoff" pickup sound. */
        @LegacyMethod(name="playButtonAspect",descriptor="()V") void sound();
    }
    @LegacyType("thaumcraft.common.tiles.TileDeconstructionTable") public interface Table {
        @LegacyField(name="aspect",descriptor="Lthaumcraft/api/aspects/Aspect;") LegacyObject aspect();
        @LegacyField(name="breaktime",descriptor="I") int breaktime();
    }
    @LegacyType("thaumcraft.api.aspects.Aspect") public interface Text {
        @LegacyMethod(name="getName",descriptor="()Ljava/lang/String;") String name();
        @LegacyMethod(name="getLocalizedDescription",descriptor="()Ljava/lang/String;") String description();
    }
    /** {@code aspect} null while nothing waits to be taken; {@code lines} its original tooltip. */
    private record State(String aspect,List<String> lines,int breaktime) {}
    private static final State EMPTY=new State(null,List.of(),0);
    private final LegacyClientScreens.Context context;
    private final AbstractContainerScreen<?> host;
    private final Map<String,TcAspect> aspects=new HashMap<>();
    private State state=EMPTY;
    private boolean closed,failed;
    public static AutoCloseable install(LegacyRuntime runtime){return runtime.clientScreens().register("thaumcraft.client.gui.GuiDeconstructionTable",DeconstructionTableGui::new);}
    DeconstructionTableGui(LegacyClientScreens.Context context){
        this.context=context;host=context.hostScreen(AbstractContainerScreen.class);
        for(var aspect:TcAspects.snapshot(context.runtime()))aspects.put(aspect.tag(),aspect);
    }
    @Override public void tick(){
        if(closed)return;
        try{
            var runtime=context.runtime();
            state=context.withScreen(reference->{
                var tile=runtime.accessor(Gui.class,reference).tile();if(tile==null)return EMPTY;
                var table=runtime.accessor(Table.class,tile);var aspect=table.aspect();
                if(aspect==null)return new State(null,List.of(),table.breaktime());
                var text=runtime.accessor(Text.class,aspect);
                return new State(runtime.accessor(TcAspectAccessors.Aspect.class,aspect).tag(),List.of(text.name(),text.description()),table.breaktime());
            });
        }catch(RuntimeException failure){
            // Slots stay usable; only the progress and aspect display are lost.
            if(!failed){failed=true;com.mojang.logging.LogUtils.getLogger().error("TC4 native deconstruction table cannot read the original state",failure);}
            state=EMPTY;
        }
    }
    @Override public void renderBackground(Object target,int left,int top,int mouseX,int mouseY,float partial){
        var graphics=(GuiGraphics)target;
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();graphics.setColor(1,1,1,1);
        graphics.blit(TABLE,left,top,0,0,176,166);
        if(state.breaktime()>0){int height=state.breaktime()*46/40;graphics.blit(TABLE,left+93,top+15+46-height,176,46-height,9,height);}
        var aspect=state.aspect()==null?null:aspects.get(state.aspect());
        var texture=aspect==null?null:ResourceLocation.tryParse(aspect.texture());
        if(texture!=null){
            int color=aspect.color();
            graphics.setColor((color>>16&255)/255F,(color>>8&255)/255F,(color&255)/255F,1);
            graphics.blit(texture,left+64,top+48,16,16,0,0,1,1,1,1);
            graphics.setColor(1,1,1,1);
        }
        RenderSystem.disableBlend();
    }
    @Override public void renderForeground(Object target,int mouseX,int mouseY,float partial){
        if(state.aspect()!=null&&overAspect(mouseX,mouseY))ResearchTableTooltip.draw((GuiGraphics)target,state.lines(),mouseX,mouseY-8);
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        // Any mouse button, as in the original; the container's button 1 hands the aspect to the player's pool.
        if(state.aspect()!=null&&overAspect(x,y)){
            context.clickMenuButton(1);
            context.withScreen(reference->{context.runtime().accessor(Gui.class,reference).sound();return null;});
        }
        return false;
    }
    private boolean overAspect(double x,double y){
        double dx=x-(host.getGuiLeft()+64),dy=y-(host.getGuiTop()+48);return dx>=0&&dy>=0&&dx<16&&dy<16;
    }
    @Override public void close(){closed=true;}
}
