package github.com.gengyoubo.tcmodern.lib.client.book;

import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.research.TcResearchCategory;
import github.com.gengyoubo.tcmodern.api.research.TcThaumonomicon;
import github.com.gengyoubo.tcmodern.lib.TcModernLib;
import github.com.gengyoubo.tcmodern.lib.book.BookNetwork;
import github.com.gengyoubo.tcmodern.lib.json.ResearchLoader;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.gui.screens.Screen;
import github.com.gengyoubo.tcmodern.lib.book.BookLogic;
import java.util.WeakHashMap;

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
            if (!remote) return;
            remote = false;
            TcModernApi.runtime().ifPresent(ResearchLoader::reload);
            invalidate();
        }
    }

    /**
     * The original book item still runs its own use on both sides (sibling research, the cheat sheet, the research
     * sync); only the screen it opens is replaced. MFCL shows that original browser in its own host screen, so the
     * first hosted screen opened after use gets an operator-only entry to the editor. The hosted screen itself
     * remains responsible for all ordinary browsing and research actions.
     */
    @Mod.EventBusSubscriber(modid = TcModernLib.MOD_ID, value = Dist.CLIENT)
    public static final class Opening {
        private static final String THAUMONOMICON = "Thaumcraft:ItemThaumonomicon", HOST_SCREEN = "github.com.gengyoubo.mfcl.client.ClientContainerBridge$HostedScreen";
        private static final long WINDOW_MS = 1500;
        private static final Map<Item, Boolean> BOOKS = new ConcurrentHashMap<>();
        private static long expectUntil;
        private static final Map<Screen, Boolean> HOSTS = new WeakHashMap<>();

        private Opening() {}

        @SubscribeEvent public static void used(PlayerInteractEvent.RightClickItem event) {
            if (event.getLevel().isClientSide() && isBook(event.getItemStack())) expectUntil = System.currentTimeMillis() + WINDOW_MS;
        }

        @SubscribeEvent public static void opening(ScreenEvent.Opening event) {
            var screen = event.getNewScreen();
            if (screen == null || screen instanceof ThaumonomiconScreen || System.currentTimeMillis() > expectUntil) return;
            if (!screen.getClass().getName().equals(HOST_SCREEN)) return;
            expectUntil = 0;
            HOSTS.put(screen, true);
        }

        private static boolean editable(Screen screen) {
            var player = Minecraft.getInstance().player;
            return HOSTS.containsKey(screen) && player != null && player.hasPermissions(BookLogic.CHEAT_PERMISSION);
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

        /** The original Thaumonomicon, any metadata (the cheat sheet included); cached per host item. */
        static boolean isBook(ItemStack stack) {
            if (stack.isEmpty()) return false;
            return BOOKS.computeIfAbsent(stack.getItem(), item -> TcModernApi.runtime().map(runtime -> {
                try {
                    var legacy = runtime.mappings().toLegacy(LegacyMappings.ITEM_STACK, stack.copyWithCount(1)).orElse(null);
                    return legacy != null && runtime.snapshot(legacy).key().equals(THAUMONOMICON);
                } catch (RuntimeException modernOnly) {
                    return false;
                }
            }).orElse(false));
        }
    }
}
