package github.com.gengyoubo.tcmodern.tc4.compat.jei;

import github.com.gengyoubo.tcmodern.api.recipe.TcRecipe;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Original layout drawn with native primitives; no original TC4 textures are redistributed. */
final class TcRecipeCategory implements IRecipeCategory<JeiTcRecipe> {
    static final int MAX_ASPECTS = 16;
    static final int MAX_COMPONENTS = 16;
    private final RecipeType<JeiTcRecipe> type;
    private final String title;
    private final IDrawable icon;

    TcRecipeCategory(RecipeType<JeiTcRecipe> type, String title, IDrawable icon) {
        this.type = type;
        this.title = title;
        this.icon = icon;
    }

    @Override public RecipeType<JeiTcRecipe> getRecipeType() { return type; }
    @Override public Component getTitle() { return Component.translatable(title); }
    @Override public int getWidth() { return 192; }
    @Override public int getHeight() { return 144; }
    @Override public IDrawable getIcon() { return icon; }
    @Override public ResourceLocation getRegistryName(JeiTcRecipe recipe) {
        return ResourceLocation.fromNamespaceAndPath("tc4modern", recipe.source().id());
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, JeiTcRecipe recipe, IFocusGroup focuses) {
        for (int i = 0; i < recipe.inputs().size(); i++) {
            if (recipe.inputs().get(i).isEmpty()) continue;
            int[] position = position(recipe.source(), i);
            builder.addSlot(RecipeIngredientRole.INPUT, position[0], position[1])
                    .addItemStacks(recipe.inputs().get(i));
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, 156, outputY(recipe.source())).addItemStack(recipe.output());
        if (recipe.source().kind() == TcRecipe.Kind.ARCANE_SHAPELESS) builder.setShapeless();
    }

    @Override
    public void draw(JeiTcRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        for (int i = 0; i < recipe.inputs().size(); i++) {
            var position = position(recipe.source(), i);
            frame(graphics, position[0], position[1]);
        }
        frame(graphics, 156, outputY(recipe.source()));
        graphics.drawString(font, ">", 131, outputY(recipe.source()) + 4, 0xFF606060, false);
        String research = recipe.source().research();
        Component label = research.isEmpty() ? Component.translatable("jei.tc4modern.no_research")
                : Component.translatable("jei.tc4modern.research", research);
        graphics.drawString(font, font.plainSubstrByWidth(label.getString(), 188), 2, 68, 0xFF404040, false);
        if (recipe.source().kind() == TcRecipe.Kind.INFUSION) {
            graphics.drawString(font, Component.translatable("jei.tc4modern.instability", recipe.source().instability()),
                    2, 80, 0xFF404040, false);
        }
        graphics.drawString(font, Component.translatable(isArcane(recipe.source())
                ? "jei.tc4modern.vis" : "jei.tc4modern.essentia"), 2, 92, 0xFF404040, false);
        for (int i = 0; i < recipe.source().aspects().size(); i++) {
            var cost = recipe.source().aspects().get(i);
            int x = 2 + i % 8 * 24, y = 105 + i / 8 * 18;
            var texture = ResourceLocation.tryParse(cost.texture());
            if (texture != null && Minecraft.getInstance().getResourceManager().getResource(texture).isPresent()) {
                graphics.setColor((cost.color() >> 16 & 255) / 255F, (cost.color() >> 8 & 255) / 255F,
                        (cost.color() & 255) / 255F, 1);
                graphics.blit(texture, x, y, 16, 16, 0F, 0F, 32, 32, 32, 32);
                graphics.setColor(1, 1, 1, 1);
            } else {
                graphics.fill(x, y, x + 16, y + 16, 0xFF000000 | cost.color());
                graphics.drawString(font, cost.tag().substring(0, 1), x + 4, y + 2, 0xFFFFFFFF, true);
            }
            String amount = Integer.toString(cost.amount());
            graphics.drawString(font, amount, x + 19 - font.width(amount), y + 9, 0xFFFFFFFF, true);
        }
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, JeiTcRecipe recipe, IRecipeSlotsView slots, double x, double y) {
        if (y >= 66 && y < 79) {
            var text = recipe.source().research();
            tooltip.addAll(List.of(text.isEmpty() ? Component.translatable("jei.tc4modern.no_research")
                    : Component.translatable("jei.tc4modern.research", text),
                    Component.translatable("jei.tc4modern.view_only")));
            return;
        }
        var result = new ArrayList<Component>();
        for (int i = 0; i < recipe.source().aspects().size(); i++) {
            int left = 2 + i % 8 * 24, top = 105 + i / 8 * 18;
            if (x >= left && x < left + 23 && y >= top && y < top + 17) {
                var cost = recipe.source().aspects().get(i);
                String key = "tc.aspect." + cost.tag();
                String name = I18n.exists(key) ? I18n.get(key) : cost.tag();
                result.add(Component.literal(name + " (" + cost.tag() + ") × " + cost.amount()));
            }
        }
        tooltip.addAll(result);
    }

    private static int[] position(TcRecipe recipe, int index) {
        return switch (recipe.kind()) {
            case ARCANE_SHAPED -> new int[]{8 + index % recipe.width() * 20, 8 + index / recipe.width() * 20};
            case ARCANE_SHAPELESS -> new int[]{8 + index % 3 * 20, 8 + index / 3 * 20};
            case CRUCIBLE -> new int[]{28, 26};
            case INFUSION -> index == 0 ? new int[]{72, 56}
                    : new int[]{8 + (index - 1) % 8 * 22, 8 + (index - 1) / 8 * 22};
        };
    }

    private static int outputY(TcRecipe recipe) { return recipe.kind() == TcRecipe.Kind.INFUSION ? 56 : 26; }
    private static boolean isArcane(TcRecipe recipe) {
        return recipe.kind() == TcRecipe.Kind.ARCANE_SHAPED || recipe.kind() == TcRecipe.Kind.ARCANE_SHAPELESS;
    }
    private static void frame(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF777777);
        graphics.fill(x, y, x + 16, y + 16, 0xFFE8E8E8);
    }
}
