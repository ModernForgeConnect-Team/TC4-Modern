package github.com.gengyoubo.tcmodern.api.recipe;

import github.com.gengyoubo.mfcl.api.LegacyFeatures;
import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspectAccessors.AspectLookup;
import java.util.ArrayList;
import java.util.Map;
import static github.com.gengyoubo.tcmodern.api.recipe.TcRecipeAccessors.*;

/**
 * Adds new original TC4 recipes through the original's own {@code ThaumcraftApi.add*} entry points, so matching,
 * research gating and {@link TcRecipes} snapshots treat them exactly like built-in ones. Add only: original recipes
 * are never removed or replaced. Register once per runtime (an integrated server shares the client's runtime), after
 * the aspects and research the recipe names exist.
 */
public final class TcRecipeRegistrar {
    private TcRecipeRegistrar() { }

    /**
     * Validates everything (research, aspects, items, ore names) before the original registration is called, so a
     * rejected definition leaves the original recipe list unchanged.
     *
     * @throws IllegalArgumentException when the research or an aspect is unknown, or an ore name has no stacks
     * @throws IllegalStateException    when the runtime is closed, Thaumcraft is absent, or MFCL cannot pass
     *                                  String/Character values to the original's {@code Object} parameters
     */
    public static void register(LegacyRuntime runtime, TcRecipeDefinition definition) {
        if (!runtime.isOpen() || !runtime.loadedMods().contains(TcModernApi.LEGACY_MOD_ID))
            throw new IllegalStateException("Thaumcraft runtime is not available");
        if (!runtime.supports(LegacyFeatures.MLA_OBJECT_ARGUMENTS))
            throw new IllegalStateException("This MFCL build cannot pass recipe inputs to the original (needs " + LegacyFeatures.MLA_OBJECT_ARGUMENTS + ")");
        if (runtime.accessor(ResearchLookup.class).find(definition.research()) == null)
            throw new IllegalArgumentException("Unknown research: " + definition.research());
        var aspects = aspects(runtime, definition.aspects());
        var registration = runtime.accessor(Registration.class);
        if (definition instanceof TcRecipeDefinition.ArcaneShaped shaped) {
            var recipe = new ArrayList<Object>(shaped.pattern());
            shaped.key().forEach((symbol, ingredient) -> {
                recipe.add(symbol);
                recipe.add(ingredient(runtime, ingredient));
            });
            var result = stack(runtime, shaped.result());
            var created = registration.arcane(shaped.research(), result, aspects, recipe.toArray());
            // The original defaults to mirrored.
            if (!shaped.mirrored()) runtime.accessor(Shaped.class, created).mirrored(false);
        } else if (definition instanceof TcRecipeDefinition.ArcaneShapeless shapeless) {
            var recipe = shapeless.ingredients().stream().map(ingredient -> ingredient(runtime, ingredient)).toArray();
            registration.shapeless(shapeless.research(), stack(runtime, shapeless.result()), aspects, recipe);
        } else if (definition instanceof TcRecipeDefinition.Crucible crucible) {
            var catalyst = ingredient(runtime, crucible.catalyst());
            registration.crucible(crucible.research(), stack(runtime, crucible.result()), catalyst, aspects);
        } else if (definition instanceof TcRecipeDefinition.Infusion infusion) {
            var input = stack(runtime, infusion.input());
            var components = infusion.components().stream().map(component -> stack(runtime, component)).toArray(LegacyObject[]::new);
            registration.infusion(infusion.research(), stack(runtime, infusion.result()), infusion.instability(), aspects, input, components);
        } else {
            throw new IllegalArgumentException("Unsupported recipe definition: " + definition.getClass().getName());
        }
    }

    private static LegacyObject aspects(LegacyRuntime runtime, Map<String, Integer> costs) {
        var lookup = runtime.accessor(AspectLookup.class);
        var resolved = new ArrayList<Map.Entry<LegacyObject, Integer>>();
        costs.forEach((tag, amount) -> {
            var aspect = lookup.find(tag);
            if (aspect == null) throw new IllegalArgumentException("Unknown aspect: " + tag);
            resolved.add(Map.entry(aspect, amount));
        });
        var list = runtime.accessor(AspectListFactory.class).create();
        var editor = runtime.accessor(Aspects.class, list);
        for (var cost : resolved) editor.add(cost.getKey(), cost.getValue());
        return list;
    }

    /** An original ItemStack, or the ore name itself: the original constructors expand names to their live stacks. */
    private static Object ingredient(LegacyRuntime runtime, TcRecipeDefinition.Ingredient ingredient) {
        if (ingredient instanceof TcRecipeDefinition.Ingredient.Ore ore) {
            if (runtime.values().size(runtime.accessor(Ores.class).stacks(ore.name())) == 0)
                throw new IllegalArgumentException("Ore dictionary name has no stacks: " + ore.name());
            return ore.name();
        }
        return stack(runtime, ((TcRecipeDefinition.Ingredient.Item) ingredient).stack());
    }

    private static LegacyObject stack(LegacyRuntime runtime, LegacyStack stack) {
        try {
            return runtime.stack(stack);
        } catch (RuntimeException unknown) {
            throw new IllegalArgumentException("Unknown original item: " + stack.key(), unknown);
        }
    }
}
