package github.com.gengyoubo.tcmodern.api.recipe;

import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Smithing table upgrades that research {@code smithing} pages can show. The original Thaumcraft has no smithing, and
 * modern recipes are not loaded yet when research registers, so providers hand them over as display data; this
 * library registers one that reads the {@code minecraft:smithing_transform} recipe files of every mod.
 *
 * <p>Providers are asked whenever research registers its pages, on both sides; they must not touch client classes.</p>
 */
public final class TcSmithingRecipes {
    /** One upgrade; each slot lists the stacks it accepts (several for a tag), all as their original forms. */
    public record Recipe(List<LegacyStack> template, List<LegacyStack> base, List<LegacyStack> addition, LegacyStack output) {
        public Recipe {
            template = List.copyOf(template);
            base = List.copyOf(base);
            addition = List.copyOf(addition);
            Objects.requireNonNull(output);
            if (template.isEmpty() || base.isEmpty() || addition.isEmpty()) throw new IllegalArgumentException("Every smithing slot needs an item");
        }
    }

    private static final Map<String, Function<LegacyRuntime, List<Recipe>>> PROVIDERS = new LinkedHashMap<>();

    private TcSmithingRecipes() { }

    /**
     * Adds or replaces the provider registered under {@code owner} (usually a mod id).
     *
     * @return closing it removes this provider again
     */
    public static synchronized AutoCloseable register(String owner, Function<LegacyRuntime, List<Recipe>> recipes) {
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("Blank provider owner");
        if (recipes == null) throw new IllegalArgumentException("Null provider");
        PROVIDERS.put(owner, recipes);
        return () -> {
            synchronized (TcSmithingRecipes.class) { PROVIDERS.remove(owner, recipes); }
        };
    }

    /**
     * Every provider's smithing recipes.
     *
     * @throws IllegalStateException naming the provider when one fails
     */
    public static List<Recipe> all(LegacyRuntime runtime) {
        Map<String, Function<LegacyRuntime, List<Recipe>>> providers;
        synchronized (TcSmithingRecipes.class) { providers = new LinkedHashMap<>(PROVIDERS); }
        var result = new ArrayList<Recipe>();
        providers.forEach((owner, provider) -> {
            List<Recipe> recipes;
            try {
                recipes = provider.apply(runtime);
            } catch (RuntimeException failure) {
                throw new IllegalStateException("smithing recipes from " + owner + " failed: " + failure, failure);
            }
            if (recipes != null) for (var recipe : recipes) if (recipe != null) result.add(recipe);
        });
        return List.copyOf(result);
    }
}
