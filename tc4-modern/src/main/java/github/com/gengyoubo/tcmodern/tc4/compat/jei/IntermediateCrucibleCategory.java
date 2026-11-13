package github.com.gengyoubo.tcmodern.tc4.compat.jei;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.tcmodern.lib.jei.*;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.tcmodern.tc4.crucible.IntermediateRecipe;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.mojang.blaze3d.systems.RenderSystem;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;

/** Synthesis in the intermediate crucible, drawn as the original crucible page: one or two materials and the dissolved aspects make the result. */
final class IntermediateCrucibleCategory implements IRecipeCategory<IntermediateCrucibleCategory.Recipe> {
    /** @param inputs the choices for each material slot */
    record Recipe(String research, List<List<ItemStack>> inputs, List<JeiAspect> essentia, ItemStack output) {
        Recipe { inputs = inputs.stream().map(List::copyOf).toList(); essentia = List.copyOf(essentia); output = output.copy(); }
    }

    static final RecipeType<Recipe> TYPE = RecipeType.create("tc4modern", "intermediate_crucible", Recipe.class);
    private static final int WIDTH = TcRecipeCategory.WIDTH, HEIGHT = TcRecipeCategory.HEIGHT;
    /** At most this many aspects fit inside the crucible outline (three rows of five). */
    static final int MAX_ASPECTS = 15;
    private final IDrawable icon;

    IntermediateCrucibleCategory(IDrawable icon) { this.icon = icon; }

    @Override public RecipeType<Recipe> getRecipeType() { return TYPE; }
    @Override public Component getTitle() { return Component.translatable("jei.tc4modern.intermediate_crucible"); }
    @Override public int getWidth() { return WIDTH; }
    @Override public int getHeight() { return HEIGHT; }
    @Override public IDrawable getIcon() { return icon; }

    /**
     * Laid out as the original crucible page: the result above the crucible, the aspects inside it, and the materials
     * queued up on the left behind the arrow.
     */
    @Override public void setRecipe(IRecipeLayoutBuilder builder, Recipe recipe, IFocusGroup focuses) {
        for (int i = 0; i < recipe.inputs().size(); i++)
            builder.addSlot(RecipeIngredientRole.INPUT, materialX(i, recipe.inputs().size()), TcRecipeCategory.itemY(72))
                    .addItemStacks(recipe.inputs().get(i));
        builder.addSlot(RecipeIngredientRole.OUTPUT, TcRecipeCategory.itemX(48), TcRecipeCategory.itemY(36)).addItemStack(recipe.output());
        var note = Component.translatable("jei.tc4modern.essentia");
        int count = Math.min(recipe.essentia().size(), MAX_ASPECTS);
        for (int i = 0; i < count; i++) {
            int[] at = TcRecipeCategory.crucibleAspect(count, i);
            builder.addSlot(RecipeIngredientRole.INPUT, at[0], at[1]).addIngredient(JeiAspect.TYPE, recipe.essentia().get(i))
                    .addRichTooltipCallback((slot, tooltip) -> tooltip.add(note));
        }
    }

    @Override public void draw(Recipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (TcRecipeCategory.hasBookArt()) {
            TcRecipeCategory.parchment(graphics, WIDTH, HEIGHT);
            TcRecipeCategory.crucibleArt(graphics);
        } else {
            for (int i = 0; i < recipe.inputs().size(); i++)
                TcRecipeCategory.frame(graphics, materialX(i, recipe.inputs().size()), TcRecipeCategory.itemY(72));
            TcRecipeCategory.frame(graphics, TcRecipeCategory.itemX(48), TcRecipeCategory.itemY(36));
        }
        TcRecipeCategory.centered(graphics, TcRecipeCategory.research(recipe.research()).getString(), TcRecipeCategory.RESEARCH_Y);
        RenderSystem.disableBlend();
    }

    /**
     * The materials queue up on the left behind the original arrow: the last one at the original catalyst spot by the
     * arrow's tail, the one before it further left.
     */
    private static int materialX(int index, int count) {
        int tail = TcRecipeCategory.itemX(26);
        return tail - (count - 1 - index) * (tail - TcRecipeCategory.itemX(-10));
    }

    @Override public void getTooltip(ITooltipBuilder tooltip, Recipe recipe, IRecipeSlotsView slots, double x, double y) {
        if (y >= TcRecipeCategory.RESEARCH_Y - 2 && y < TcRecipeCategory.RESEARCH_Y + 10)
            tooltip.addAll(List.of(TcRecipeCategory.research(recipe.research()), Component.translatable("jei.tc4modern.view_only")));
    }

    /** The bundled recipes with modern stacks for every slot; a recipe with no shown form for some slot is skipped. */
    static List<Recipe> recipes(List<IntermediateRecipe> recipes, JeiRecipeConverter converter) {
        var result = new ArrayList<Recipe>();
        for (var recipe : recipes) {
            try {
                var inputs = new ArrayList<List<ItemStack>>();
                for (var ingredient : recipe.ingredients()) {
                    var choices = choices(ingredient, converter);
                    if (choices.isEmpty()) throw new IllegalArgumentException("no item shows " + ingredient);
                    inputs.add(choices);
                }
                var output = output(recipe.result(), converter).orElseThrow(() -> new IllegalArgumentException("no item shows the result"));
                var essentia = recipe.aspects().entrySet().stream().map(cost -> JeiAspect.of(cost.getKey()).withAmount(cost.getValue())).toList();
                result.add(new Recipe(recipe.research(), inputs, essentia, output));
            } catch (RuntimeException unshown) {
                LogUtils.getLogger().warn("TC4 JEI: intermediate crucible recipe {} not shown: {}", recipe.id(), unshown.getMessage());
            }
        }
        return result;
    }

    private static List<ItemStack> choices(IntermediateRecipe.Ingredient ingredient, JeiRecipeConverter converter) {
        if (ingredient instanceof IntermediateRecipe.Ingredient.Item item)
            return BuiltInRegistries.ITEM.getOptional(new ResourceLocation(item.id())).map(found -> List.of(new ItemStack(found))).orElse(List.of());
        if (ingredient instanceof IntermediateRecipe.Ingredient.Tag tag)
            return BuiltInRegistries.ITEM.getTag(ItemTags.create(new ResourceLocation(tag.id())))
                    .map(set -> set.stream().map(holder -> new ItemStack(holder.value())).toList()).orElse(List.of());
        var legacy = (IntermediateRecipe.Ingredient.Legacy) ingredient;
        int metadata = legacy.metadata() == IntermediateRecipe.WILDCARD ? 0 : legacy.metadata();
        return converter.map(new LegacyStack(legacy.key(), metadata, 1, "")).map(List::of).orElse(List.of());
    }

    private static Optional<ItemStack> output(IntermediateRecipe.Output result, JeiRecipeConverter converter) {
        if (result.legacyItem() != null)
            return converter.map(new LegacyStack(result.legacyItem(), result.metadata(), result.count(), result.nbt()));
        return BuiltInRegistries.ITEM.getOptional(new ResourceLocation(result.item())).map(item -> {
            var stack = new ItemStack(item, result.count());
            if (!result.nbt().isEmpty()) {
                try { stack.setTag(TagParser.parseTag(result.nbt())); }
                catch (com.mojang.brigadier.exceptions.CommandSyntaxException malformed) { throw new IllegalArgumentException(malformed.getMessage()); }
            }
            return stack;
        });
    }
}
