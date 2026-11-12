package github.com.gengyoubo.tcmodern.lib.client.book;

import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.mfcl.api.mla.LegacyField;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.recipe.TcSmithingRecipes;
import github.com.gengyoubo.tcmodern.api.research.TcBookAccessors;
import github.com.gengyoubo.tcmodern.api.research.TcThaumonomicon;
import github.com.gengyoubo.tcmodern.lib.TcModernLib;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Completes, on the original Thaumonomicon's recipe page, what that page cannot draw itself:
 * <ul>
 *   <li>extra crucible recipes with several materials: the page draws one catalyst, by the arrow above the crucible
 *       (the last material); the others are drawn in a row to its left, on the same line, with their tooltips;</li>
 *   <li>smithing pages, which reach the original as an empty text page: drawn here from the original's own art,
 *       materials to result as on the smithing table: template, base and addition in three cells of the crafting
 *       grid, the smelting page's arrow, and the result in the crafting page's rune circle.</li>
 * </ul>
 * Coordinates follow GuiResearchRecipe of TC4 4.2.3.5: the 256x181 pane is centred, each page starts 152 px after the
 * previous, recipe pages are drawn 4 px left of and 8 px above the page origin, the catalyst sits 22, 64 into the
 * pane and a title is centred 52 px in. The overlay texture is 512 px, which the original addresses as 256 at double
 * scale, so its pixels are GUI pixels: the rune circle is at 40, 6 (32 px), the crafting grid at 120, 30 (104 px, cells
 * about 34.7 px wide), the smelting arrow's shaft at 53, 476 and its head at 45, 479.
 * <p>This runs after MFCL has replayed the original page, so it draws over it.</p>
 */
@Mod.EventBusSubscriber(modid = TcModernLib.MOD_ID, value = Dist.CLIENT)
final class OriginalRecipePages {
    private static final String HOST_SCREEN = "github.com.gengyoubo.mfcl.client.ClientContainerBridge$HostedScreen";
    private static final String RECIPE_SCREEN = "thaumcraft.client.gui.GuiResearchRecipe";
    private static final ResourceLocation OVERLAY = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_researchbook_overlay.png");
    private static final int PANE_WIDTH = 256, PANE_HEIGHT = 181, PAGE_STEP = 152, CATALYST_X = 22, CATALYST_Y = 64, SPACING = 20;
    private static final int TITLE_CENTRE = 52, TITLE_Y = -8, INK = 0x505050;
    // The smithing page, relative to the page origin, in one row centred on the page as on the smithing table:
    // template, base and addition in three cells, an arrow, then the result in the rune circle.
    private static final int ROW_CENTRE = 44;
    private static final int ROW_U = 120, ROW_V = 30, ROW_WIDTH = 104, ROW_HALF = 20, CELLS_X = -2;
    private static final float CELLS_SCALE = 0.6F;
    /** Item x in each cell: the cells' centres (a third of the grid apart) less half an item. */
    private static final int[] SLOT_X = {0, 21, 42};
    private static final int ARROW_X = 64, ARROW_SHAFT = 8;
    private static final int ARROW_SHAFT_U = 53, ARROW_SHAFT_V = 476, ARROW_SHAFT_WIDTH = 6, ARROW_SHAFT_HEIGHT = 3, ARROW_SHAFT_OFFSET = 8;
    private static final int ARROW_HEAD_U = 45, ARROW_HEAD_V = 479, ARROW_HEAD_WIDTH = 22, ARROW_HEAD_HEIGHT = 12;
    private static final float ARROW_SCALE = 0.75F;
    private static final int RUNE_X = 82, RUNE_U = 40, RUNE_V = 6, RUNE_SIZE = 32, RESULT_X = 86;
    private static final float RUNE_SCALE = 0.75F;

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

    private OriginalRecipePages() { }

