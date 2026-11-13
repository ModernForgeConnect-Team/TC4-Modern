package github.com.gengyoubo.tcmodern.lib.client.testing;

import github.com.gengyoubo.tcmodern.lib.TcModernLib;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Creates an isolated world so JEI initializes during the opt-in standalone smoke run. */
@Mod.EventBusSubscriber(modid=TcModernLib.MOD_ID, value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
public final class StandaloneJeiSmoke {
    private static boolean started;
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        if (Boolean.getBoolean("tcmodernlib.jeiSmokeTest") || Boolean.getBoolean("tcmodernlib.noJeiSmokeTest")) event.enqueueWork(() -> MinecraftForge.EVENT_BUS.addListener(StandaloneJeiSmoke::tick));
    }
    private static void tick(TickEvent.ClientTickEvent event) {
        var mc = Minecraft.getInstance();
        if (started || event.phase != TickEvent.Phase.END || !(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
        started = true;
        if (Boolean.getBoolean("tcmodernlib.noJeiSmokeTest")) {
            try {
                if (net.minecraftforge.fml.ModList.get().isLoaded("jei")) throw new IllegalStateException("JEI must be absent");
                java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("lib-no-jei-smoke.txt"), "PASS");
            } catch (Exception failure) { throw new IllegalStateException(failure); }
            mc.stop(); return;
        }
        org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        mc.options.pauseOnLostFocus = false; mc.options.renderDistance().set(2);
        mc.createWorldOpenFlows().createFreshLevel("lib-jei-smoke-" + System.currentTimeMillis(),
            new LevelSettings("Lib JEI smoke", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
            new WorldOptions(42, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
    }
}
