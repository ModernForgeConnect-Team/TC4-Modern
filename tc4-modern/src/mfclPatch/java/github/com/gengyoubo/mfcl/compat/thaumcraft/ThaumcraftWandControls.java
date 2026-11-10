package github.com.gengyoubo.mfcl.compat.thaumcraft;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.client.LegacyKeyMappings;
import github.com.gengyoubo.mfcl.content.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.*;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** TC4's own "Change Wand Focus"/"Misc Wand Toggle" bindings (bridged host mappings); original server handlers own focus transfers and wand mode changes. */
final class ThaumcraftWandControls {
    private static final String FOCUS="Change Wand Focus",MODE="Misc Wand Toggle";
    private ThaumcraftWandControls() {}
    static void install(LegacyServices services){
        MinecraftForge.EVENT_BUS.addListener((InputEvent.Key event)->{
            var mc=Minecraft.getInstance();if(event.getAction()!=GLFW.GLFW_PRESS||mc.player==null||mc.screen!=null)return;
            // TC4 polls these from its client PlayerTickEvent, which the host does not run; the press itself is handled here.
            boolean focus=pressed(FOCUS,event),mode=!focus&&pressed(MODE,event);
            if(!focus&&!mode)return;
            try{
                var content=ModernContentBridge.active();var held=content.renderSnapshot(mc.player.getMainHandItem());
                if(held==null||!held.key().equals("Thaumcraft:WandCasting"))return;
                if(focus){
                    // F is also the modern offhand shortcut. Consume that queued click while a wand owns this key.
                    if(mc.options.keySwapOffhand.matches(event.getKey(),event.getScanCode()))while(mc.options.keySwapOffhand.consumeClick()){}
                    if(mc.player.isShiftKeyDown())send(services,"REMOVE");else open(services);
                }else send(services,null);
            }catch(Exception failure){LogUtils.getLogger().error("MFCL wand control failed",failure);}
        });
    }
    private static boolean pressed(String binding,InputEvent.Key event){
        var key=LegacyKeyMappings.find(binding);return key!=null&&key.matches(event.getKey(),event.getScanCode());
    }
    static void open(LegacyServices services) throws Exception {
        var mc=Minecraft.getInstance();var content=ModernContentBridge.active();
        if(!services.items().inLegacy(()->ThaumcraftWandFocus.supportsFocus(services.loader(),services.items().materialize(content.gameplayStack(mc.player.getMainHandItem()))))){
            mc.player.displayClientMessage(Component.translatable("mfcl.wand.focus.sceptre"),true);return;
        }
        var choices=services.items().inLegacy(()->{
            var result=new TreeMap<String,Object>();
            for(int i=0;i<36;i++){
                var host=mc.player.getInventory().getItem(i);if(host.isEmpty())continue;
                try{ThaumcraftWandFocus.add(services.loader(),services.items().materialize(content.gameplayStack(host)),result);}catch(UnsupportedOperationException unmapped){}
            }
            var names=new LinkedHashMap<String,LegacyClientData.Stack>();for(var choice:result.entrySet())names.put(choice.getKey(),services.items().snapshot(choice.getValue()));return names;
        });
        if(choices.isEmpty())return;
        var equipped=services.items().inLegacy(()->{
            Object stack=services.items().materialize(content.gameplayStack(mc.player.getMainHandItem()));
            Object item=LegacyObjects.item(stack);
            Object focus=item.getClass().getMethod("getFocusItem",services.loader().loadClass("net.minecraft.item.ItemStack")).invoke(item,stack);
            return focus==null?null:services.items().snapshot(focus);
        });
        mc.setScreen(new Selector(services,choices,equipped));
    }
    static void send(LegacyServices services,String focus) throws Exception {
        var mc=Minecraft.getInstance();var content=ModernContentBridge.active();
        var packet=services.items().inLegacy(()->{
            Object world=services.worlds().bound(mc.level);Object actor=content.itemUse().clientActor(world,new LegacyTiles.Placer(mc.player.getUUID(),mc.player.getName().getString(),mc.player.getX(),mc.player.getY(),mc.player.getZ(),mc.player.getYRot(),mc.player.getXRot()),null);
            var loader=services.loader();var player=loader.loadClass("net.minecraft.entity.player.EntityPlayer");
            Object request=focus==null?loader.loadClass("thaumcraft.common.lib.network.misc.PacketItemKeyToServer").getConstructor(player,int.class).newInstance(actor,1)
                    :loader.loadClass("thaumcraft.common.lib.network.misc.PacketFocusChangeToServer").getConstructor(player,String.class).newInstance(actor,focus);
            return LegacyPackets.encode(ThaumcraftReflection.packetHandler(loader),request,"SERVER");
        });ScanSyncChannel.sendLegacyPacket(packet);
    }
    private static final class Selector extends Screen {
        private final LegacyServices services;
        private final List<Map.Entry<String,LegacyClientData.Stack>> choices;
        private final LegacyClientData.Stack equipped;
        Selector(LegacyServices services,Map<String,LegacyClientData.Stack> choices,LegacyClientData.Stack equipped){super(Component.translatable("mfcl.wand.focus.title"));this.services=services;this.choices=new ArrayList<>(choices.entrySet());this.equipped=equipped;}
        private double radius(){return 16.0+choices.size()*2.5;}
        private int selected(double x,double y){
            double dx=x-width/2.0,dy=y-height/2.0;
            for(int i=0;i<choices.size();i++){
                double angle=Math.PI*2*i/choices.size()-Math.PI/2;
                if(Math.abs(dx-Math.cos(angle)*radius())<=10&&Math.abs(dy-Math.sin(angle)*radius())<=10)return i;
            }
            return -1;
        }
        private void select(String key){try{send(services,key);onClose();}catch(Exception failure){LogUtils.getLogger().error("MFCL focus selection failed",failure);}}
        @Override public boolean mouseClicked(double x,double y,int button){
            if(button!=0)return super.mouseClicked(x,y,button);
            int i=selected(x,y);if(i>=0)select(choices.get(i).getKey());return true;
        }
        @Override public boolean keyReleased(int key,int scan,int modifiers){
            var binding=LegacyKeyMappings.find(FOCUS);
            if(binding!=null&&binding.matches(key,scan)){
                var mc=Minecraft.getInstance();double x=mc.mouseHandler.xpos()*width/mc.getWindow().getScreenWidth(),y=mc.mouseHandler.ypos()*height/mc.getWindow().getScreenHeight();
                int i=selected(x,y);if(i>=0)select(choices.get(i).getKey());else onClose();return true;
            }
            return super.keyReleased(key,scan,modifiers);
        }
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics graphics,int x,int y,float partial){
            var content=ModernContentBridge.active();var mc=Minecraft.getInstance();int cx=width/2,cy=height/2;
            var radial=net.minecraft.resources.ResourceLocation.parse(content.pack().worldResource("thaumcraft:textures/misc/radial.png"));
            var radial2=net.minecraft.resources.ResourceLocation.parse(content.pack().worldResource("thaumcraft:textures/misc/radial2.png"));
            var pose=graphics.pose();float angle=(mc.player.tickCount+partial)/2f;
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            graphics.setColor(.5f,.5f,.5f,.5f);
            drawLayer(graphics,radial,cx,cy,(float)(radius()*2.75),angle);
            drawLayer(graphics,radial2,cx,cy,(float)(radius()*2.55),-angle);
            graphics.setColor(1,1,1,1);
            if(equipped!=null)graphics.renderItem(content.gameplayStack(equipped),cx-8,cy-8);
            int hovered=selected(x,y);
            for(int i=0;i<choices.size();i++){
                double a=Math.PI*2*i/choices.size()-Math.PI/2;
                int ix=cx+(int)Math.round(Math.cos(a)*radius())-8,iy=cy+(int)Math.round(Math.sin(a)*radius())-8;
                graphics.renderItem(content.gameplayStack(choices.get(i).getValue()),ix,iy);
            }
            if(hovered>=0)try{graphics.renderTooltip(font,Component.literal(services.items().displayName(choices.get(hovered).getValue())),x,y);}catch(Exception failure){LogUtils.getLogger().debug("Cannot name focus",failure);}
        }
        private void drawLayer(GuiGraphics graphics,net.minecraft.resources.ResourceLocation texture,int x,int y,float size,float angle){
            var pose=graphics.pose();pose.pushPose();pose.translate(x,y,0);pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(angle));pose.scale(size/256f,size/256f,1);
            graphics.blit(texture,-128,-128,0,0,256,256,256,256);pose.popPose();
        }
    }
}
