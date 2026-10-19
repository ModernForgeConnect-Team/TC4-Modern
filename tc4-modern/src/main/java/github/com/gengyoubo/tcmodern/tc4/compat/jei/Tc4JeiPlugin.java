package github.com.gengyoubo.tcmodern.tc4.compat.jei;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.recipe.TcRecipe;
import github.com.gengyoubo.tcmodern.api.recipe.TcRecipes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Discovered only by JEI. Common mod initialization never links any JEI or client class. */
@JeiPlugin
public final class Tc4JeiPlugin implements IModPlugin {
    static final RecipeType<JeiTcRecipe> ARCANE = RecipeType.create("tc4modern", "arcane", JeiTcRecipe.class);
    static final RecipeType<JeiTcRecipe> CRUCIBLE = RecipeType.create("tc4modern", "crucible", JeiTcRecipe.class);
    static final RecipeType<JeiTcRecipe> INFUSION = RecipeType.create("tc4modern", "infusion", JeiTcRecipe.class);

    @Override public ResourceLocation getPluginUid() { return ResourceLocation.fromNamespaceAndPath("tc4modern", "jei"); }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        if (Boolean.getBoolean("tc4modern.jeiSmokeTest")) {
            github.com.gengyoubo.tcmodern.tc4.client.testing.JeiSmokeProbe.ready(index -> {
                var type = List.of(ARCANE, CRUCIBLE, INFUSION).get(index);
                var manager = runtime.getRecipeManager();
                var recipes = manager.createRecipeLookup(type).get().toList();
                if (recipes.isEmpty()) throw new IllegalStateException("No JEI recipes for " + type);
                var category = manager.getRecipeCategory(type);
                var focus = runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup();
                for (var recipe : recipes) {
                    if (manager.createRecipeLayoutDrawable(category, recipe, focus).isEmpty())
                        throw new IllegalStateException("JEI could not build layout for " + recipe.source().id());
                    var factory = runtime.getJeiHelpers().getFocusFactory();
                    var output = factory.createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, recipe.output());
                    if (manager.createRecipeLookup(type).limitFocus(List.of(output)).get()
                            .noneMatch(found -> found.source().id().equals(recipe.source().id())))
                        throw new IllegalStateException("JEI recipe lookup failed for " + recipe.source().id());
                    var first = recipe.inputs().stream().filter(slot -> !slot.isEmpty()).findFirst().orElseThrow().get(0);
                    var input = factory.createFocus(RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, first);
                    if (manager.createRecipeLookup(type).limitFocus(List.of(input)).get()
                            .noneMatch(found -> found.source().id().equals(recipe.source().id())))
                        throw new IllegalStateException("JEI usage lookup failed for " + recipe.source().id());
                }
                runtime.getRecipesGui().showTypes(List.of(type));
                return recipes.size();
            });
        }
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        TcModernApi.runtime().ifPresent(runtime -> {
            Set<Item> registered = new HashSet<>();
            var converter = new JeiRecipeConverter(runtime, List.of(), Set.of());
            for (String key : runtime.registryKeys(LegacyRuntime.Registry.ITEM)) {
                if (!key.startsWith(TcModernApi.LEGACY_MOD_ID + ":")) continue;
                converter.map(new LegacyStack(key, 0, 1, "")).ifPresent(stack -> {
                    if (registered.add(stack.getItem())) registration.registerSubtypeInterpreter(stack.getItem(),
                            (ingredient, context) -> subtype(ingredient));
                });
            }
        });
    }

    private static String subtype(ItemStack stack) {
        return TcModernApi.runtime().map(runtime -> {
            try {
                // Presentation and durability are not subtype identity. Keep all original NBT opaque.
                var clean = stack.copy();
                if (clean.hasTag()) {
                    clean.getTag().remove("display");
                    clean.getTag().remove("RepairCost");
                }
                return runtime.mappings().toLegacy(LegacyMappings.ITEM_STACK, clean).map(runtime::snapshot)
                        .map(old -> (stack.isDamageableItem() ? 0 : old.metadata()) + ":" + old.snbt())
                        .orElse(IIngredientSubtypeInterpreter.NONE);
            } catch (RuntimeException unsupported) {
                return IIngredientSubtypeInterpreter.NONE;
            }
        }).orElse(IIngredientSubtypeInterpreter.NONE);
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        var helper = registration.getJeiHelpers().getGuiHelper();
        // Generic vanilla icons avoid requiring original assets during category discovery.
        registration.addRecipeCategories(
                new TcRecipeCategory(ARCANE, "jei.tc4modern.arcane", helper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(Items.CRAFTING_TABLE))),
                new TcRecipeCategory(CRUCIBLE, "jei.tc4modern.crucible", helper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(Items.CAULDRON))),
                new TcRecipeCategory(INFUSION, "jei.tc4modern.infusion", helper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(Items.END_CRYSTAL))));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var available = TcModernApi.runtime();
        if (available.isEmpty()) {
            LogUtils.getLogger().info("TC4 JEI: original Thaumcraft runtime is not ready; no TC4 recipes registered.");
            return;
        }
        var runtime = available.get();
        var snapshot = TcRecipes.snapshot(runtime);
        Set<String> wildcards = snapshot.recipes().stream().flatMap(recipe -> recipe.inputs().stream())
                .flatMap(List::stream).filter(stack -> stack.metadata() == 32767).map(LegacyStack::key).collect(Collectors.toSet());
        var converter = new JeiRecipeConverter(runtime, registration.getIngredientManager().getAllItemStacks(), wildcards);
        Map<RecipeType<JeiTcRecipe>, List<JeiTcRecipe>> recipes = new LinkedHashMap<>();
        recipes.put(ARCANE, new ArrayList<>());
        recipes.put(CRUCIBLE, new ArrayList<>());
        recipes.put(INFUSION, new ArrayList<>());
        int unmapped = 0;
        for (var recipe : snapshot.recipes()) {
            try {
                var converted = converter.convert(recipe);
                recipes.get(switch (recipe.kind()) {
                    case ARCANE_SHAPED, ARCANE_SHAPELESS -> ARCANE;
                    case CRUCIBLE -> CRUCIBLE;
                    case INFUSION -> INFUSION;
                }).add(converted);
            } catch (RuntimeException failure) {
                unmapped++;
                LogUtils.getLogger().debug("TC4 JEI omitted {}: {}", recipe.id(), failure.toString());
            }
        }
        recipes.forEach(registration::addRecipes);
        LogUtils.getLogger().info("TC4 JEI: arcane={}, crucible={}, infusion={}, unsupported={}, unmapped={}.",
                recipes.get(ARCANE).size(), recipes.get(CRUCIBLE).size(), recipes.get(INFUSION).size(), snapshot.issues().size(), unmapped);
        snapshot.issues().forEach(issue -> LogUtils.getLogger().debug("TC4 JEI source {}: {}", issue.source(), issue.detail()));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        TcModernApi.runtime().ifPresent(runtime -> {
            var converter = new JeiRecipeConverter(runtime, List.of(), Set.of());
            converter.map(new LegacyStack("Thaumcraft:blockTable", 15, 1, ""))
                    .ifPresent(stack -> registration.addRecipeCatalyst(stack, ARCANE));
            converter.map(new LegacyStack("Thaumcraft:blockMetalDevice", 0, 1, ""))
                    .ifPresent(stack -> registration.addRecipeCatalyst(stack, CRUCIBLE));
            converter.map(new LegacyStack("Thaumcraft:blockStoneDevice", 2, 1, ""))
                    .ifPresent(stack -> registration.addRecipeCatalyst(stack, INFUSION));
        });
    }
}
