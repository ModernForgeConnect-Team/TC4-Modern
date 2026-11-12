package github.com.gengyoubo.tcmodern.tc4.crucible;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspect;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspects;
import github.com.gengyoubo.tcmodern.api.recipe.TcRecipe;
import github.com.gengyoubo.tcmodern.lib.json.RecipeJson;
import github.com.gengyoubo.tcmodern.lib.json.RecipeLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

/**
 * Offers the intermediate crucible's recipes to research {@code crucible} pages, so a page for an item this crucible
 * makes finds its recipe. Display data only: the original crucible still cannot craft them.
 */
public final class IntermediateCruciblePages {
    private IntermediateCruciblePages() { }

    public static List<TcRecipe> recipes(LegacyRuntime runtime) {
        var hostItems = RecipeLoader.hostItems(runtime);
        Map<String, Integer> colors = TcAspects.snapshot(runtime).stream()
                .collect(Collectors.toMap(TcAspect::tag, TcAspect::color, (first, second) -> first));
        var result = new ArrayList<TcRecipe>();
        for (var recipe : IntermediateRecipes.all()) {
            try {
                var inputs = new ArrayList<List<LegacyStack>>();
                for (var ingredient : recipe.ingredients()) {
                    var choices = choices(ingredient, hostItems);
                    // Tags are bound only once a world loads; research registers earlier, so an unbound tag shows nothing.
                    if (choices.isEmpty()) throw new IllegalStateException("no items for " + ingredient);
                    inputs.add(choices);
                }
                var aspects = new ArrayList<TcRecipe.AspectCost>();
                recipe.aspects().forEach((tag, amount) -> aspects.add(new TcRecipe.AspectCost(tag, colors.getOrDefault(tag, 0), amount)));
                result.add(new TcRecipe(TcRecipe.Kind.CRUCIBLE, recipe.research(), inputs.size(), 1, inputs,
                        output(recipe.result(), hostItems), aspects, 0));
            } catch (RuntimeException unshowable) {
                LogUtils.getLogger().warn("Intermediate crucible recipe {} cannot be shown on research pages: {}", recipe.id(), unshowable.toString());
            }
        }
        return result;
    }

    private static List<LegacyStack> choices(IntermediateRecipe.Ingredient ingredient, RecipeJson.HostItems hostItems) {
        if (ingredient instanceof IntermediateRecipe.Ingredient.Item item) return List.of(hostItems.stack(item.id(), 1, null));
        if (ingredient instanceof IntermediateRecipe.Ingredient.Legacy legacy)
            return List.of(new LegacyStack(legacy.key(), legacy.metadata(), 1, null));
        var tag = TagKey.create(BuiltInRegistries.ITEM.key(), new ResourceLocation(((IntermediateRecipe.Ingredient.Tag) ingredient).id()));
        var choices = new ArrayList<LegacyStack>();
        BuiltInRegistries.ITEM.getTagOrEmpty(tag).forEach(holder ->
                choices.add(hostItems.stack(BuiltInRegistries.ITEM.getKey(holder.value()).toString(), 1, null)));
        return choices;
    }

    private static LegacyStack output(IntermediateRecipe.Output result, RecipeJson.HostItems hostItems) {
        String nbt = result.nbt().isEmpty() ? null : result.nbt();
        return result.item() != null ? hostItems.stack(result.item(), result.count(), nbt)
                : new LegacyStack(result.legacyItem(), result.metadata(), result.count(), nbt);
    }
}