    /** After MFCL's own handler, which replays the original page; otherwise the page would cover this. */
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void render(ScreenEvent.Render.Post event) {
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
            graphics.pose().pushPose();
            graphics.pose().scale(scale, scale, 1);
            for (int side = 0; side < 2; side++) {
                int index = gui.page() + side;
                if (index >= runtime.values().length(pages) || !(runtime.values().get(pages, index) instanceof LegacyObject page)) continue;
                int pageX = left + PAGE_STEP * side;
                var upgrades = TcThaumonomicon.smithing(runtime, page);
                if (!upgrades.isEmpty()) {
                    var recipe = upgrades.get((int) (System.currentTimeMillis() / 1000 % upgrades.size()));
                    var under = smithing(graphics, runtime, recipe, pageX, top, mouseX, mouseY);
                    if (!under.isEmpty()) hovered = under;
                    continue;
                }
                var content = runtime.accessor(TcBookAccessors.Page.class, page);
                var shown = shown(runtime, content.recipe(), gui.cycle());
                if (runtime.values().same(content.type(), types.crucible())) {
                    var extra = TcThaumonomicon.extraCrucible(runtime, shown).orElse(null);
                    if (extra == null || extra.inputs().size() < 2) continue;
                    int materials = extra.inputs().size(), catalystX = pageX + CATALYST_X, y = top + CATALYST_Y;
                    // The last material is the original catalyst; the others line up to its left.
                    for (int i = 0; i < materials - 1; i++) {
                        int x = catalystX - SPACING * (materials - 1 - i);
                        var stack = item(runtime, extra.inputs().get(i));
                        if (stack.isEmpty()) continue;
                        graphics.renderItem(stack, x, y);
                        if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) hovered = stack;
                    }
                }
            }
            graphics.pose().popPose();
            if (!hovered.isEmpty()) graphics.renderTooltip(Minecraft.getInstance().font, hovered, event.getMouseX(), event.getMouseY());
        } catch (RuntimeException unavailable) {
            // Another TC4 build or MFCL without these fields: the original page stays as it is.
        }
    }

    /** Draws a smithing page at {@code pageX}; returns the stack under the mouse, or empty. */
    private static ItemStack smithing(GuiGraphics graphics, LegacyRuntime runtime, TcSmithingRecipes.Recipe recipe,
                                      int pageX, int top, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        var title = Component.translatable("book.tcmodernlib.page.smithing");
        graphics.drawString(font, title, pageX + TITLE_CENTRE - font.width(title) / 2, top + TITLE_Y, INK, false);
        int middle = top + ROW_CENTRE;
        var pose = graphics.pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        try {
            // Three cells: the upper half of the crafting grid's top row, then the same strip flipped, so their
            // separators fade out at both ends as the grid's do at its outer edge.
            pose.pushPose();
            pose.translate(pageX + CELLS_X, middle - CELLS_SCALE * ROW_HALF, 0);
            pose.scale(CELLS_SCALE, CELLS_SCALE, 1);
            graphics.blit(OVERLAY, 0, 0, ROW_U, ROW_V, ROW_WIDTH, ROW_HALF, 512, 512);
            pose.translate(0, 2 * ROW_HALF, 0);
            pose.scale(1, -1, 1);
            graphics.blit(OVERLAY, 0, 0, ROW_U, ROW_V, ROW_WIDTH, ROW_HALF, 512, 512);
            pose.popPose();
            // The smelting page's arrow, which points down, with a longer shaft, turned to point at the result.
            pose.pushPose();
            pose.translate(pageX + ARROW_X, middle + ARROW_SCALE * ARROW_HEAD_WIDTH / 2F, 0);
            pose.scale(ARROW_SCALE, ARROW_SCALE, 1);
            pose.mulPose(Axis.ZP.rotationDegrees(-90));
            graphics.blit(OVERLAY, ARROW_SHAFT_OFFSET, 0, ARROW_SHAFT_WIDTH, ARROW_SHAFT, ARROW_SHAFT_U, ARROW_SHAFT_V,
                    ARROW_SHAFT_WIDTH, ARROW_SHAFT_HEIGHT, 512, 512);
            graphics.blit(OVERLAY, 0, ARROW_SHAFT, ARROW_HEAD_U, ARROW_HEAD_V, ARROW_HEAD_WIDTH, ARROW_HEAD_HEIGHT, 512, 512);
            pose.popPose();
            // The crafting page's rune circle behind the result.
            pose.pushPose();
            pose.translate(pageX + RUNE_X, middle - RUNE_SCALE * RUNE_SIZE / 2F, 0);
            pose.scale(RUNE_SCALE, RUNE_SCALE, 1);
            graphics.blit(OVERLAY, 0, 0, RUNE_U, RUNE_V, RUNE_SIZE, RUNE_SIZE, 512, 512);
            pose.popPose();
        } finally {
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
        ItemStack hovered = ItemStack.EMPTY;
        var slots = List.of(recipe.template(), recipe.base(), recipe.addition());
        for (int i = 0; i < slots.size(); i++) {
            var stack = item(runtime, slots.get(i));
            if (draw(graphics, font, stack, pageX + SLOT_X[i], middle - 8, mouseX, mouseY)) hovered = stack;
        }
        var result = item(runtime, List.of(recipe.output()));
        if (draw(graphics, font, result, pageX + RESULT_X, middle - 8, mouseX, mouseY)) hovered = result;
        return hovered;
    }

    /** Draws one stack with its count; true when the mouse is over it. */
    private static boolean draw(GuiGraphics graphics, Font font, ItemStack stack, int x, int y, double mouseX, double mouseY) {
        if (stack.isEmpty()) return false;
        graphics.renderItem(stack, x, y);
        graphics.renderItemDecorations(font, stack, x, y);
        return mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16;
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
