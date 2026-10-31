package github.com.gengyoubo.tcmodern.tc4.client.desk;

import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4BlockEntities;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Menus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Screens and the desktop renderer of the research desk and archive cabinet. */
@Mod.EventBusSubscriber(modid = Tc4Modern.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DeskClient {
    private DeskClient() {}

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(Tc4Menus.RESEARCH_DESK.get(), ResearchDeskScreen::new);
            MenuScreens.register(Tc4Menus.ARCHIVE_CABINET.get(), ArchiveCabinetScreen::new);
        });
    }

    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(Tc4BlockEntities.RESEARCH_DESK.get(), ResearchDeskRenderer::new);
    }
}
