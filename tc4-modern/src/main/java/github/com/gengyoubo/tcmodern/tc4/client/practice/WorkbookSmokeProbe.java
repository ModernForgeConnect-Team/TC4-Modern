package github.com.gengyoubo.tcmodern.tc4.client.practice;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspects;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Items;
import github.com.gengyoubo.tcmodern.tc4.practice.PracticeAspects;
import github.com.gengyoubo.tcmodern.tc4.practice.PracticePuzzle;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Opt-in acceptance against the installed TC4 registry, real item use, GUI input and rendering. */
@Mod.EventBusSubscriber(modid = "tc4modern", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class WorkbookSmokeProbe {
    private final long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(5);
    private final Map<String, Object> proof = new LinkedHashMap<>();
    private CompletableFuture<?> pending;
    private int stage, frames, level = 1;

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        if (Boolean.getBoolean("tc4modern.workbookSmokeTest"))
            event.enqueueWork(() -> MinecraftForge.EVENT_BUS.addListener(new WorkbookSmokeProbe()::tick));
    }

    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == 99) return;
        var mc = Minecraft.getInstance();
        try {
            require(System.nanoTime() < deadline, "Workbook smoke timed out at stage " + stage);
            if (pending != null && pending.isCompletedExceptionally()) pending.join();
            if (stage == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
                mc.options.pauseOnLostFocus = false;
                mc.options.renderDistance().set(2);
                mc.options.languageCode = "zh_cn";
                mc.getLanguageManager().setSelected("zh_cn");
                pending = mc.reloadResourcePacks();
                stage = 1;
            } else if (stage == 1 && pending.isDone() && mc.getOverlay() == null) {
                verifyRegistry();
                mc.createWorldOpenFlows().createFreshLevel("tc4modern-workbook-smoke-" + System.currentTimeMillis(),
                        new LevelSettings("TC4 Workbook", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                                true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(42, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                stage = 2;
            } else if (stage == 2 && mc.player != null && mc.level != null && mc.screen == null) {
                var server = mc.getSingleplayerServer();
                pending = server.submit(() -> {
                    var recipe = server.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("tc4modern", "element_workbook")).orElseThrow();
                    require(recipe.getResultItem(server.registryAccess()).is(Tc4Items.ELEMENT_WORKBOOK.get()), "Missing workbook recipe");
                    server.getPlayerList().getPlayers().get(0).setItemInHand(InteractionHand.MAIN_HAND,
                            new ItemStack(Tc4Items.ELEMENT_WORKBOOK.get()));
                });
                frames = 0;
                stage = 3;
            } else if (stage == 3 && pending.isDone() && ++frames > 20) {
                require(mc.player.getMainHandItem().is(Tc4Items.ELEMENT_WORKBOOK.get()), "Workbook did not sync to client hand");
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                require(mc.screen instanceof ElementWorkbookScreen, "Workbook use did not open practice");
                proof.put("recipeAndItemUse", true);
                frames = 0;
                stage = 4;
            } else if (stage == 4 && ++frames > 15) {
                var screen = (ElementWorkbookScreen) mc.screen;
                click(mc, screen, 14 + (level - 1) * 30 + 14, 41, 0);
                require(screen.puzzle() != null && screen.puzzle().level() == level, "Level selection failed");
                exerciseInput(mc, screen);
                frames = 0;
                stage = 5;
            } else if (stage == 5 && ++frames > 15) {
                Screenshot.grab(mc.gameDirectory, "tc4modern-workbook-level-" + level + ".png", mc.getMainRenderTarget(), message -> { });
                if (level++ < 7) { frames = 0; stage = 4; }
                else {
                    var screen = (ElementWorkbookScreen) mc.screen;
                    for (int i = 0; i < screen.puzzle().cells().size() && !screen.puzzle().solved(); i++) click(mc, screen, 257, 235, 0);
                    require(screen.puzzle().solved(), "Hint button did not reach a solution");
                    frames = 0;
                    stage = 6;
                }
            } else if (stage == 6 && ++frames > 15) {
                Screenshot.grab(mc.gameDirectory, "tc4modern-workbook-solved.png", mc.getMainRenderTarget(), message -> { });
                proof.put("levelsAndInputAndHints", true);
                frames = 0;
                stage = 7;
            } else if (stage == 7 && ++frames > 20) {
                require(Files.isRegularFile(mc.gameDirectory.toPath().resolve("screenshots/tc4modern-workbook-solved.png")), "Missing screenshot");
                finish(mc, null);
            }
        } catch (Exception | AssertionError failure) { finish(mc, failure); }
    }

    private void verifyRegistry() {
        var graph = new PracticeAspects(TcAspects.snapshot(TcModernApi.runtime().orElseThrow()));
        var counts = new LinkedHashMap<Integer, Integer>();
        for (int tier = 1; tier <= 7; tier++) {
            final int selectedTier = tier;
            counts.put(tier, graph.available(tier).size());
            for (int seed = 0; seed < 100; seed++) {
                var puzzle = PracticePuzzle.generate(graph, tier, new Random(seed));
                require(!puzzle.solved(), "Generated a pre-solved board");
                require(puzzle.solution().values().stream().allMatch(tag -> graph.tier(tag) <= selectedTier), "Tier limit exceeded");
                puzzle.solution().forEach(puzzle::place);
                require(puzzle.solved() && puzzle.connected().equals(puzzle.cells()), "Generated board is unsolvable");
            }
        }
        proof.put("realRegistrySolvableBoards", 700);
        proof.put("availableAspectsByLevel", counts);
    }

    private void exerciseInput(Minecraft mc, ElementWorkbookScreen screen) {
        var puzzle = screen.puzzle();
        var editable = puzzle.cells().stream().filter(h -> !puzzle.anchors().containsKey(h)).findFirst().orElseThrow();
        int x = 255 + (int) Math.round(Math.sqrt(3) * 12 * (editable.q() + editable.r() / 2.0));
        int y = 151 + 18 * editable.r();
        click(mc, screen, 20, 96, 0);
        click(mc, screen, x, y, 0);
        require(puzzle.placed().containsKey(editable), "Left click did not place aspect");
        click(mc, screen, x, y, 1);
        require(!puzzle.placed().containsKey(editable), "Right click did not erase aspect");
        click(mc, screen, 257, 235, 0);
        require(puzzle.placed().size() > puzzle.anchors().size(), "Hint did not fill a cell");
        click(mc, screen, 197, 235, 0);
        require(puzzle.placed().equals(puzzle.anchors()), "Clear did not preserve fixed aspects");
    }

    private static void click(Minecraft mc, ElementWorkbookScreen screen, int x, int y, int button) {
        int width = mc.getWindow().getGuiScaledWidth(), height = mc.getWindow().getGuiScaledHeight();
        float scale = Math.min(1.5F, Math.min((width - 8F) / 360, (height - 8F) / 280));
        double px = (width - 360 * scale) / 2 + x * scale, py = (height - 280 * scale) / 2 + y * scale;
        screen.mouseClicked(px, py, button);
        screen.mouseReleased(px, py, button);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private void finish(Minecraft mc, Throwable failure) {
        stage = 99;
        proof.put("status", failure == null ? "PASS" : "FAIL");
        if (failure != null) {
            proof.put("failure", failure.toString());
            LogUtils.getLogger().error("TC4_WORKBOOK_SMOKE_FAIL", failure);
        }
        try {
            var file = mc.gameDirectory.toPath().resolve("logs/tc4modern/workbook-smoke.json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
        LogUtils.getLogger().info("TC4_WORKBOOK_SMOKE_RESULT: {}", proof);
        if (mc.level != null) { mc.level.disconnect(); mc.clearLevel(); }
        mc.stop();
    }
}
