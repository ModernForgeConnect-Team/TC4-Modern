package github.com.gengyoubo.tcmodern.lib.client.book;

import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.research.TcResearchCategory;
import github.com.gengyoubo.tcmodern.api.research.TcThaumonomicon;
import github.com.gengyoubo.tcmodern.api.research.TcBookAccessors;
import github.com.gengyoubo.tcmodern.lib.TcModernLib;
import github.com.gengyoubo.tcmodern.lib.book.BookNetwork;
import github.com.gengyoubo.tcmodern.lib.json.ResearchLoader;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import github.com.gengyoubo.tcmodern.lib.book.BookLogic;
import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.mla.LegacyField;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import net.minecraft.client.gui.screens.Screen;

/**
 * Keeps the editor's research snapshot current and adds its entry point to the original hosted Thaumonomicon.
 */
public final class BookClient {
    private static List<TcResearchCategory> book;

    private BookClient() {}

    /** The registry snapshot the book draws; read once and again after a reload. */
    public static synchronized List<TcResearchCategory> book() {
        if (book == null) book = TcModernApi.runtime().map(TcThaumonomicon::snapshot).orElse(List.of());
        return book;
    }

    static synchronized void invalidate() { book = null; }

    public static void open() { Minecraft.getInstance().setScreen(new ThaumonomiconScreen()); }

