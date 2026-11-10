package github.com.gengyoubo.mfcl.compat.thaumcraft;

import com.mojang.math.Axis;
import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.common.MinecraftForge;

/** Draws the original TC4 dial from the held wand's own vis and focus data. */
final class ThaumcraftWandHud {
    private record Bar(int amount,int color) {}
    private record Dial(int maximum,List<Bar> bars,LegacyClientData.Stack focus,boolean bottom) {}
    private static final AtomicBoolean REPORTED=new AtomicBoolean();
    static void install(LegacyServices services){MinecraftForge.EVENT_BUS.addListener((RenderGuiEvent.Post event)->render(services,event));}
    private static void render(LegacyServices services,RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();var content=ModernContentBridge.active();
        if(mc.player==null||mc.level==null||mc.options.hideGui||content==null||mc.screen!=null)return;
        try{
            var snapshot=content.renderSnapshot(mc.player.getMainHandItem());
            if(snapshot==null||!snapshot.key().equals("Thaumcraft:WandCasting"))return;
            Dial dial=services.items().inLegacy(()->{
                var loader=services.loader();Object stack=services.items().materialize(content.gameplayStack(mc.player.getMainHandItem()));
                Object wand=LegacyObjects.item(stack);var stackType=loader.loadClass("net.minecraft.item.ItemStack");
                Object aspects=wand.getClass().getMethod("getAllVis",stackType).invoke(wand,stack);
                var aspectType=loader.loadClass("thaumcraft.api.aspects.Aspect");var bars=new ArrayList<Bar>();
                for(Object aspect:(Object[])aspects.getClass().getMethod("getAspects").invoke(aspects))if(aspect!=null)
                    bars.add(new Bar((int)aspects.getClass().getMethod("getAmount",aspectType).invoke(aspects,aspect),(int)aspectType.getMethod("getColor").invoke(aspect)));
                Object focus=wand.getClass().getMethod("getFocusItem",stackType).invoke(wand,stack);
                return new Dial((int)wand.getClass().getMethod("getMaxVis",stackType).invoke(wand,stack),bars,focus==null?null:services.items().snapshot(focus),loader.loadClass("thaumcraft.common.config.Config").getField("dialBottom").getBoolean(null));
            });
            var g=event.getGuiGraphics();var pose=g.pose();var texture=ResourceLocation.parse(content.pack().worldResource("thaumcraft:textures/gui/hud.png"));
            int cy=dial.bottom()?mc.getWindow().getGuiScaledHeight()-16:16;
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            pose.pushPose();pose.translate(16,cy,0);
            for(int i=0;i<dial.bars().size();i++){
                var bar=dial.bars().get(i);pose.pushPose();pose.mulPose(Axis.ZP.rotationDegrees((dial.bottom()?0:90)-15+i*24));pose.translate(0,-32,0);pose.scale(.5f,.5f,1);
                int fill=Math.max(0,Math.min(30,dial.maximum()<=0?0:30*bar.amount()/dial.maximum()));
                int color=bar.color();g.setColor((color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,.8f);
                if(fill>0)g.blit(texture,-4,35-fill,104,0,8,fill,256,256);
                g.setColor(1,1,1,1);g.blit(texture,-8,-3,72,0,16,42,256,256);pose.popPose();
            }
            g.blit(texture,-16,-16,0,0,32,32,32,32,256,256);
            if(dial.focus()!=null)g.renderItem(content.gameplayStack(dial.focus()),-8,-8);
            pose.popPose();
        }catch(Exception|LinkageError failure){ThaumcraftReflection.reportOnce(REPORTED,"MFCL TC4 wand HUD unavailable",failure);}
    }
}
