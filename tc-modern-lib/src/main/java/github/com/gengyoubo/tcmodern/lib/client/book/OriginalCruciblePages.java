package github.com.gengyoubo.tcmodern.lib.client.book;

import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.mfcl.api.mla.LegacyField;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.research.TcBookAccessors;
import github.com.gengyoubo.tcmodern.api.research.TcThaumonomicon;
import github.com.gengyoubo.tcmodern.lib.TcModernLib;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Completes extra crucible recipes (several materials) on the original Thaumonomicon's recipe page. That page draws
 * one catalyst, by the arrow above the crucible: the last material. The others are drawn here in a row to its left,
 * on the same line, with their tooltips. Coordinates follow GuiResearchRecipe of TC4 4.2.3.5: the 256x181 pane is
 * centred, each page starts 152 px after the previous, and the catalyst sits 22, 64 into the pane.
 */
@Mod.EventBusSubscriber(modid = TcModernLib.MOD_ID, value = Dist.CLIENT)
final class OriginalCruciblePages {
    private static final String HOST_SCREEN = "github.com.gengyoubo.mfcl.client.ClientContainerBridge$HostedScreen";
    private static final String RECIPE_SCREEN = "thaumcraft.client.gui.GuiResearchRecipe";
    private static final int PANE_WIDTH = 256, PANE_HEIGHT = 181, PAGE_STEP = 152, CATALYST_X = 22, CATALYST_Y = 64, SPACING = 20;

    @LegacyType(RECIPE_SCREEN)
    public interface RecipeScreen {
        /** The left page shown; the right one is the next. */
        @LegacyField(name = "page", descriptor = "I") int page();
        @LegacyField(name = "pages", descriptor = "[Lthaumcraft/api/research/ResearchPage;") LegacyObject pages();
        /** Which recipe of a page's recipe array is shown. */
        @LegacyField(name = "cycle", descriptor = "I") int cycle();
    }

    @LegacyType("net.minecraft.client.gui.GuiScreen")
    public interface ScreenSize {
        @LegacyField(name = "field_146294_l", descriptor = "I") int width();
        @LegacyField(name = "field_146295_m", descriptor = "I") int height();
    }

    private OriginalCruciblePages() { }

    @SubscribeEvent public static void render(ScreenEvent.Render.Post event) {
        var screen = event.getScreen();
        if (!screen.getClass().getName().equals(HOST_SCREEN)) return;
        var runtime = TcModernApi.runtime().orElse(null);
        if (runtime == null) return;
        try {
            var client = runtime.accessor(BookClient.Opening.ClientFactory.class).instance();
            var original = runtime.accessor(BookClient.Opening.Client.class, client).screen();
            if (original == null || !RECIPE_SCREEN.equals(original.typeName())) return;
            var size = runtime.accessor(ScreenSize.class, original);
            if (size.width() <= 0 || size.height() <= 0) return;
            var gui = runtime.accessor(RecipeScreen.class, original);
            var pages = gui.pages();
            if (pages == null) return;
            // The original draws in its own GUI space; map it onto the host screen in case the scales differ.
            float scale = (float) screen.width / size.width();
            int left = (size.width() - PANE_WIDTH) / 2, top = (size.height() - PANE_HEIGHT) / 2;
            double mouseX = event.getMouseX() / scale, mouseY = event.getMouseY() / scale;
            var types = runtime.accessor(TcBookAccessors.PageTypes.class);
            var graphics = event.getGuiGraphics();
            ItemStack hovered = ItemStack.EMPTY;
            for (int side = 0; side < 2; side++) {
                int index = gui.page() + side;
                if (index >= runtime.values().length(pages) || !(runtime.values().get(pages, index) instanceof LegacyObject page)) continue;
                var content = runtime.accessor(TcBookAccessors.Page.class, page);
                if (!runtime.values().same(content.type(), types.crucible())) continue;
                var extra = TcThaumonomicon.extraCrucible(runtime, shown(runtime, content.recipe(), gui.cycle())).orElse(null);
                if (extra == null || extra.inputs().size() < 2) continue;
                int materials = extra.inputs().size(), catalystX = left + CATALYST_X + PAGE_STEP * side, y = top + CATALYST_Y;
                graphics.pose().pushPose();
                graphics.pose().scale(scale, scale, 1);
                // The last material is the original catalyst; the others line up to its left.
                for (int i = 0; i < materials - 1; i++) {
                    int x = catalystX - SPACING * (materials - 1 - i);
                    var stack = item(runtime, extra.inputs().get(i));
                    if (stack.isEmpty()) continue;
                    graphics.renderItem(stack, x, y);
                    if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) hovered = stack;
                }
                graphics.pose().popPose();
            }
            if (!hovered.isEmpty()) graphics.renderTooltip(Minecraft.getInstance().font, hovered, event.getMouseX(), event.getMouseY());
        } catch (RuntimeException unavailable) {
            // Another TC4 build or MFCL without these fields: the original page stays as it is.
        }
    }

    /** The recipe a page currently shows: one recipe, or the {@code cycle}th of an array (the original resets it to 0 past the end). */
    private static LegacyObject shown(LegacyRuntime runtime, LegacyObject recipe, int cycle) {
        if (recipe == null) return null;
        String type = recipe.typeName();
        if (!type.startsWith("[") && !type.endsWith("[]")) return recipe;
        int length = runtime.values().length(recipe);
        if (length == 0) return null;
        return runtime.values().get(recipe, cycle >= 0 && cycle < length ? cycle : 0) instanceof LegacyObject chosen ? chosen : null;
    }

    /** One material; alternatives (a tag) cycle once a second. */
    private static ItemStack item(LegacyRuntime runtime, List<LegacyStack> choices) {
        if (choices.isEmpty()) return ItemStack.EMPTY;
        var choice = choices.get((int) (System.currentTimeMillis() / 1000 % choices.size()));
        if (choice.metadata() == TcThaumonomicon.WILDCARD) choice = new LegacyStack(choice.key(), 0, 1, choice.snbt());
        try {
            return runtime.mappings().toHost(LegacyMappings.ITEM_STACK, runtime.stack(choice), ItemStack.class).orElse(ItemStack.EMPTY);
        } catch (RuntimeException unmapped) {
            return ItemStack.EMPTY;
        }
    }
}
