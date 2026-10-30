package github.com.gengyoubo.tcmodern.tc4.client.testing;

import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Keep optional FTB types out of automatically discovered subscriber signatures. */
@Mod.EventBusSubscriber(modid = "tc4modern", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FtbQuestsSmokeBootstrap {
    private static boolean finished;
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        if (!Boolean.getBoolean("tc4modern.ftbQuestsSmokeTest")) return;
        if (ModList.get().isLoaded("ftbquests")) event.enqueueWork(FtbQuestsSmokeProbe::install);
        else event.enqueueWork(() -> MinecraftForge.EVENT_BUS.addListener(FtbQuestsSmokeBootstrap::withoutFtb));
    }
    private static void withoutFtb(TickEvent.ClientTickEvent event) {
        var mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || finished || !(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
        finished = true;
        try {
            var path = mc.gameDirectory.toPath().resolve("logs/tc4modern/ftb-quests-smoke.json");
            Files.createDirectories(path.getParent());
            Files.writeString(path, "{\"loadsWithoutFtbQuests\":true,\"status\":\"PASS\"}");
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
        mc.stop();
    }
}
