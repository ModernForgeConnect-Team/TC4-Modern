package github.com.gengyoubo.tcmodern.api.recipe;

import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import static github.com.gengyoubo.tcmodern.api.recipe.TcRecipeAccessors.*;

/** Reads supported static recipes exclusively through MFCL's public MLA API. */
public final class TcRecipes {
    private TcRecipes() { }

    public static TcRecipeSnapshot snapshot(LegacyRuntime runtime) {
        if (!runtime.isOpen() || !runtime.loadedMods().contains(TcModernApi.LEGACY_MOD_ID))
            return new TcRecipeSnapshot(List.of(), List.of());
        var reader = new Reader(runtime);
        var issues = new ArrayList<TcRecipeSnapshot.Issue>();
        var recipes = new LinkedHashMap<String, TcRecipe>();
        LegacyObject[] originals;
        try {
            originals = reader.list(runtime.accessor(Registry.class).recipes());
        } catch (RuntimeException failure) {
            return new TcRecipeSnapshot(List.of(), List.of(new TcRecipeSnapshot.Issue("registry", failure.toString())));
        }
        for (LegacyObject original : originals) {
            try {
                var recipe = reader.read(original);
                recipes.putIfAbsent(recipe.id(), recipe);
            } catch (RuntimeException failure) {
                issues.add(new TcRecipeSnapshot.Issue(original == null ? "null" : original.typeName(), failure.toString()));
            }
        }
        return new TcRecipeSnapshot(List.copyOf(recipes.values()), issues);
    }

    private static final class Reader {
        private final LegacyRuntime runtime;
        Reader(LegacyRuntime runtime) { this.runtime = runtime; }

        LegacyObject[] list(LegacyObject collection) {
            return runtime.values().list(collection).stream().map(LegacyObject.class::cast).toArray(LegacyObject[]::new);
        }

        TcRecipe read(LegacyObject original) {
            return switch (original.typeName()) {
                case "thaumcraft.api.crafting.ShapedArcaneRecipe" -> {
                    var shaped = runtime.accessor(Shaped.class, original);
                    var arcane = runtime.accessor(Arcane.class, original);
                    yield new TcRecipe(TcRecipe.Kind.ARCANE_SHAPED, arcane.research(), shaped.width(), shaped.height(),
                            ingredients(shaped.inputs(), true), stack(arcane.output()), aspects(arcane.aspects()), 0);
                }
                case "thaumcraft.api.crafting.ShapelessArcaneRecipe" -> {
                    var arcane = runtime.accessor(Arcane.class, original);
                    var inputs = list(runtime.accessor(Shapeless.class, original).inputs());
                    if (inputs.length > 9) throw new IllegalArgumentException("Shapeless grid exceeds 9 slots");
                    yield new TcRecipe(TcRecipe.Kind.ARCANE_SHAPELESS, arcane.research(), inputs.length, 1,
                            ingredients(inputs, false), stack(arcane.output()), aspects(arcane.aspects()), 0);
                }
                case "thaumcraft.api.crafting.CrucibleRecipe" -> {
                    var crucible = runtime.accessor(Crucible.class, original);
                    yield new TcRecipe(TcRecipe.Kind.CRUCIBLE, crucible.research(), 1, 1,
                            ingredients(new LegacyObject[]{crucible.catalyst()}, false), stack(crucible.output()),
                            aspects(crucible.aspects()), 0);
                }
                case "thaumcraft.api.crafting.InfusionRecipe" -> {
                    var infusion = runtime.accessor(Infusion.class, original);
                    var inputs = new ArrayList<LegacyObject>();
                    inputs.add(infusion.central());
                    inputs.addAll(Arrays.asList(infusion.components()));
                    yield new TcRecipe(TcRecipe.Kind.INFUSION, infusion.research(), inputs.size(), 1,
                            ingredients(inputs.toArray(LegacyObject[]::new), false), stack(infusion.output()),
                            aspects(infusion.aspects()), infusion.instability());
                }
                default -> throw new UnsupportedOperationException("Dynamic or unsupported recipe type; no static display is inferred");
            };
        }

        LegacyStack stack(LegacyObject value) {
            if (value == null || !value.typeName().equals("net.minecraft.item.ItemStack"))
                throw new UnsupportedOperationException("Expected a concrete legacy ItemStack");
            return runtime.snapshot(value);
        }

        List<List<LegacyStack>> ingredients(LegacyObject[] values, boolean allowHoles) {
            var inputs = new ArrayList<List<LegacyStack>>();
            for (var value : values) {
                if (value == null && allowHoles) inputs.add(List.of());
                else if (value == null) throw new IllegalArgumentException("Missing required ingredient");
                else if (value.typeName().equals("net.minecraft.item.ItemStack")) inputs.add(List.of(stack(value)));
                else {
                    // TC4's recipe constructors expand ore dictionary names to collections.
                    var alternatives = Arrays.stream(list(value)).map(this::stack).distinct().toList();
                    if (alternatives.isEmpty()) throw new IllegalArgumentException("Empty ore dictionary ingredient");
                    inputs.add(alternatives);
                }
            }
            return inputs;
        }

        List<TcRecipe.AspectCost> aspects(LegacyObject value) {
            if (value == null) return List.of();
            var list = runtime.accessor(Aspects.class, value);
            var result = new ArrayList<TcRecipe.AspectCost>();
            for (var aspect : list.values()) {
                int amount = list.amount(aspect);
                if (amount == 0) continue;
                var detail = runtime.accessor(Aspect.class, aspect);
                var image = detail.image();
                String texture = "";
                if (image != null) {
                    var resource = runtime.accessor(Resource.class, image);
                    texture = resource.namespace() + ":" + resource.path();
                }
                result.add(new TcRecipe.AspectCost(detail.tag(), detail.color(), amount, texture));
            }
            return result;
        }
    }
}
