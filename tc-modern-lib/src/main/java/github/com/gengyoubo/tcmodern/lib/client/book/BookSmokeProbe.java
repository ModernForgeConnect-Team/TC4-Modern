package github.com.gengyoubo.tcmodern.lib.client.book;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspects;
import github.com.gengyoubo.tcmodern.api.aspect.TcPlayerAspects;
import github.com.gengyoubo.tcmodern.api.research.TcResearch;
import github.com.gengyoubo.tcmodern.api.research.TcThaumonomicon;
import github.com.gengyoubo.tcmodern.lib.TcModernLib;
import github.com.gengyoubo.tcmodern.lib.json.ResearchLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;

/**
 * Opt-in acceptance of the modern Thaumonomicon with {@code -PbookSmokeTest}: JSON research written to the config
 * folder and loaded by the debug reload, the original book item opening the new screen, visibility, debug-mode
 * complete/revoke for one research and a category, buying, research notes, the reader and a second reload.
 */
@Mod.EventBusSubscriber(modid = TcModernLib.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BookSmokeProbe {
    private static final int WAIT = 14;
    private static final String CATEGORY = "TC4TEST", ROOT = "TEST_ROOT", CHILD = "TEST_CHILD", NOTE = "TEST_NOTE", HIDDEN = "TEST_HIDDEN";
    private final long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(6);
    private final Map<String, Object> proof = new LinkedHashMap<>();
    private CompletableFuture<?> pending;
    private int stage, frames, lastStage = -1, airBefore;

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        if (Boolean.getBoolean("tcmodernlib.bookSmokeTest"))
            event.enqueueWork(() -> MinecraftForge.EVENT_BUS.addListener(new BookSmokeProbe()::tick));
    }

    private static LegacyRuntime api() { return TcModernApi.runtime().orElseThrow(); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static ServerPlayer player(Minecraft mc) { return mc.getSingleplayerServer().getPlayerList().getPlayers().get(0); }
    private CompletableFuture<?> server(Minecraft mc, Runnable action) { return mc.getSingleplayerServer().submit(action); }
    private static ThaumonomiconScreen book(Minecraft mc) {
        require(mc.screen instanceof ThaumonomiconScreen, "Thaumonomicon is not open: " + mc.screen);
        return (ThaumonomiconScreen) mc.screen;
    }

    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == 99) return;
        var mc = Minecraft.getInstance();
        try {
            if (stage != lastStage) { lastStage = stage; frames = 0; LogUtils.getLogger().info("TCMODERNLIB_BOOK_STAGE {}", stage); }
            require(System.nanoTime() < deadline, "Book smoke timed out at stage " + stage);
            if (pending != null && pending.isCompletedExceptionally()) pending.join();
            if (stage == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                GLFW.glfwHideWindow(mc.getWindow().getWindow());
                mc.options.pauseOnLostFocus = false;
                mc.options.renderDistance().set(2);
                mc.options.languageCode = "zh_cn";
                mc.getLanguageManager().setSelected("zh_cn");
                pending = mc.reloadResourcePacks();
                stage = 1;
            } else if (stage == 1 && pending.isDone() && mc.getOverlay() == null) {
                writeFixtures("测试根研究");
                mc.createWorldOpenFlows().createFreshLevel("tcmodernlib-book-" + System.currentTimeMillis(),
                        new LevelSettings("Thaumonomicon", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(42, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                stage = 2;
            } else if (stage == 2 && mc.level != null && mc.player != null && mc.screen == null) {
                pending = server(mc, () -> seed(mc));
                stage = 3;
            } else if (stage == 3 && pending.isDone() && ++frames > WAIT) {
                // The JSON files appeared after start-up: the debug reload must bring them in, on the server and here.
                require(mc.player.hasPermissions(2), "Single-player host is not an operator");
                github.com.gengyoubo.tcmodern.lib.book.BookNetwork.send(new github.com.gengyoubo.tcmodern.lib.book.BookNetwork.Action(
                        github.com.gengyoubo.tcmodern.lib.book.BookNetwork.Kind.RELOAD, "", false));
                stage = 4;
            } else if (stage == 4 && ++frames > WAIT * 2) {
                var category = BookClient.book().stream().filter(c -> c.key().equals(CATEGORY)).findFirst().orElse(null);
                require(category != null && category.entries().size() == 4, "JSON category did not load: " + ResearchLoader.last());
                require(ResearchLoader.last().errors().isEmpty(), "JSON research reported errors: " + ResearchLoader.last().errors());
                require("测试根研究".equals(TcThaumonomicon.name(api(), ROOT)), "Inline name not published: " + TcThaumonomicon.name(api(), ROOT));
                var root = category.entries().stream().filter(e -> e.key().equals(ROOT)).findFirst().orElseThrow();
                require(root.pages().size() == 6, "Root pages: " + root.pages());
                proof.put("jsonCategoryAndResearchLoadedByDebugReload", true);
                proof.put("rootPageKinds", root.pages().stream().map(p -> p.getClass().getSimpleName()).toList());
                // Open through the original item: the hook must replace the original browser with the modern book.
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                stage = 5;
            } else if (stage == 5 && mc.screen instanceof ThaumonomiconScreen && ++frames > WAIT) {
                proof.put("originalBookItemOpensModernBook", true);
                var screen = book(mc);
                screen.select(CATEGORY);
                screen.refresh();
                var hidden = entry(screen, HIDDEN);
                require(!screen.visible(hidden), "Hidden research is visible without debug mode");
                require(screen.visible(entry(screen, CHILD)) && !screen.unlockable(entry(screen, CHILD)), "Child should be visible and locked");
                screenshot(mc, "map", 0, 0);
                click(screen, screen.controlPoint("toggle"), 0);
                require(screen.cheating() && screen.visible(hidden), "Debug mode did not reveal hidden research");
                // Debug menu on the root research: "complete".
                click(screen, screen.nodeCenter(ROOT), 1);
                click(screen, screen.menuPoint(1), 0);
                stage = 6;
            } else if (stage == 6 && ++frames > WAIT) {
                var screen = book(mc);
                require(screen.completedKeys().contains(ROOT), "Debug complete did not reach the client");
                pending = server(mc, () -> require(TcResearch.isComplete(api(), player(mc), ROOT), "Debug complete did not reach the server"));
                screenshot(mc, "debug", Math.round(screen.nodeCenter(HIDDEN)[0]), Math.round(screen.nodeCenter(HIDDEN)[1]));
                proof.put("debugCompleteSingle", true);
                click(screen, screen.controlPoint("toggle"), 0);
                airBefore = screen.pool("aer");
                // Normal mode: the secondary child is bought with points.
                click(screen, screen.nodeCenter(CHILD), 0);
                stage = 7;
            } else if (stage == 7 && pending.isDone() && ++frames > WAIT) {
                var screen = book(mc);
                require(!screen.cheating(), "Debug mode did not switch off");
                require(screen.completedKeys().contains(CHILD), "Buying the secondary research failed");
                require(screen.pool("aer") == airBefore - 2, "Buying did not spend 2 aer: " + airBefore + " -> " + screen.pool("aer"));
                proof.put("buySecondaryResearch", true);
                click(screen, screen.nodeCenter(NOTE), 0);
                stage = 8;
            } else if (stage == 8 && ++frames > WAIT) {
                pending = server(mc, () -> {
                    var player = player(mc);
                    boolean note = false;
                    for (var stack : player.getInventory().items) {
                        if (stack.isEmpty()) continue;
                        var legacy = api().mappings().toLegacy(LegacyMappings.ITEM_STACK, stack).orElse(null);
                        if (legacy != null && api().snapshot(legacy).key().equals("Thaumcraft:ItemResearchNotes")) note = true;
                    }
                    require(note, "Clicking a primary research did not write a research note");
                });
                stage = 9;
            } else if (stage == 9 && pending.isDone() && ++frames > 2) {
                proof.put("primaryResearchWritesNote", true);
                var screen = book(mc);
                click(screen, screen.controlPoint("toggle"), 0);
                click(screen, screen.nodeCenter(ROOT), 0);
                require(screen.reader() != null && screen.reader().sides() >= 6, "Reader pages: " + (screen.reader() == null ? "closed" : screen.reader().sides()));
                screenshot(mc, "reader-1", 0, 0);
                screen.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0);
                screenshot(mc, "reader-2", 0, 0);
                screen.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0);
                screenshot(mc, "reader-3", 0, 0);
                screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                require(screen.reader() == null, "Escape did not close the reader");
                proof.put("readerPages", true);
                // Revoke with everything that needs it.
                click(screen, screen.nodeCenter(ROOT), 1);
                click(screen, screen.menuPoint(4), 0);
                stage = 10;
            } else if (stage == 10 && ++frames > WAIT) {
                var screen = book(mc);
                require(!screen.completedKeys().contains(ROOT) && !screen.completedKeys().contains(CHILD), "Revoke with dependents left research");
                proof.put("debugRevokeWithDependents", true);
                click(screen, screen.categoryPoint(CATEGORY), 1);
                click(screen, screen.menuPoint(0), 0);
                stage = 11;
            } else if (stage == 11 && ++frames > WAIT) {
                var screen = book(mc);
                require(screen.completedKeys().containsAll(List.of(ROOT, CHILD, NOTE, HIDDEN)), "Category complete missed research");
                click(screen, screen.categoryPoint(CATEGORY), 1);
                click(screen, screen.menuPoint(1), 0);
                stage = 12;
            } else if (stage == 12 && ++frames > WAIT) {
                var screen = book(mc);
                require(List.of(ROOT, CHILD, NOTE, HIDDEN).stream().noneMatch(screen.completedKeys()::contains), "Category reset left research");
                proof.put("debugCategoryCompleteAndReset", true);
                writeFixtures("测试根研究（已修改）");
                click(screen, screen.controlPoint("reload"), 0);
                stage = 13;
            } else if (stage == 13 && ++frames > WAIT * 2) {
                var screen = book(mc);
                require("测试根研究（已修改）".equals(TcThaumonomicon.name(api(), ROOT)), "Second reload did not apply the edit");
                proof.put("hotReloadAppliesEdits", true);
                screen.focus("NITOR");
                click(screen, screen.nodeCenter("NITOR"), 0);
                require(screen.reader() != null, "Original research did not open in debug mode");
                screenshot(mc, "original", 0, 0);
                screen.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0);
                screenshot(mc, "original-2", 0, 0);
                proof.put("originalResearchPages", true);
                screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                screen.select(CATEGORY);
                require(screen.cheating(), "Edit mode is off");
                // Drag a JSON research to a free slot.
                float[] from = screen.nodeCenter(CHILD), to = screen.cellCenter(4, 0);
                press(screen, from);
                drag(screen, new float[]{from[0] + 6, from[1]});
                drag(screen, to);
                release(screen, to);
                stage = 14;
            } else if (stage == 14 && ++frames > WAIT * 2) {
                var screen = book(mc);
                var child = entry(screen, CHILD);
                require(child.column() == 4 && child.row() == 0, "Dragging did not move the research: " + child.column() + "," + child.row());
                require(Files.readString(fixture("child")).replaceAll("\\s", "").contains("\"position\":[4,0]"), "Move was not written to the JSON file");
                proof.put("editDragMovesResearch", true);
                // Right-click an empty slot: new research there.
                click(screen, screen.cellCenter(4, 2), 1);
                click(screen, screen.menuPoint(0), 0);
                require(mc.screen instanceof github.com.gengyoubo.tcmodern.lib.client.book.edit.ResearchEditorScreen, "New research did not open the editor");
                stage = 15;
            } else if (stage == 15 && ++frames > WAIT) {
                var editor = (github.com.gengyoubo.tcmodern.lib.client.book.edit.ResearchEditorScreen) mc.screen;
                var form = editor.current();
                require(form.column == 4 && form.row == 2 && CATEGORY.equals(form.categoryKey), "New research form is not at the clicked slot");
                form.key = "TEST_NEW";
                form.name.zh = "新建的研究";
                form.name.en = "Created in game";
                form.aspects = "aer:1";
                form.parents = "TEST_ROOT";
                form.pages.get(0).text.zh = "在游戏里写的页面。";
                editor.load(form);
                screenshot(mc, "editor", 0, 0);
                editor.submit();
                require(mc.screen instanceof ThaumonomiconScreen, "Saving did not return to the book");
                stage = 16;
            } else if (stage == 16 && ++frames > WAIT * 2) {
                var screen = book(mc);
                var created = entry(screen, "TEST_NEW");
                require(created.column() == 4 && created.row() == 2 && created.parents().equals(List.of(ROOT)), "Created research differs");
                require(Files.isRegularFile(fixture("test_new")), "New research was not written to config/tc4modern/tc4_research/tc4test");
                require("新建的研究".equals(TcThaumonomicon.name(api(), "TEST_NEW")), "New research name missing");
                proof.put("editCreateResearch", true);
                // Edit an existing research through its menu ("Edit…" follows copy key).
                click(screen, screen.nodeCenter(NOTE), 1);
                click(screen, screen.menuPoint(6), 0);
                require(mc.screen instanceof github.com.gengyoubo.tcmodern.lib.client.book.edit.ResearchEditorScreen, "Edit did not open the editor");
                stage = 17;
            } else if (stage == 17 && ++frames > WAIT) {
                var editor = (github.com.gengyoubo.tcmodern.lib.client.book.edit.ResearchEditorScreen) mc.screen;
                var form = editor.current();
                require(NOTE.equals(form.key), "Editor opened " + form.key);
                form.name.zh = "测试笔记研究（已编辑）";
                editor.load(form);
                editor.submit();
                stage = 18;
            } else if (stage == 18 && ++frames > WAIT * 2) {
                var screen = book(mc);
                require("测试笔记研究（已编辑）".equals(TcThaumonomicon.name(api(), NOTE)), "Edit did not apply: " + TcThaumonomicon.name(api(), NOTE));
                proof.put("editExistingResearch", true);
                // Delete the created research: menu, then the confirmation.
                click(screen, screen.nodeCenter("TEST_NEW"), 1);
                click(screen, screen.menuPoint(7), 0);
                click(screen, screen.menuPoint(0), 0);
                stage = 19;
            } else if (stage == 19 && ++frames > WAIT * 2) {
                var screen = book(mc);
                require(screen.snapshot().stream().flatMap(c -> c.entries().stream()).noneMatch(e -> e.key().equals("TEST_NEW")), "Deleted research is still there");
                require(!Files.exists(fixture("test_new")), "Deleted research file still exists");
                proof.put("editDeleteResearch", true);
                click(screen, screen.cellCenter(6, 2), 1);
                click(screen, screen.menuPoint(1), 0);
                require(mc.screen instanceof github.com.gengyoubo.tcmodern.lib.client.book.edit.CategoryEditorScreen, "New category did not open the editor");
                var editor = (github.com.gengyoubo.tcmodern.lib.client.book.edit.CategoryEditorScreen) mc.screen;
                var form = github.com.gengyoubo.tcmodern.lib.client.book.edit.ResearchForm.newCategory();
                form.key = "TESTCAT2";
                form.name.zh = "游戏内新建章节";
                editor.load(form);
                editor.submit();
                stage = 20;
            } else if (stage == 20 && ++frames > WAIT * 2) {
                var screen = book(mc);
                require(screen.snapshot().stream().anyMatch(c -> c.key().equals("TESTCAT2")), "New category missing");
                require("游戏内新建章节".equals(TcThaumonomicon.categoryName(api(), "TESTCAT2")), "New category name missing");
                proof.put("editCreateCategory", true);
                screen.select(CATEGORY);
                screenshot(mc, "edit-map", 0, 0);
                finish(mc, null);
            }
        } catch (Exception | AssertionError failure) { finish(mc, failure); }
    }

    private static github.com.gengyoubo.tcmodern.api.research.TcResearchEntry entry(ThaumonomiconScreen screen, String key) {
        return screen.snapshot().stream().flatMap(c -> c.entries().stream()).filter(e -> e.key().equals(key)).findFirst()
                .orElseThrow(() -> new IllegalStateException("No research " + key));
    }

    private void seed(Minecraft mc) {
        var player = player(mc);
        var amounts = new LinkedHashMap<String, Integer>();
        for (var aspect : TcAspects.snapshot(api())) amounts.put(aspect.tag(), 50);
        TcPlayerAspects.give(api(), player, amounts);
        player.getInventory().clearContent();
        player.getInventory().selected = 0;
        player.getInventory().setItem(0, host("Thaumcraft:ItemThaumonomicon"));
        player.getInventory().setItem(1, host("Thaumcraft:ItemInkwell"));
        player.getInventory().setItem(2, new ItemStack(Items.PAPER, 4));
    }

    private static ItemStack host(String key) {
        return api().mappings().toHost(LegacyMappings.ITEM_STACK, api().stack(new LegacyStack(key, 0, 1, "")), ItemStack.class).orElseThrow();
    }

    /** A test category with a root, a secondary child, a primary that needs a note and a hidden research. */
    private static void writeFixtures(String rootName) throws Exception {
        Path root = FMLPaths.CONFIGDIR.get().resolve("tc4modern").resolve("tc4_research");
        Path folder = root.resolve("tc4test");
        // Files the editing stages create must not survive into the next run.
        for (Path stale : List.of(folder.resolve("test_new.json"), root.resolve("custom").resolve("category_testcat2.json"))) Files.deleteIfExists(stale);
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("category.json"), """
                {"type": "tc4modern:category", "key": "TC4TEST", "icon": "thaumcraft:textures/misc/r_thaumaturgy.png",
                 "name": {"zh_CN": "测试章节", "en_US": "Test chapter"}}""", StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("root.json"), """
                {"type": "tc4modern:research", "key": "TEST_ROOT", "category": "TC4TEST", "position": [0, 0],
                 "icon": {"legacy_item": "Thaumcraft:ItemThaumonomicon"}, "aspects": {"cognitio": 2, "ordo": 1},
                 "name": {"zh_CN": "%s", "en_US": "Test root"}, "subtitle": {"zh_CN": "由 JSON 定义", "en_US": "Defined in JSON"},
                 "pages": [
                   {"text": {"zh_CN": "这是第一页，来自 JSON 文件。<BR>魔导手册会按原版标记换行。<LINE>分隔线之后的一段文字，用来检查自动换行是否正常工作。", "en_US": "Page one."}},
                   {"aspects": {"cognitio": 2, "ordo": 1}},
                   {"arcane": {"legacy_item": "Thaumcraft:WandCap", "metadata": 1}},
                   {"crafting": {"legacy_item": "minecraft:chest"}},
                   {"smelting": {"legacy_item": "minecraft:iron_ore"}},
                   {"text": {"zh_CN": "完成子研究后才可见的一页。"}, "requires": "TEST_CHILD"}]}""".formatted(rootName), StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("child.json"), """
                {"type": "tc4modern:research", "key": "TEST_CHILD", "category": "TC4TEST", "position": [2, 0],
                 "icon": {"item": "minecraft:diamond"}, "aspects": {"aer": 2}, "parents": ["TEST_ROOT"], "flags": ["secondary"],
                 "name": {"zh_CN": "测试子研究"}}""", StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("note.json"), """
                {"type": "tc4modern:research", "key": "TEST_NOTE", "category": "TC4TEST", "position": [2, 2],
                 "icon": {"legacy_item": "Thaumcraft:ItemInkwell"}, "aspects": {"ordo": 2, "ignis": 1}, "parents": ["TEST_ROOT"],
                 "name": {"zh_CN": "测试笔记研究"}}""", StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("hidden.json"), """
                {"type": "tc4modern:research", "key": "TEST_HIDDEN", "category": "TC4TEST", "position": [0, 2],
                 "icon": {"item": "minecraft:ender_eye"}, "aspects": {"tenebrae": 1}, "flags": ["hidden"],
                 "triggers": {"aspects": ["tenebrae"]}, "name": {"zh_CN": "测试隐藏研究"}}""", StandardCharsets.UTF_8);
    }

    private static Path fixture(String name) {
        return FMLPaths.CONFIGDIR.get().resolve("tc4modern").resolve("tc4_research").resolve("tc4test").resolve(name + ".json");
    }

    private static void press(ThaumonomiconScreen screen, float[] point) {
        require(point != null, "Press target not on screen");
        screen.mouseClicked(point[0] * screen.canvasScale(), point[1] * screen.canvasScale(), 0);
    }

    private static void drag(ThaumonomiconScreen screen, float[] point) {
        screen.mouseDragged(point[0] * screen.canvasScale(), point[1] * screen.canvasScale(), 0, 0, 0);
    }

    private static void release(ThaumonomiconScreen screen, float[] point) {
        screen.mouseReleased(point[0] * screen.canvasScale(), point[1] * screen.canvasScale(), 0);
    }

    private static void click(ThaumonomiconScreen screen, float[] point, int button) {
        require(point != null, "Click target not on screen");
        float scale = screen.canvasScale();
        screen.mouseClicked(point[0] * scale, point[1] * scale, button);
        screen.mouseReleased(point[0] * scale, point[1] * scale, button);
    }

    /** Offscreen at a fixed 480x270 GUI size; mouse in canvas coordinates of the current layout. */
    private void screenshot(Minecraft mc, String name, int mouseX, int mouseY) throws Exception {
        int width = 480, height = 270, pixels = 2;
        var screen = mc.screen;
        var book = screen instanceof ThaumonomiconScreen open ? open : null;
        int dx = book == null ? 0 : mouseX - book.canvasLeft(), dy = book == null ? 0 : mouseY - book.canvasTop();
        var target = new com.mojang.blaze3d.pipeline.TextureTarget(width * pixels, height * pixels, true, Minecraft.ON_OSX);
        screen.resize(mc, width, height);
        int mx = book == null || mouseX == 0 && mouseY == 0 ? 0 : Math.round((book.canvasLeft() + dx) * book.canvasScale());
        int my = book == null || mouseX == 0 && mouseY == 0 ? 0 : Math.round((book.canvasTop() + dy) * book.canvasScale());
        target.setClearColor(0.12F, 0.16F, 0.12F, 1);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        com.mojang.blaze3d.systems.RenderSystem.backupProjectionMatrix();
        com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(new org.joml.Matrix4f().setOrtho(0, width, height, 0, 1000, 3000),
                com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
        var modelView = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        modelView.translate(0, 0, -2000);
        com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
        try {
            var graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            graphics.fill(0, 0, width, height, 0xff1e281e);
            net.minecraftforge.client.ForgeHooksClient.drawScreen(screen, graphics, mx, my, 0);
            graphics.flush();
            var path = mc.gameDirectory.toPath().resolve("screenshots/thaumonomicon-" + name + ".png");
            Files.createDirectories(path.getParent());
            try (var image = net.minecraft.client.Screenshot.takeScreenshot(target)) { image.writeToFile(path); }
        } finally {
            modelView.popPose();
            com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
            com.mojang.blaze3d.systems.RenderSystem.restoreProjectionMatrix();
            target.unbindWrite();
            target.destroyBuffers();
            mc.getMainRenderTarget().bindWrite(true);
            screen.resize(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        }
    }

    private void finish(Minecraft mc, Throwable failure) {
        stage = 99;
        proof.put("status", failure == null ? "PASS" : "FAIL");
        if (failure != null) {
            proof.put("stage", lastStage);
            proof.put("failure", failure.toString());
            LogUtils.getLogger().error("TCMODERNLIB_BOOK_SMOKE_FAIL", failure);
        }
        try {
            var file = mc.gameDirectory.toPath().resolve("logs/tcmodernlib/book-smoke.json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
        LogUtils.getLogger().info("TCMODERNLIB_BOOK_SMOKE_RESULT: {}", proof);
        if (mc.level != null) { mc.level.disconnect(); mc.clearLevel(); }
        mc.stop();
    }
}
