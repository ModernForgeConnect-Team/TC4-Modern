package github.com.gengyoubo.tcmodern.tc4.client.testing;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.tc4.client.FormedStructures;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
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

/**
 * Opt-in acceptance test for the formed multiblock previews that JEI and Jade share: draws every formed structure
 * from two angles, saves a screenshot and records each scene's bounds and MFCL's scene diagnostics.
 */
@Mod.EventBusSubscriber(modid = "tc4modern", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FormedPreviewSmokeProbe {
    private static final List<String> IDS = List.of("research_table", "infusion_altar", "infernal_furnace",
            "advanced_alchemy_furnace", "thaumatorium", "node_jar");
    private static final List<String> SINGLES = List.of("Thaumcraft:blockStoneDevice@2", "Thaumcraft:blockTable@0", "Thaumcraft:blockStoneDevice@1");
    private final long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(5);
    private final Map<String, Object> proof = new LinkedHashMap<>();
    private int stage, frames;

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        if (Boolean.getBoolean("tc4modern.formedPreviewSmokeTest"))
            event.enqueueWork(() -> MinecraftForge.EVENT_BUS.addListener(new FormedPreviewSmokeProbe()::tick));
    }

    private static final class Gallery extends Screen {
        final Map<String, Boolean> drawn = new LinkedHashMap<>();
        Gallery() { super(Component.literal("Formed previews")); }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
            graphics.fill(0, 0, width, height / 2, 0xFFC6C6C6); graphics.fill(0, height / 2, width, height, 0xFF202020);
            int cell = Math.min(width / IDS.size(), height / 2);
            for (int i = 0; i < IDS.size(); i++) {
                var id = IDS.get(i);
                float x = cell * i + cell / 2F;
                boolean front = FormedStructures.render(graphics, id, x, cell / 2F, cell * 0.9F, 40, 45, 30);
                boolean back = FormedStructures.render(graphics, id, x, cell * 1.5F, cell * 0.9F, 40, 225, 20);
                drawn.put(id, front && back);
                graphics.drawString(font, id, Math.round(cell * i + 2), 2, 0xFF202020, false);
            }
            // Tile-rendered single blocks of the unformed previews.
            for (int i = 0; i < SINGLES.size(); i++) {
                var key = SINGLES.get(i);
                int at = key.indexOf('@');
                drawn.put(key, single(graphics, key.substring(0, at), Integer.parseInt(key.substring(at + 1)), 70 + i * 120, cell * 2 + 60));
                graphics.drawString(font, key, 10 + i * 120, cell * 2 + 4, 0xFFFFFFFF, false);
            }
        }

        private static boolean single(GuiGraphics graphics, String key, int metadata, float x, float y) {
            var pose = graphics.pose();
            pose.pushPose();
            pose.translate(x, y, 200);
            pose.scale(50, -50, 50);
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(30));
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(45));
            pose.translate(-0.5, -0.5, -0.5);
            com.mojang.blaze3d.platform.Lighting.setupForEntityInInventory();
            com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
            boolean drawn = FormedStructures.renderBlock(key, metadata, pose, graphics.bufferSource(), 225, 30);
            graphics.flush();
            com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
            pose.popPose();
            return drawn;
        }
        @Override public boolean isPauseScreen() { return false; }
    }

    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == 99) return;
        var mc = Minecraft.getInstance();
        try {
            if (System.nanoTime() > deadline) throw new IllegalStateException("Formed preview smoke timed out at " + stage);
            if (stage == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
                mc.options.pauseOnLostFocus = false;
                mc.options.renderDistance().set(2);
                mc.createWorldOpenFlows().createFreshLevel("tc4modern-formed-smoke-" + System.currentTimeMillis(),
                        new LevelSettings("TC4 Modern formed previews", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                                true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(42, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                stage = 1;
            } else if (stage == 1 && mc.player != null && mc.level != null && mc.screen == null && ++frames >= 40) {
                mc.setScreen(new Gallery());
                frames = 0;
                stage = 2;
            } else if (stage == 2 && ++frames >= 40) {
                Screenshot.grab(mc.gameDirectory, "tc4modern-formed-previews.png", mc.getMainRenderTarget(),
                        message -> LogUtils.getLogger().info("{}", message.getString()));
                var gallery = (Gallery) mc.screen;
                var scenes = new LinkedHashMap<String, Object>();
                for (var id : IDS) scenes.put(id, FormedStructures.scene(id).flatMap(scene -> scene.bounds()).map(Object::toString).orElse("unavailable"));
                proof.put("scenes", scenes);
                proof.put("drawn", gallery.drawn);
                var runtime = TcModernApi.runtime().orElseThrow();
                proof.put("diagnostics", runtime.diagnostics().stream().filter(d -> d.code().startsWith("SCENE"))
                        .map(d -> d.operation() + ": " + d.detail()).toList());
                if (gallery.drawn.containsValue(false) || gallery.drawn.size() != IDS.size() + SINGLES.size())
                    throw new IllegalStateException("Some formed previews were not drawn: " + gallery.drawn);
                finish(mc, null);
            }
        } catch (Exception | AssertionError failure) { finish(mc, failure); }
    }

    private void finish(Minecraft mc, Throwable failure) {
        stage = 99;
        proof.put("status", failure == null ? "PASS" : "FAIL");
        if (failure != null) {
            proof.put("failure", failure.toString());
            LogUtils.getLogger().error("TC4_MODERN_FORMED_SMOKE_FAIL", failure);
        }
        try {
            var file = mc.gameDirectory.toPath().resolve("logs/tc4modern/formed-preview-smoke.json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
        if (mc.level != null) { mc.level.disconnect(); mc.clearLevel(); }
        mc.stop();
    }
}
