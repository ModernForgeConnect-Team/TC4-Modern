package github.com.gengyoubo.tcmodern.tc4.client.arcane;

import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** This entry point links only API v2 base types, so older MFCL builds can retain their original GUI. */
@Mod.EventBusSubscriber(modid=Tc4Modern.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ArcaneWorkbenchClient {
    private ArcaneWorkbenchClient() {}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event){
        event.enqueueWork(()->TcModernApi.whenReady(runtime->{
            if(runtime.supports("client-screens"))ArcaneWorkbenchGui.install(runtime);
            else com.mojang.logging.LogUtils.getLogger().warn("TC4 native arcane worktable GUI requires MFCL client-screens support; using the hosted original GUI");
        }));
    }
}
