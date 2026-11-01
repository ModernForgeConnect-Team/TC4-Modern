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
import github.com.gengyoubo.tcmodern.api.research.TcBookAccessors;
import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.mfcl.api.mla.LegacyField;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
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
    @LegacyType("thaumcraft.client.gui.GuiResearchBrowser")
    public interface BrowserProbe {
        @LegacyField(name = "research", descriptor = "Ljava/util/LinkedList;") LegacyObject research();
        @LegacyField(name = "field_74117_m", descriptor = "D") double mapX();
        @LegacyField(name = "field_74115_n", descriptor = "D") double mapY();
        @LegacyField(name = "guiMapTop", descriptor = "I", isStatic = true) int top();
        @LegacyField(name = "guiMapBottom", descriptor = "I", isStatic = true) int bottom();
        @LegacyField(name = "guiMapLeft", descriptor = "I", isStatic = true) int left();
        @LegacyField(name = "guiMapRight", descriptor = "I", isStatic = true) int right();
        @LegacyField(name = "hasScribestuff", descriptor = "Z") void hasScribestuff(boolean available);
    }
    @LegacyType("thaumcraft.client.gui.GuiResearchRecipe")
    public interface RecipeProbe {
        @LegacyField(name = "research", descriptor = "Lthaumcraft/api/research/ResearchItem;") LegacyObject research();
    }
    @LegacyType("thaumcraft.common.lib.research.ResearchManager")
    public interface NoteManagerProbe {
        @LegacyMethod(name = "getData", descriptor = "(Lnet/minecraft/item/ItemStack;)Lthaumcraft/common/lib/research/ResearchNoteData;", isStatic = true)
        LegacyObject data(LegacyObject stack);
    }
    @LegacyType("thaumcraft.common.lib.research.ResearchNoteData")
    public interface NoteDataProbe {
        @LegacyField(name = "hexEntries", descriptor = "Ljava/util/HashMap;") LegacyObject entries();
    }
    @LegacyType("thaumcraft.common.lib.research.ResearchManager$HexEntry")
    public interface NoteHexProbe {
        @LegacyField(name = "aspect", descriptor = "Lthaumcraft/api/aspects/Aspect;") LegacyObject aspect();
        @LegacyField(name = "type", descriptor = "I") int type();
    }
    private static final int WAIT = 14;
    private static final String CATEGORY = "TC4TEST", ROOT = "TEST_ROOT", CHILD = "TEST_CHILD", NOTE = "TEST_NOTE", HIDDEN = "TEST_HIDDEN";
    private final long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(6);
    private final Map<String, Object> proof = new LinkedHashMap<>();
    private CompletableFuture<?> pending;
    private net.minecraft.client.gui.screens.Screen originalBrowser;
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
                require(category != null && category.entries().size() == 6, "JSON category did not load: " + ResearchLoader.last());
                require(ResearchLoader.last().errors().isEmpty(), "JSON research reported errors: " + ResearchLoader.last().errors());
                require("测试根研究".equals(TcThaumonomicon.name(api(), ROOT)), "Inline name not published: " + TcThaumonomicon.name(api(), ROOT));
                var root = category.entries().stream().filter(e -> e.key().equals(ROOT)).findFirst().orElseThrow();
                var splitNote = category.entries().stream().filter(e -> e.key().equals(NOTE)).findFirst().orElseThrow();
                require(splitNote.aspects().isEmpty() && splitNote.noteAspects().keySet().equals(Set.of("aqua", "terra")),
                        "Independent note aspects or empty cost did not register: " + splitNote);
                require(root.pages().size() == 6, "Root pages: " + root.pages());
                var originalIcon = root.iconItem();
                require(originalIcon != null, "Modern research icon missing from registry");
                var snapshotClass = Class.forName("github.com.gengyoubo.mfcl.bridge.LegacyClientData$Stack");
                var snapshot = snapshotClass.getConstructor(String.class, int.class, int.class, String.class, String.class)
                        .newInstance(originalIcon.key(), originalIcon.metadata(), originalIcon.count(), "", originalIcon.snbt());
                var mapping = Class.forName("github.com.gengyoubo.mfcl.client.ModernItemMapping");
                var renderedIcon = (ItemStack) mapping.getMethod("renderStack", snapshotClass).invoke(null, snapshot);
                require("tc4modern:research_desk".equals(String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(renderedIcon.getItem()))),
                        "Original GUI did not map the modern research desk icon: " + renderedIcon);
                proof.put("originalGuiModernResearchIcon", true);
                var completed = TcThaumonomicon.completed(api(), mc.player.getGameProfile().getName());
                require(completed.containsAll(List.of("TEST_AUTO_ZERO", "TEST_AUTO_ONE")), "Zero/one-aspect research did not auto-unlock after reload: " + completed);
                require(!root.has(github.com.gengyoubo.tcmodern.api.research.TcResearchEntry.Flag.AUTO_UNLOCK)
                        && !category.entries().stream().filter(e -> e.key().equals(CHILD)).findFirst().orElseThrow().has(github.com.gengyoubo.tcmodern.api.research.TcResearchEntry.Flag.AUTO_UNLOCK),
                        "Research with two distinct aspects was registered as automatic");
                proof.put("zeroAndOneAspectAutoUnlockAfterReload", true);
                proof.put("jsonCategoryAndResearchLoadedByDebugReload", true);
                proof.put("rootPageKinds", root.pages().stream().map(p -> p.getClass().getSimpleName()).toList());
                // Fixture files may have been loaded at startup from an older smoke run with one-aspect children.
                // Clear only test progress after the current two-aspect definitions have replaced those files.
                pending = server(mc, () -> {
                    var actor = player(mc);
                    var result = TcResearch.revokeAndSnapshot(api(), actor, List.of(ROOT, CHILD, NOTE, HIDDEN));
                    github.com.gengyoubo.tcmodern.lib.book.BookNetwork.research(actor, result.completed());
                    require(TcResearch.isComplete(api(), actor, "TEST_AUTO_ZERO") && TcResearch.isComplete(api(), actor, "TEST_AUTO_ONE"),
                            "Automatic research was not persisted on the server");
                    require(!TcResearch.isComplete(api(), actor, ROOT) && !TcResearch.isComplete(api(), actor, CHILD),
                            "Two-aspect research was automatically unlocked on the server");
                });
                stage = 40;
            } else if (stage == 40 && pending.isDone() && ++frames > WAIT) {
                var completed = TcThaumonomicon.completed(api(), mc.player.getGameProfile().getName());
                require(!completed.contains(ROOT) && !completed.contains(CHILD), "Two-aspect research did not stay locked: " + completed);
                // Open the original hosted book, then exercise its operator entry point.
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                stage = 5;
            } else if (stage == 5 && mc.screen != null && mc.screen.getClass().getName().equals("github.com.gengyoubo.mfcl.client.ClientContainerBridge$HostedScreen") && ++frames > WAIT) {
                require(BookClient.Opening.editable(mc.screen), "Editor entry missing from the original browser");
                openOriginalResearch(mc);
                stage = 51;
            } else if (stage == 51 && "thaumcraft.client.gui.GuiResearchRecipe".equals(originalScreenType()) && ++frames > WAIT) {
                require(!BookClient.Opening.editable(mc.screen), "Editor entry must not overlay a research page");
                mc.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                stage = 52;
            } else if (stage == 52 && "thaumcraft.client.gui.GuiResearchBrowser".equals(originalScreenType()) && ++frames > WAIT) {
                require(BookClient.Opening.editable(mc.screen), "Editor entry missing after returning from a research page");
                proof.put("editorEntryReturnsAfterOriginalResearch", true);
                stage = 53;
            } else if (stage == 53 && ++frames > WAIT) {
                var original = mc.screen;
                originalBrowser = original;
                int x = original.width - mc.font.width(net.minecraft.network.chat.Component.translatable("book.tcmodernlib.cheat.off")) - 10;
                var click = new net.minecraftforge.client.event.ScreenEvent.MouseButtonPressed.Pre(original, x, 10, 0);
                MinecraftForge.EVENT_BUS.post(click);
                require(click.isCanceled() && mc.screen instanceof ThaumonomiconScreen, "Editor entry did not open");
                mc.screen.onClose();
                require(mc.screen == original, "Editor did not return to the original book");
                proof.put("originalBookAndEditorEntry", true);
                var form = github.com.gengyoubo.tcmodern.lib.client.book.edit.ResearchForm.newResearch(CATEGORY, 0, 0);
                form.pages.add(new github.com.gengyoubo.tcmodern.lib.client.book.edit.ResearchForm.Page());
                var editor = new github.com.gengyoubo.tcmodern.lib.client.book.edit.ResearchEditorScreen(original, "", form);
                mc.setScreen(editor);
                editor.openTextWriter();
                var writer = mc.screen;
                var fields = writer.children().stream().filter(e -> e instanceof net.minecraft.client.gui.components.MultiLineEditBox)
                        .map(e -> (net.minecraft.client.gui.components.MultiLineEditBox) e).toList();
                require(fields.size() == 2, "Book spread must expose both text pages");
                fields.get(0).setValue("第一页\n第二行");
                fields.get(1).setValue("另一页");
                var insertTexture = writer.children().stream().filter(e -> e instanceof net.minecraft.client.gui.components.Button b
                        && b.getMessage().getString().equals(net.minecraft.network.chat.Component.translatable("book.tcmodernlib.writer.insert_texture").getString()))
                        .map(e -> (net.minecraft.client.gui.components.Button) e).findFirst().orElseThrow();
                insertTexture.onPress();
                var picker = mc.screen;
                var search = picker.children().stream().filter(e -> e instanceof net.minecraft.client.gui.components.EditBox)
                        .map(e -> (net.minecraft.client.gui.components.EditBox) e).findFirst().orElseThrow();
                search.setValue("thaumcraft:textures/items/thaumonomicon.png");
                require(picker.mouseClicked(picker.width / 2, 62, 0), "Texture picker did not select a resource");
                require(mc.screen == writer, "Texture picker did not return to the book writer");
                var insertItem = writer.children().stream().filter(e -> e instanceof net.minecraft.client.gui.components.Button b
                        && b.getMessage().getString().equals(net.minecraft.network.chat.Component.translatable("book.tcmodernlib.writer.insert_item").getString()))
                        .map(e -> (net.minecraft.client.gui.components.Button) e).findFirst().orElseThrow();
                insertItem.onPress();
                picker = mc.screen;
                search = picker.children().stream().filter(e -> e instanceof net.minecraft.client.gui.components.EditBox)
                        .map(e -> (net.minecraft.client.gui.components.EditBox) e).findFirst().orElseThrow();
                search.setValue("minecraft:nether_star");
                int columns = Math.max(1, Math.min(15, (picker.width - 30) / 20));
                require(picker.mouseClicked((picker.width - columns * 20) / 2 + 1, 62, 0), "Item picker did not select an item");
                require(mc.screen == writer, "Item picker did not return to the book writer");
                fields = writer.children().stream().filter(e -> e instanceof net.minecraft.client.gui.components.MultiLineEditBox)
                        .map(e -> (net.minecraft.client.gui.components.MultiLineEditBox) e).toList();
                String beforeCancel = fields.get(0).getValue();
                insertTexture = writer.children().stream().filter(e -> e instanceof net.minecraft.client.gui.components.Button b
                        && b.getMessage().getString().equals(net.minecraft.network.chat.Component.translatable("book.tcmodernlib.writer.insert_texture").getString()))
                        .map(e -> (net.minecraft.client.gui.components.Button) e).findFirst().orElseThrow();
                insertTexture.onPress();
                mc.screen.onClose();
                fields = writer.children().stream().filter(e -> e instanceof net.minecraft.client.gui.components.MultiLineEditBox)
                        .map(e -> (net.minecraft.client.gui.components.MultiLineEditBox) e).toList();
                require(mc.screen == writer && beforeCancel.equals(fields.get(0).getValue()), "Cancelling insertion changed the draft");
                screenshot(mc, "writer", 0, 0);
                writer.onClose();
                String written = form.pages.get(0).text.get("zh_cn");
                require(mc.screen == editor && written.startsWith("第一页<BR>第二行")
                        && written.contains("<IMG>thaumcraft:textures/items/thaumonomicon.png:0:0:255:255:0.0625</IMG>")
                        && written.contains("<IMG>minecraft:textures/item/nether_star.png:0:0:255:255:0.0625</IMG>")
                        && "另一页".equals(form.pages.get(1).text.get("zh_cn")), "Book drafts did not return to editor: " + written);
                proof.put("bookWritingAndNewlines", true);
                proof.put("bookInlineTextureAndItem", true);
                editor.openTextWriter();
                writer = mc.screen;
                var buttons = writer.children().stream().filter(e -> e instanceof net.minecraft.client.gui.components.Button)
                        .map(e -> (net.minecraft.client.gui.components.Button) e).toList();
                buttons.get(2).onPress(); // Text -> image on the first page.
                require(form.pages.get(0).type.equals("text"), "Draft changed the research before confirming");
                var edits = writer.children().stream().filter(e -> e instanceof net.minecraft.client.gui.components.EditBox)
                        .map(e -> (net.minecraft.client.gui.components.EditBox) e).toList();
                edits.get(0).setValue("thaumcraft:textures/items/thaumonomicon.png");
                buttons = writer.children().stream().filter(e -> e instanceof net.minecraft.client.gui.components.Button)
                        .map(e -> (net.minecraft.client.gui.components.Button) e).toList();
                buttons.get(4).onPress(); // Caption switches to a translation key.
                edits = writer.children().stream().filter(e -> e instanceof net.minecraft.client.gui.components.EditBox)
                        .map(e -> (net.minecraft.client.gui.components.EditBox) e).toList();
                edits.get(1).setValue("example.book.caption");
                screenshot(mc, "writer-image", 0, 0);
                writer.onClose();
                require(form.pages.get(0).type.equals("image") && form.pages.get(0).caption.isRaw()
                        && "example.book.caption".equals(form.pages.get(0).caption.get("zh_cn")), "Image or translation key not applied");
                proof.put("bookImageAndTranslationKey", true);
                // Continue the existing isolated editor and action checks.
                mc.setScreen(new ThaumonomiconScreen(original));
                ThaumonomiconScreen.cheat = false;
                stage = 50;
            } else if (stage == 50 && mc.screen instanceof ThaumonomiconScreen && ++frames > WAIT) {
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
                click(screen, screen.menuPoint(0), 0);
                stage = 6;
            } else if (stage == 6 && ++frames > WAIT) {
                var screen = book(mc);
                require(screen.completedKeys().contains(ROOT), "Debug complete did not reach the client");
                pending = server(mc, () -> require(TcResearch.isComplete(api(), player(mc), ROOT), "Debug complete did not reach the server"));
                screenshot(mc, "debug", Math.round(screen.nodeCenter(HIDDEN)[0]), Math.round(screen.nodeCenter(HIDDEN)[1]));
                proof.put("debugCompleteSingle", true);
                ThaumonomiconScreen.cheat = false;
                screen.refresh();
                airBefore = screen.pool("aer");
                // Normal mode: the secondary child is bought with points.
                clickOriginalResearch(mc, screen, CHILD);
                stage = 7;
            } else if (stage == 7 && pending.isDone() && ++frames > WAIT) {
                var screen = book(mc);
                require(!screen.cheating(), "Debug mode did not switch off");
                require(screen.completedKeys().contains(CHILD), "Buying the secondary research failed");
                require(screen.pool("aer") == airBefore - 2, "Buying did not spend 2 aer: " + airBefore + " -> " + screen.pool("aer"));
                proof.put("buySecondaryResearch", true);
                require(screen.unlockable(entry(screen, NOTE)), "Note prerequisites are not complete");
                clickOriginalResearch(mc, screen, NOTE);
                stage = 8;
            } else if (stage == 8 && ++frames > WAIT) {
                pending = server(mc, () -> {
                    var player = player(mc);
                    boolean note = false;
                    for (var stack : player.getInventory().items) {
                        if (stack.isEmpty()) continue;
                        var legacy = api().mappings().toLegacy(LegacyMappings.ITEM_STACK, stack).orElse(null);
                        if (legacy != null && api().snapshot(legacy).key().equals("Thaumcraft:ItemResearchNotes")) {
                            var data = api().accessor(NoteManagerProbe.class).data(legacy);
                            var entries = api().values().map(api().accessor(NoteDataProbe.class, data).entries());
                            var fixed = new HashSet<String>();
                            for (Object value : entries.values()) {
                                var hex = api().accessor(NoteHexProbe.class, (LegacyObject) value);
                                if (hex.type() == 1 && hex.aspect() != null)
                                    fixed.add(api().accessor(github.com.gengyoubo.tcmodern.api.aspect.TcAspectAccessors.Aspect.class, hex.aspect()).tag());
                            }
                            require(fixed.equals(Set.of("aqua", "terra")), "Original note used wrong fixed aspects: " + fixed);
                            note = true;
                        }
                    }
                    require(note, "Clicking a primary research did not write a research note");
                });
                stage = 9;
            } else if (stage == 9 && pending.isDone() && ++frames > 2) {
                proof.put("primaryResearchWritesNote", true);
                var screen = book(mc);
                ThaumonomiconScreen.cheat = true;
                screen.refresh();
                click(screen, screen.nodeCenter(NOTE), 0);
                stage = 90;
            } else if (stage == 90 && "thaumcraft.client.gui.GuiResearchRecipe".equals(originalScreenType()) && ++frames > WAIT) {
                var selected = api().accessor(RecipeProbe.class, originalScreen()).research();
                require(NOTE.equals(api().accessor(TcBookAccessors.Research.class, selected).key()), "Original page opened the wrong research");
                require(!TcThaumonomicon.completed(api(), mc.player.getGameProfile().getName()).contains(NOTE), "Preview unlocked research on the client");
                pending = server(mc, () -> require(!TcResearch.isComplete(api(), player(mc), NOTE), "Preview unlocked research on the server"));
                screenshot(mc, "original-preview", 0, 0);
                MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.KeyPressed.Pre(mc.screen, GLFW.GLFW_KEY_ESCAPE, 0, 0));
                mc.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                stage = 91;
            } else if (stage == 91 && mc.screen instanceof ThaumonomiconScreen && pending.isDone() && ++frames > WAIT) {
                var screen = book(mc);
                require(screen.cheating() && !screen.completedKeys().contains(NOTE), "Preview did not return to editing or changed progress");
                proof.put("originalResearchPreviewWithoutUnlock", true);
                click(screen, screen.nodeCenter(ROOT), 0);
                stage = 92;
            } else if (stage == 92 && "thaumcraft.client.gui.GuiResearchRecipe".equals(originalScreenType()) && ++frames > WAIT) {
                var selected = api().accessor(RecipeProbe.class, originalScreen()).research();
                require(ROOT.equals(api().accessor(TcBookAccessors.Research.class, selected).key()), "Original completed page opened the wrong research");
                screenshot(mc, "original-preview-completed", 0, 0);
                MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.KeyPressed.Pre(mc.screen, GLFW.GLFW_KEY_ESCAPE, 0, 0));
                mc.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                stage = 93;
            } else if (stage == 93 && mc.screen instanceof ThaumonomiconScreen && ++frames > WAIT) {
                var screen = book(mc);
                require(screen.cheating() && screen.completedKeys().contains(ROOT), "Original completed preview did not return to editing");
                proof.put("originalCompletedResearchPreview", true);
                // Revoke with everything that needs it.
                click(screen, screen.nodeCenter(ROOT), 1);
                click(screen, screen.menuPoint(3), 0);
                stage = 10;
            } else if (stage == 10 && ++frames > WAIT * 3) {
                var screen = book(mc);
                require(!screen.completedKeys().contains(ROOT) && !screen.completedKeys().contains(CHILD), "Revoke with dependents left research: " + screen.completedKeys());
                require(screen.pool("aer") == airBefore - 2, "Research revoke changed the aspect pool");
                pending = server(mc, () -> {
                    require(!TcResearch.isComplete(api(), player(mc), ROOT) && !TcResearch.isComplete(api(), player(mc), CHILD),
                            "Dependent revoke did not persist on the server");
                });
                proof.put("debugRevokeWithDependents", true);
                click(screen, screen.categoryPoint(CATEGORY), 1);
                click(screen, screen.menuPoint(0), 0);
                stage = 11;
            } else if (stage == 11 && pending.isDone() && ++frames > WAIT) {
                var screen = book(mc);
                require(screen.completedKeys().containsAll(List.of(ROOT, CHILD, NOTE, HIDDEN)), "Category complete missed research");
                click(screen, screen.nodeCenter(NOTE), 1);
                click(screen, screen.menuPoint(2), 0);
                stage = 110;
            } else if (stage == 110 && ++frames > WAIT * 2) {
                var screen = book(mc);
                require(!screen.completedKeys().contains(NOTE) && screen.completedKeys().containsAll(List.of(ROOT, CHILD, HIDDEN)),
                        "Single revoke changed the wrong research: " + screen.completedKeys());
                pending = server(mc, () -> {
                    require(!TcResearch.isComplete(api(), player(mc), NOTE) && TcResearch.isComplete(api(), player(mc), ROOT),
                            "Single revoke did not persist on the server");
                });
                proof.put("debugRevokeSingle", true);
                click(screen, screen.categoryPoint(CATEGORY), 1);
                click(screen, screen.menuPoint(1), 0);
                stage = 12;
            } else if (stage == 12 && pending.isDone() && ++frames > WAIT) {
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
                form.name.set("zh_cn", "新建的研究");
                form.name.set("en_us", "Created in game");
                form.aspects = "aer:1";
                form.noteAspects = "aer:1";
                form.parents = "TEST_ROOT";
                form.pages.get(0).text.set("zh_cn", "在游戏里写的页面。");
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
                require(created.has(github.com.gengyoubo.tcmodern.api.research.TcResearchEntry.Flag.AUTO_UNLOCK)
                        && screen.completedKeys().contains("TEST_NEW"), "New one-aspect research was not shown immediately");
                proof.put("editCreateResearch", true);
                // Edit an existing research through its menu ("Edit…" follows copy key).
                click(screen, screen.nodeCenter(NOTE), 1);
                click(screen, screen.menuPoint(5), 0);
                require(mc.screen instanceof github.com.gengyoubo.tcmodern.lib.client.book.edit.ResearchEditorScreen, "Edit did not open the editor");
                stage = 17;
            } else if (stage == 17 && ++frames > WAIT) {
                var editor = (github.com.gengyoubo.tcmodern.lib.client.book.edit.ResearchEditorScreen) mc.screen;
                var form = editor.current();
                require(NOTE.equals(form.key), "Editor opened " + form.key);
                form.name.set("zh_cn", "测试笔记研究（已编辑）");
                editor.load(form);
                editor.submit();
                stage = 18;
            } else if (stage == 18 && ++frames > WAIT * 2) {
                var screen = book(mc);
                require("测试笔记研究（已编辑）".equals(TcThaumonomicon.name(api(), NOTE)), "Edit did not apply: " + TcThaumonomicon.name(api(), NOTE));
                proof.put("editExistingResearch", true);
                // Delete the created research: menu, then the confirmation.
                click(screen, screen.nodeCenter("TEST_NEW"), 1);
                click(screen, screen.menuPoint(6), 0);
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
                form.name.set("zh_cn", "游戏内新建章节");
                editor.load(form);
                editor.submit();
                stage = 20;
            } else if (stage == 20 && ++frames > WAIT * 2) {
                var screen = book(mc);
                require(screen.snapshot().stream().anyMatch(c -> c.key().equals("TESTCAT2")), "New category missing");
                require("游戏内新建章节".equals(TcThaumonomicon.categoryName(api(), "TESTCAT2")), "New category name missing");
                proof.put("editCreateCategory", true);
                for (int i = 0; i < 100 && screen.categoryPoint("TESTCAT2") == null; i++)
                    screen.mouseScrolled((screen.canvasLeft() + ThaumonomiconScreen.SIDE_X + 10) * screen.canvasScale(),
                            (screen.canvasTop() + ThaumonomiconScreen.SIDE_Y + 40) * screen.canvasScale(), -1);
                click(screen, screen.categoryPoint("TESTCAT2"), 1);
                click(screen, screen.menuPoint(4), 0);
                click(screen, screen.menuPoint(0), 0);
                stage = 21;
            } else if (stage == 21 && ++frames > WAIT * 2) {
                var screen = book(mc);
                require(screen.snapshot().stream().noneMatch(c -> c.key().equals("TESTCAT2")), "Deleted category is still present");
                require(!Files.exists(ResearchLoader.configPath("custom:category_testcat2")), "Deleted category file still exists");
                proof.put("editDeleteCategory", true);
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

    private void clickOriginalResearch(Minecraft mc, ThaumonomiconScreen editor, String key) {
        mc.setScreen(originalBrowser);
        var browser = originalScreen();
        var selection = api().accessor(BookClient.Opening.BrowserSelection.class, browser);
        var previous = selection.highlighted();
        try {
            api().accessor(BrowserProbe.class, browser).hasScribestuff(true);
            selection.highlighted(api().accessor(github.com.gengyoubo.tcmodern.api.research.TcResearchAccessors.Categories.class).find(key));
            require(originalBrowser.mouseClicked(0, 0, 0), "Original browser did not handle research " + key);
            originalBrowser.mouseReleased(0, 0, 0);
        } finally {
            selection.highlighted(previous);
            mc.setScreen(editor);
        }
    }

    private static LegacyObject originalScreen() {
        var runtime = api();
        var client = runtime.accessor(BookClient.Opening.ClientFactory.class).instance();
        return runtime.accessor(BookClient.Opening.Client.class, client).screen();
    }

    private static String originalScreenType() {
        var screen = originalScreen();
        return screen == null ? "" : screen.typeName();
    }

    /** Uses the original map's live coordinates and input, including its deferred host-screen transition. */
    private static void openOriginalResearch(Minecraft mc) {
        var runtime = api();
        var browser = runtime.accessor(BrowserProbe.class, originalScreen());
        int mapX = Math.max(browser.top(), Math.min((int) Math.floor(browser.mapX()), browser.bottom() - 1));
        int mapY = Math.max(browser.left(), Math.min((int) Math.floor(browser.mapY()), browser.right() - 1));
        var completed = TcThaumonomicon.completed(runtime, mc.player.getGameProfile().getName());
        for (Object value : runtime.values().list(browser.research())) {
            if (!(value instanceof LegacyObject item)) continue;
            var research = runtime.accessor(TcBookAccessors.Research.class, item);
            if (!completed.contains(research.key()) || research.virtual() || research.pages() == null || research.pages().length == 0) continue;
            int x = research.column() * 24 - mapX, y = research.row() * 24 - mapY;
            if (x < 0 || x > 210 || y < 0 || y > 180) continue;
            x += (mc.screen.width - 256) / 2 + 27;
            y += (mc.screen.height - 230) / 2 + 28;
            var graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            net.minecraftforge.client.ForgeHooksClient.drawScreen(mc.screen, graphics, x, y, 0);
            graphics.flush();
            mc.screen.mouseClicked(x, y, 0);
            mc.screen.mouseReleased(x, y, 0);
            return;
        }
        throw new IllegalStateException("No visible completed research page in original browser");
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
                 "icon": {"item": "tc4modern:research_desk"}, "aspects": {"cognitio": 2, "ordo": 1},
                 "name": {"zh_CN": "%s", "en_US": "Test root"}, "subtitle": {"zh_CN": "由 JSON 定义", "en_US": "Defined in JSON"},
                 "pages": [
                   {"text": {"zh_CN": "这是第一页，来自 JSON 文件。<BR>魔导手册会按原版标记换行。<IMG>thaumcraft:textures/items/thaumonomicon.png:0:0:255:255:0.0625</IMG><IMG>minecraft:textures/item/nether_star.png:0:0:255:255:0.0625</IMG><LINE>分隔线之后的一段文字，用来检查自动换行是否正常工作。", "en_US": "Page one."}},
                   {"aspects": {"cognitio": 2, "ordo": 1}},
                   {"arcane": {"legacy_item": "Thaumcraft:WandCap", "metadata": 1}},
                   {"crafting": {"legacy_item": "minecraft:chest"}},
                   {"smelting": {"legacy_item": "minecraft:iron_ore"}},
                   {"text": {"zh_CN": "完成子研究后才可见的一页。"}, "requires": "TEST_CHILD"}]}""".formatted(rootName), StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("child.json"), """
                {"type": "tc4modern:research", "key": "TEST_CHILD", "category": "TC4TEST", "position": [2, 0],
                 "icon": {"item": "minecraft:diamond"}, "aspects": {"aer": 2, "ordo": 1}, "note_aspects": {"ignis": 1, "aqua": 1}, "parents": ["TEST_ROOT"], "flags": ["secondary"],
                 "name": {"zh_CN": "测试子研究"}}""", StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("note.json"), """
                {"type": "tc4modern:research", "key": "TEST_NOTE", "category": "TC4TEST", "position": [2, 2],
                 "icon": {"legacy_item": "Thaumcraft:ItemInkwell"}, "aspects": {}, "note_aspects": {"aqua": 1, "terra": 1}, "parents": ["TEST_ROOT"],
                 "name": {"zh_CN": "测试笔记研究"}}""", StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("hidden.json"), """
                {"type": "tc4modern:research", "key": "TEST_HIDDEN", "category": "TC4TEST", "position": [0, 2],
                 "icon": {"item": "minecraft:ender_eye"}, "aspects": {"tenebrae": 1, "ordo": 1}, "flags": ["hidden"],
                 "triggers": {"aspects": ["tenebrae"]}, "name": {"zh_CN": "测试隐藏研究"}}""", StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("auto_zero.json"), """
                {"type":"tc4modern:research", "key":"TEST_AUTO_ZERO", "category":"TC4TEST", "position":[4,4],
                 "icon":{"item":"minecraft:book"}, "name":{"zh_CN":"零要素研究"}}""", StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("auto_one.json"), """
                {"type":"tc4modern:research", "key":"TEST_AUTO_ONE", "category":"TC4TEST", "position":[6,4],
                 "icon":{"item":"minecraft:book"}, "aspects":{"aer":5}, "name":{"zh_CN":"单要素研究"}}""", StandardCharsets.UTF_8);
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
        boolean actualSize = name.startsWith("writer") || name.startsWith("original-preview");
        int width = actualSize ? mc.getWindow().getGuiScaledWidth() : 480;
        int height = actualSize ? mc.getWindow().getGuiScaledHeight() : 270;
        int targetWidth = actualSize ? mc.getWindow().getWidth() : width * 2;
        int targetHeight = actualSize ? mc.getWindow().getHeight() : height * 2;
        var screen = mc.screen;
        var book = screen instanceof ThaumonomiconScreen open ? open : null;
        int dx = book == null ? 0 : mouseX - book.canvasLeft(), dy = book == null ? 0 : mouseY - book.canvasTop();
        var target = new com.mojang.blaze3d.pipeline.TextureTarget(targetWidth, targetHeight, true, Minecraft.ON_OSX);
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
