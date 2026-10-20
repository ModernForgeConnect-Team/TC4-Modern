package github.com.gengyoubo.tcmodern.tc4.compat.jei;

import github.com.gengyoubo.tcmodern.api.aspect.TcAspect;
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

/** A compound aspect and the two aspects it is made of, as the Thaumonomicon's aspect pages listed it. */
final class AspectCompositionCategory implements IRecipeCategory<TcAspect> {
    static final RecipeType<TcAspect> TYPE = RecipeType.create("tc4modern", "aspect_composition", TcAspect.class);
    private static final int WIDTH = 120, HEIGHT = 34, SLOT_Y = 9;
    private static final int INK = 0xFF505050;

    @Override public RecipeType<TcAspect> getRecipeType() { return TYPE; }
    @Override public Component getTitle() { return Component.translatable("jei.tc4modern.aspect_composition"); }
    @Override public int getWidth() { return WIDTH; }
    @Override public int getHeight() { return HEIGHT; }
    @Override public ResourceLocation getRegistryName(TcAspect aspect) {
        return ResourceLocation.fromNamespaceAndPath("tc4modern", "aspect/" + aspect.tag());
    }

    /** The first compound aspect's icon, drawn when JEI asks, since the registry is read after categories exist. */
    @Override public IDrawable getIcon() {
        return new IDrawable() {
            @Override public int getWidth() { return 16; }
            @Override public int getHeight() { return 16; }
            @Override public void draw(GuiGraphics graphics, int x, int y) {
                JeiAspect.all().stream().filter(aspect -> !aspect.primal()).findFirst()
                        .ifPresent(aspect -> JeiAspect.renderIcon(graphics, aspect, x, y));
            }
        };
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, TcAspect aspect, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 16, SLOT_Y).addIngredient(JeiAspect.TYPE, JeiAspect.of(aspect.components().get(0)));
        builder.addSlot(RecipeIngredientRole.INPUT, 48, SLOT_Y).addIngredient(JeiAspect.TYPE, JeiAspect.of(aspect.components().get(1)));
        builder.addSlot(RecipeIngredientRole.OUTPUT, 88, SLOT_Y).addIngredient(JeiAspect.TYPE, new JeiAspect(aspect, 0));
    }

    @Override
    public void draw(TcAspect aspect, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        if (TcRecipeCategory.hasBookArt()) TcRecipeCategory.parchment(graphics, WIDTH, HEIGHT);
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, "+", 37, SLOT_Y + 4, INK, false);
        graphics.drawString(font, "→", 68, SLOT_Y + 4, INK, false);
    }
}
