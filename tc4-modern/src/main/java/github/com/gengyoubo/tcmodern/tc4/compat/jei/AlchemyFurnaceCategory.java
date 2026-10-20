package github.com.gengyoubo.tcmodern.tc4.compat.jei;

import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** The alchemical furnace boiling an item down into its essentia, one aspect per its TC4 object tags. */
final class AlchemyFurnaceCategory implements IRecipeCategory<AlchemyFurnaceCategory.Recipe> {
    /** @param essentia the item's aspects with their amounts, object tags plus bonus tags */
    record Recipe(ItemStack input, List<JeiAspect> essentia) {
        Recipe { input = input.copyWithCount(1); essentia = List.copyOf(essentia); }
    }

    static final RecipeType<Recipe> TYPE = RecipeType.create("tc4modern", "alchemy_furnace", Recipe.class);
    static final int MAX_ESSENTIA = 24;
    private static final int PER_ROW = 6, ESSENTIA_X = 52, ROWS = MAX_ESSENTIA / PER_ROW;
    private static final int WIDTH = 160, HEIGHT = 8 + ROWS * 18, INPUT_Y = (HEIGHT - 16) / 2;
    private static final int INK = 0xFF505050;
    private final IDrawable icon;

    AlchemyFurnaceCategory(IDrawable icon) { this.icon = icon; }

    @Override public RecipeType<Recipe> getRecipeType() { return TYPE; }
    @Override public Component getTitle() { return Component.translatable("jei.tc4modern.alchemy_furnace"); }
    @Override public int getWidth() { return WIDTH; }
    @Override public int getHeight() { return HEIGHT; }
    @Override public IDrawable getIcon() { return icon; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Recipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 12, INPUT_Y).addItemStack(recipe.input());
        int rows = (recipe.essentia().size() + PER_ROW - 1) / PER_ROW, top = (HEIGHT - rows * 18 + 2) / 2;
        for (int i = 0; i < recipe.essentia().size(); i++) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, ESSENTIA_X + i % PER_ROW * 18, top + i / PER_ROW * 18)
                    .addIngredient(JeiAspect.TYPE, recipe.essentia().get(i));
        }
    }

    @Override
    public void draw(Recipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        if (TcRecipeCategory.hasBookArt()) TcRecipeCategory.parchment(graphics, WIDTH, HEIGHT);
        graphics.drawString(Minecraft.getInstance().font, "→", 34, INPUT_Y + 4, INK, false);
    }
}
