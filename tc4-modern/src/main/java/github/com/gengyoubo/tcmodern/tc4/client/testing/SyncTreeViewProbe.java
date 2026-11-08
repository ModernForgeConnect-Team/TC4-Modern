package github.com.gengyoubo.tcmodern.tc4.client.testing;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import java.nio.file.Files;

/** Renders the previously created disposable client test world from outside its tree. */
@Mod.EventBusSubscriber(modid="tc4modern",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class SyncTreeViewProbe {
    private int stage,frames;
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        if(Boolean.getBoolean("tc4modern.syncTreeViewProbe"))event.enqueueWork(()->MinecraftForge.EVENT_BUS.addListener(new SyncTreeViewProbe()::tick));
    }
    private void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||stage==99)return;
        var mc=Minecraft.getInstance();
        try {
            if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null) {
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(8);
                var result=mc.gameDirectory.toPath().resolve("logs/tc4modern/sync-client-80-result.txt");
                var world=Files.readAllLines(result).stream().filter(s->s.startsWith("world=")).findFirst().orElseThrow().substring(6);
                mc.createWorldOpenFlows().loadLevel(new TitleScreen(),world);stage=1;
            } else if(stage==1&&mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen confirmation) {
                for(var child:confirmation.children())if(child instanceof net.minecraft.client.gui.components.Button button){button.onPress();break;}
            } else if(stage==1&&mc.level!=null&&mc.player!=null&&mc.screen==null) {
                var server=mc.getSingleplayerServer();server.submit(()->{var p=server.getPlayerList().getPlayers().get(0);p.getAbilities().flying=true;p.onUpdateAbilities();p.teleportTo(4.5,345,-103.5);p.setYRot(0);p.setXRot(20);});stage=2;
            } else if(stage==2&&mc.player!=null) {
                mc.player.setYRot(0);mc.player.setXRot(20);
                if(++frames>=180) {
                    var path=mc.gameDirectory.toPath().resolve("logs/tc4modern/sync-client-80-outside.png");
                    try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}
                    Files.writeString(path.resolveSibling("sync-client-80-outside.txt"),"completed=true\nframes="+frames+"\n");stage=99;mc.stop();
                }
            }
        } catch(Throwable failure) {failure.printStackTrace();try{Files.writeString(mc.gameDirectory.toPath().resolve("logs/tc4modern/sync-client-80-outside.txt"),"error="+failure+"\n");}catch(Exception ignored){}stage=99;mc.stop();}
    }
}
