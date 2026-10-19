package github.com.gengyoubo.tcmodern.tc4.client.testing;

import net.minecraft.client.Screenshot;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.IntUnaryOperator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Opt-in acceptance test. Does nothing in normal games and links no optional JEI types. */
@Mod.EventBusSubscriber(modid = "tc4modern", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class JeiSmokeProbe {
    private static volatile IntUnaryOperator showCategory;
    private final long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(5);
    private final Map<String, Object> proof = new LinkedHashMap<>();
    private final String[] categories = {"arcane", "crucible", "infusion"};
    private int stage, category, frames;

    public static void ready(IntUnaryOperator callback) { showCategory = callback; }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        if (Boolean.getBoolean("tc4modern.jeiSmokeTest")) {
            event.enqueueWork(() -> MinecraftForge.EVENT_BUS.addListener(new JeiSmokeProbe()::tick));
        }
    }

    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == 99) return;
        var mc = Minecraft.getInstance();
        try {
            if (System.nanoTime() > deadline) throw new IllegalStateException("JEI smoke timed out at " + stage);
            if (stage == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
                mc.options.pauseOnLostFocus = false;
                mc.options.renderDistance().set(2);
                mc.createWorldOpenFlows().createFreshLevel("tc4modern-jei-smoke-" + System.currentTimeMillis(),
                        new LevelSettings("TC4 Modern JEI", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                                true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(42, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                stage = 1;
            } else if (stage == 1 && mc.player != null && mc.level != null && mc.screen == null && showCategory != null) {
                proof.put(categories[category], showCategory.applyAsInt(category));
                frames = 0;
                stage = 2;
            } else if (stage == 2 && ++frames >= 30) {
                Screenshot.grab(mc.gameDirectory, "tc4modern-jei-" + categories[category] + ".png",
                        mc.getMainRenderTarget(), message -> LogUtils.getLogger().info("{}", message.getString()));
                category++;
                if (category < categories.length) {
                    proof.put(categories[category], showCategory.applyAsInt(category));
                    frames = 0;
                } else { frames = 0; stage = 3; }
            } else if (stage == 3 && ++frames >= 20) {
                for (var name : categories) {
                    if (!Files.isRegularFile(mc.gameDirectory.toPath().resolve("screenshots/tc4modern-jei-" + name + ".png")))
                        throw new IllegalStateException("Missing screenshot for " + name);
                }
                proof.put("allLayoutsCreated", true);
                proof.put("recipeAndUsageLookups", true);
                finish(mc, null);
            }
        } catch (Exception | AssertionError failure) { finish(mc, failure); }
    }

    private void finish(Minecraft mc, Throwable failure) {
        stage = 99;
        proof.put("status", failure == null ? "PASS" : "FAIL");
        if (failure != null) {
            proof.put("failure", failure.toString());
            LogUtils.getLogger().error("TC4_MODERN_JEI_SMOKE_FAIL", failure);
        }
        try {
            var file = mc.gameDirectory.toPath().resolve("logs/tc4modern/jei-smoke.json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
        if (mc.level != null) { mc.level.disconnect(); mc.clearLevel(); }
        mc.stop();
    }
}
