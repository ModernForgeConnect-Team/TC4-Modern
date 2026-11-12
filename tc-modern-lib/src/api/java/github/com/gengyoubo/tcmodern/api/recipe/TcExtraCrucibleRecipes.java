package github.com.gengyoubo.tcmodern.api.recipe;

import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Crucible recipes that a mod crafts itself instead of through the original {@code CrucibleRecipe} list, such as a
 * crucible that takes several materials at once. A research {@code crucible} page shows them next to the original
 * crucible recipes for the same output. They are display data only: nothing here lets the original crucible craft them.
 *
 * <p>Each recipe is a {@link TcRecipe.Kind#CRUCIBLE} {@link TcRecipe} with one input slot per material
 * ({@code width} = materials, {@code height} = 1). The original book draws the last material as its catalyst and this
 * library's client draws the others in a row to its left; this library's own book draws them side by side.</p>
 *
 * <p>Providers are asked whenever research registers its pages (after every mod has loaded, and again on a research
 * reload), so register one from a mod constructor or common setup. A provider runs on both sides and must not
 * touch client classes.</p>
 */
public final class TcExtraCrucibleRecipes {
    private static final Map<String, Function<LegacyRuntime, List<TcRecipe>>> PROVIDERS = new LinkedHashMap<>();

    private TcExtraCrucibleRecipes() { }

    /**
     * Adds or replaces the provider registered under {@code owner} (usually a mod id).
     *
     * @return closing it removes this provider again
     */
    public static synchronized AutoCloseable register(String owner, Function<LegacyRuntime, List<TcRecipe>> recipes) {
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("Blank provider owner");
        if (recipes == null) throw new IllegalArgumentException("Null provider");
        PROVIDERS.put(owner, recipes);
        return () -> {
            synchronized (TcExtraCrucibleRecipes.class) { PROVIDERS.remove(owner, recipes); }
        };
    }

    /**
     * Every provider's crucible recipes.
     *
     * @throws IllegalStateException naming the provider when one fails or returns a recipe of another kind
     */
    public static List<TcRecipe> all(LegacyRuntime runtime) {
        Map<String, Function<LegacyRuntime, List<TcRecipe>>> providers;
        synchronized (TcExtraCrucibleRecipes.class) { providers = new LinkedHashMap<>(PROVIDERS); }
        var result = new ArrayList<TcRecipe>();
        providers.forEach((owner, provider) -> {
            List<TcRecipe> recipes;
            try {
                recipes = provider.apply(runtime);
            } catch (RuntimeException failure) {
                throw new IllegalStateException("extra crucible recipes from " + owner + " failed: " + failure, failure);
            }
            if (recipes == null) return;
            for (var recipe : recipes) {
                if (recipe == null || recipe.kind() != TcRecipe.Kind.CRUCIBLE)
                    throw new IllegalStateException("extra crucible recipes from " + owner + " include a non-crucible recipe: " + recipe);
                result.add(recipe);
            }
        });
        return List.copyOf(result);
    }
}
