package github.com.gengyoubo.tcmodern.lib.jei;

import java.util.List;
import github.com.gengyoubo.tcmodern.lib.client.formed.FormedStructures;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Right-clicking a placed block with a wand to turn it into a TC4 one, as the original BlockTable and WandManager do. */
public final class WandConversionCategory implements IRecipeCategory<WandConversionCategory.Recipe> {
    /** @param note translation key of the line under the conversion */
    public record Recipe(ItemStack block, List<ItemStack> wands, ItemStack result, String note, String structure) {
        public Recipe { block = block.copyWithCount(1); wands = List.copyOf(wands); result = result.copyWithCount(1); }
        public Recipe(ItemStack block, List<ItemStack> wands, ItemStack result, String note) { this(block, wands, result, note, ""); }
    }

    /** The preview draws the real placed structure; the invisible slot retains JEI lookup and hover behaviour. */
    private static final IIngredientRenderer<ItemStack> STRUCTURE_SLOT = new IIngredientRenderer<>() {
        @Override public void render(GuiGraphics graphics, ItemStack ingredient) { }
        @Override public List<Component> getTooltip(ItemStack ingredient, TooltipFlag flag) {
            return List.of(Component.translatable("jei.tc4modern.multiblock.research_table"),
                    Component.translatable("jei.tc4modern.wand.modern_desk"));
        }
    };

    public static final RecipeType<Recipe> TYPE = RecipeType.create("tc4modern", "wand_conversion", Recipe.class);
    private static final int WIDTH = 160, HEIGHT = 64, ROW = 26, BLOCK_X = 20, WAND_X = 72, RESULT_X = 124;
    private static final int INK = 0xFF505050;
    private final IDrawable icon;

    WandConversionCategory(IDrawable icon) { this.icon = icon; }

    @Override public RecipeType<Recipe> getRecipeType() { return TYPE; }
    @Override public Component getTitle() { return Component.translatable("jei.tc4modern.wand_conversion"); }
    @Override public int getWidth() { return WIDTH; }
    @Override public int getHeight() { return HEIGHT; }
    @Override public IDrawable getIcon() { return icon; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Recipe recipe, IFocusGroup focuses) {
        var source = builder.addSlot(RecipeIngredientRole.INPUT, BLOCK_X, ROW).addItemStack(recipe.block());
        if (!recipe.structure().isEmpty()) source.setCustomRenderer(VanillaTypes.ITEM_STACK, STRUCTURE_SLOT);
        builder.addSlot(RecipeIngredientRole.CATALYST, WAND_X, ROW - 20).addItemStacks(recipe.wands())
                .addRichTooltipCallback((slot, tooltip) -> tooltip.add(Component.translatable("jei.tc4modern.wand.any")));
        builder.addSlot(RecipeIngredientRole.OUTPUT, RESULT_X, ROW).addItemStack(recipe.result())
                .addRichTooltipCallback((slot, tooltip) -> tooltip.add(Component.translatable(recipe.note())));
    }

    @Override
    public void draw(Recipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        if (TcRecipeCategory.hasBookArt()) TcRecipeCategory.parchment(graphics, WIDTH, HEIGHT);
        else for (int x : new int[]{BLOCK_X, WAND_X, RESULT_X}) frame(graphics, x, x == WAND_X ? ROW - 20 : ROW);
        if (!recipe.structure().isEmpty() && !FormedStructures.render(graphics, recipe.structure(), BLOCK_X + 8, ROW + 7,
                33, 17, 45, 30)) {
            String unavailable = Component.translatable("jei.tc4modern.wand.preview_unavailable").getString();
            graphics.drawString(font, font.plainSubstrByWidth(unavailable, 38), BLOCK_X - 10, ROW + 4, INK, false);
        }
        // Arrow from the block to the result, with the right-click under the wand.
        graphics.fill(BLOCK_X + 22, ROW + 7, RESULT_X - 8, ROW + 9, INK);
        graphics.drawString(font, ">", RESULT_X - 9, ROW + 4, INK, false);
        String click = Component.translatable(recipe.structure().isEmpty() ? "jei.tc4modern.wand.click" : "jei.tc4modern.wand.sneak_click").getString();
        graphics.drawString(font, click, WAND_X + 8 - font.width(click) / 2, ROW + 12, INK, false);
        String note = font.plainSubstrByWidth(Component.translatable(recipe.structure().isEmpty() ? recipe.note() : "jei.tc4modern.wand.modern_desk_required").getString(), WIDTH - 8);
        graphics.drawString(font, note, (WIDTH - font.width(note)) / 2, HEIGHT - 12, INK, false);
    }

    private static void frame(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF777777);
        graphics.fill(x, y, x + 16, y + 16, 0xFFE8E8E8);
    }
}