    /** Replaces only research progress, preserving aspect pools, scans and warp. */
    public static void research(List<String> completed) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        TcModernApi.runtime().ifPresent(runtime -> {
            String name = mc.player.getGameProfile().getName();
            var values = runtime.values();
            var knowledge = runtime.accessor(TcBookAccessors.Progress.class).completed(name);
            values.clear(knowledge);
            completed.forEach(key -> values.add(knowledge, key));
            values.put(runtime.accessor(TcBookAccessors.BrowserProgress.class).completed(), name, values.newList(completed));
        });
        if (mc.screen instanceof ThaumonomiconScreen screen) screen.refresh();
    }

    /** True while the loaded JSON research is a remote server's rather than this game's own files. */
    private static boolean remote;

    /**
     * The server's JSON research arrived, on login or after a reload. A remote client switches to exactly the
     * server's files; with an integrated server the registry is shared and already current.
     */
    public static void reloaded(BookNetwork.Reloaded result) {
        var mc = Minecraft.getInstance();
        if (!mc.isLocalServer()) {
            TcModernApi.runtime().ifPresent(runtime -> ResearchLoader.reloadFrom(runtime, result.contents()));
            remote = true;
        }
        invalidate();
        if (result.announce() && mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("book.tcmodernlib.reloaded", result.categories(), result.research(),
                    result.errors().size()).withStyle(result.errors().isEmpty() ? ChatFormatting.GREEN : ChatFormatting.GOLD), false);
            result.errors().stream().limit(5).forEach(error -> mc.player.displayClientMessage(Component.literal(error).withStyle(ChatFormatting.RED), false));
        }
        if (mc.screen instanceof ThaumonomiconScreen screen) screen.refresh();
    }

    /** Leaving a remote server brings back this game's own JSON research. */
    @Mod.EventBusSubscriber(modid = TcModernLib.MOD_ID, value = Dist.CLIENT)
    public static final class Session {
        private Session() {}

        @SubscribeEvent public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            Opening.reset();
            if (!remote) return;
            remote = false;
            TcModernApi.runtime().ifPresent(ResearchLoader::reload);
            invalidate();
        }
    }

    /**
     * The original book item still runs its own use on both sides (sibling research, the cheat sheet, the research
     * sync). MFCL shows that original browser in its own host screen; the original browser gets an operator-only
     * entry to the editor, including browsers recreated when returning from a research page. The hosted screen itself
     * remains responsible for all ordinary browsing and research actions.
     */
    @Mod.EventBusSubscriber(modid = TcModernLib.MOD_ID, value = Dist.CLIENT)
    public static final class Opening {
        private static final String HOST_SCREEN = "github.com.gengyoubo.mfcl.client.ClientContainerBridge$HostedScreen";
        private static final String BROWSER = "thaumcraft.client.gui.GuiResearchBrowser";
        private static Screen pendingBrowser, previewPage, returningPage;
        private static ThaumonomiconScreen pendingEditor, previewEditor;
        private static long pendingDeadline;

        @LegacyType("net.minecraft.client.Minecraft")
        public interface ClientFactory {
            @LegacyMethod(name = "func_71410_x", descriptor = "()Lnet/minecraft/client/Minecraft;", isStatic = true)
            LegacyObject instance();
        }

        @LegacyType("net.minecraft.client.Minecraft")
        public interface Client {
            @LegacyField(name = "field_71462_r", descriptor = "Lnet/minecraft/client/gui/GuiScreen;")
            LegacyObject screen();
        }

        @LegacyType("thaumcraft.client.gui.GuiResearchBrowser")
        public interface BrowserSelection {
            @LegacyField(name = "currentHighlight", descriptor = "Lthaumcraft/api/research/ResearchItem;") LegacyObject highlighted();
            @LegacyField(name = "currentHighlight", descriptor = "Lthaumcraft/api/research/ResearchItem;") void highlighted(LegacyObject item);
        }

        /** Preview in TC4 without granting or syncing the selected research. */
        static boolean preview(ThaumonomiconScreen editor, Screen originalBook, String key) {
            var mc = Minecraft.getInstance();
            var runtime = TcModernApi.runtime().orElse(null);
            if (runtime == null || originalBook == null || mc.player == null || !mc.player.hasPermissions(BookLogic.CHEAT_PERMISSION)) return false;
            try {
                var research = runtime.accessor(github.com.gengyoubo.tcmodern.api.research.TcResearchAccessors.Categories.class).find(key);
                if (research == null) return false;
                mc.setScreen(originalBook);
                var client = runtime.accessor(ClientFactory.class).instance();
                var old = runtime.accessor(Client.class, client).screen();
                if (old == null || !BROWSER.equals(old.typeName())) { mc.setScreen(editor); return false; }
                var selected = runtime.accessor(BrowserSelection.class, old);
                var previous = selected.highlighted();
                var values = runtime.values();
                var browser = runtime.accessor(TcBookAccessors.BrowserProgress.class).completed();
                String name = mc.player.getGameProfile().getName();
                Object existing = values.map(browser).get(name);
                if (!(existing instanceof LegacyObject completed)) { mc.setScreen(editor); return false; }
                boolean hadResearch = values.list(completed).contains(key);
                var originalPages = runtime.accessor(TcBookAccessors.Research.class, research).pages();
                var builder = runtime.accessor(TcBookAccessors.ResearchBuilder.class, research);
                try {
                    if (!hadResearch) values.add(completed, key);
                    if (originalPages == null || originalPages.length == 0) {
                        var blank = runtime.accessor(TcBookAccessors.TextPages.class).text("");
                        builder.pages(new LegacyObject[] { blank });
                    }
                    selected.highlighted(research);
                    pendingBrowser = originalBook; pendingEditor = editor;
                    pendingDeadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
                    if (!originalBook.mouseClicked(0, 0, 0)) throw new IllegalStateException("Original browser did not handle the preview click");
                    originalBook.mouseReleased(0, 0, 0);
                } finally {
                    selected.highlighted(previous);
                    if (!hadResearch) values.remove(completed, key);
                    if (originalPages == null || originalPages.length == 0) builder.pages(originalPages);
                }
                return true;
            } catch (RuntimeException failure) {
                pendingBrowser = null; pendingEditor = null; pendingDeadline = 0;
                com.mojang.logging.LogUtils.getLogger().warn("Could not open original research page {}", key, failure);
                mc.setScreen(editor);
                return false;
            }
        }

        @SubscribeEvent public static void opening(ScreenEvent.Opening event) {
            var current = event.getCurrentScreen();
            var next = event.getNewScreen();
            if (current == pendingBrowser && pendingEditor != null && next != null && next.getClass().getName().equals(HOST_SCREEN)) {
                previewPage = next; previewEditor = pendingEditor;
                pendingBrowser = null; pendingEditor = null; pendingDeadline = 0;
            } else if (previewPage != null && current == previewPage) {
                if (returningPage == current && next != null && next.getClass().getName().equals(HOST_SCREEN)
                        && Minecraft.getInstance().player != null && Minecraft.getInstance().player.hasPermissions(BookLogic.CHEAT_PERMISSION)) {
                    event.setNewScreen(previewEditor);
                    previewPage = null; previewEditor = null; returningPage = null;
                } else if (next != null && next.getClass().getName().equals(HOST_SCREEN)) {
                    previewPage = next; returningPage = null;
                } else { previewPage = null; previewEditor = null; returningPage = null; }
            }
        }

        @SubscribeEvent public static void key(ScreenEvent.KeyPressed.Pre event) {
            if (event.getScreen() == previewPage && (event.getKeyCode() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
                    || Minecraft.getInstance().options.keyInventory.matches(event.getKeyCode(), event.getScanCode()))) returningPage = previewPage;
        }

        @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
            if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || pendingEditor == null || System.nanoTime() < pendingDeadline) return;
            var editor = pendingEditor;
            boolean stillOnBrowser = Minecraft.getInstance().screen == pendingBrowser;
            pendingBrowser = null; pendingEditor = null; pendingDeadline = 0;
            if (stillOnBrowser) { Minecraft.getInstance().setScreen(editor); editor.previewFailed(); }
        }

        private Opening() {}

        private static void reset() {
            pendingBrowser = null; previewPage = null; returningPage = null;
            pendingEditor = null; previewEditor = null;
            pendingDeadline = 0;
        }

        static boolean editable(Screen screen) {
            var player = Minecraft.getInstance().player;
            if (screen == null || !screen.getClass().getName().equals(HOST_SCREEN) || player == null
                    || !player.hasPermissions(BookLogic.CHEAT_PERMISSION)) return false;
            return TcModernApi.runtime().map(runtime -> {
                try {
                    var client = runtime.accessor(ClientFactory.class).instance();
                    var original = runtime.accessor(Client.class, client).screen();
                    return original != null && BROWSER.equals(original.typeName());
                } catch (RuntimeException unavailable) { return false; }
            }).orElse(false);
        }

        @SubscribeEvent(priority = EventPriority.LOWEST) public static void render(ScreenEvent.Render.Post event) {
            if (!editable(event.getScreen())) return;
            var label = Component.translatable("book.tcmodernlib.cheat.off");
            var font = Minecraft.getInstance().font;
            int x = event.getScreen().width - font.width(label) - 18;
            event.getGuiGraphics().fill(x, 4, event.getScreen().width - 4, 22, 0xD020182C);
            event.getGuiGraphics().drawString(font, label, x + 7, 9, 0xFFFFFFFF);
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST) public static void click(ScreenEvent.MouseButtonPressed.Pre event) {
            var screen = event.getScreen();
            if (!editable(screen) || event.getButton() != 0) return;
            int x = screen.width - Minecraft.getInstance().font.width(Component.translatable("book.tcmodernlib.cheat.off")) - 18;
            if (event.getMouseX() < x || event.getMouseX() >= screen.width - 4 || event.getMouseY() < 4 || event.getMouseY() >= 22) return;
            event.setCanceled(true);
            Minecraft.getInstance().setScreen(new ThaumonomiconScreen(screen));
        }

    }
}
