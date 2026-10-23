package github.com.gengyoubo.tcmodern.tc4.compat.jei;

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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

/** A world interaction, displayed without registering a new executable recipe. */
final class ItemInFluidCategory implements IRecipeCategory<ItemInFluidCategory.Recipe> {
    record Recipe(ItemStack ingredient, ItemStack result) {
        Recipe { ingredient = ingredient.copyWithCount(1); result = result.copyWithCount(1); }
    }

    static final RecipeType<Recipe> TYPE = RecipeType.create("tc4modern", "item_in_fluid", Recipe.class);
    private static final int WIDTH = 160, HEIGHT = 82, ITEM_X = 22, WATER_X = 70, RESULT_X = 126, ROW = 32;
    private static final int INK = 0xFF505050;
    private final IDrawable icon;

    ItemInFluidCategory(IDrawable icon) { this.icon = icon; }

    @Override public RecipeType<Recipe> getRecipeType() { return TYPE; }
    @Override public Component getTitle() { return Component.translatable("jei.tc4modern.item_in_fluid"); }
    @Override public int getWidth() { return WIDTH; }
    @Override public int getHeight() { return HEIGHT; }
    @Override public IDrawable getIcon() { return icon; }
    @Override public ResourceLocation getRegistryName(Recipe recipe) {
        return ResourceLocation.fromNamespaceAndPath("tc4modern", "purifying_fluid");
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Recipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, ITEM_X, ROW).setStandardSlotBackground().addItemStack(recipe.ingredient())
                .addRichTooltipCallback((slot, tooltip) -> tooltip.add(Component.translatable("jei.tc4modern.item_in_fluid.stack")));
        builder.addSlot(RecipeIngredientRole.INPUT, WATER_X, ROW).setStandardSlotBackground().addFluidStack(Fluids.WATER, 1000)
                .addRichTooltipCallback((slot, tooltip) -> tooltip.add(Component.translatable("jei.tc4modern.item_in_fluid.source")));
        // A bucket is a lookup alias for the placed source, not a consumed container.
        builder.addInvisibleIngredients(RecipeIngredientRole.INPUT).addItemStack(new ItemStack(Items.WATER_BUCKET));
        builder.addSlot(RecipeIngredientRole.OUTPUT, RESULT_X, ROW).setOutputSlotBackground().addItemStack(recipe.result());
    }

    @Override
    public void draw(Recipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        // Dropped item curves down into the water; water then turns into the output block.
        graphics.fill(ITEM_X + 7, 12, ITEM_X + 10, ROW - 4, INK);
        graphics.fill(ITEM_X + 7, 12, WATER_X + 10, 15, INK);
        graphics.fill(WATER_X + 7, 12, WATER_X + 10, ROW - 4, INK);
        for (int i = 0; i < 5; i++)
            graphics.fill(WATER_X + 3 + i, ROW - 9 + i, WATER_X + 14 - i, ROW - 8 + i, INK);
        graphics.fill(WATER_X + 24, ROW + 7, RESULT_X - 8, ROW + 10, INK);
        for (int i = 0; i < 6; i++)
            graphics.fill(RESULT_X - 13 + i, ROW + 2 + i, RESULT_X - 12 + i, ROW + 15 - i, INK);
        centered(graphics, "jei.tc4modern.item_in_fluid.source", 57);
        centered(graphics, "jei.tc4modern.item_in_fluid.wait", 69);
    }

    private static void centered(GuiGraphics graphics, String key, int y) {
        var font = Minecraft.getInstance().font;
        String text = font.plainSubstrByWidth(Component.translatable(key).getString(), WIDTH - 8);
        graphics.drawString(font, text, (WIDTH - font.width(text)) / 2, y, INK, false);
    }
}
