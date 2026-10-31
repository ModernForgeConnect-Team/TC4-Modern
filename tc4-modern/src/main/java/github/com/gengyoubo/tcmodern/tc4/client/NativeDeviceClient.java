package github.com.gengyoubo.tcmodern.tc4.client;

import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import github.com.gengyoubo.tcmodern.tc4.client.arcane.ArcaneWorkbenchGui;
import github.com.gengyoubo.tcmodern.tc4.client.deconstruction.DeconstructionTableGui;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Native views of the arcane worktable and deconstruction table. Older MFCL builds keep the hosted original GUIs. */
@Mod.EventBusSubscriber(modid=Tc4Modern.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class NativeDeviceClient {
    private NativeDeviceClient() {}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event){
        event.enqueueWork(()->TcModernApi.whenReady(runtime->{
            if(runtime.supports("client-screens")){ArcaneWorkbenchGui.install(runtime);DeconstructionTableGui.install(runtime);}
            else com.mojang.logging.LogUtils.getLogger().warn("TC4 native device GUIs require MFCL client-screens support; using the hosted original GUIs");
        }));
    }
}
