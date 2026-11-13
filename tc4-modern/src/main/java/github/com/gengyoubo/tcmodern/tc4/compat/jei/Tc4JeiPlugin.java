package github.com.gengyoubo.tcmodern.tc4.compat.jei;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspect;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspects;
import github.com.gengyoubo.tcmodern.api.aspect.TcItemAspects;
import github.com.gengyoubo.tcmodern.api.recipe.TcRecipe;
import github.com.gengyoubo.tcmodern.api.recipe.TcRecipes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.registration.IModIngredientRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;


import github.com.gengyoubo.tcmodern.lib.jei.*;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Blocks;

@JeiPlugin
public final class Tc4JeiPlugin implements IModPlugin {
    static final RecipeType<JeiTcRecipe> ARCANE = TcJeiPlugin.ARCANE, CRUCIBLE = TcJeiPlugin.CRUCIBLE, INFUSION = TcJeiPlugin.INFUSION;
    @Override public ResourceLocation getPluginUid() { return ResourceLocation.fromNamespaceAndPath("tc4modern", "jei_addon"); }
    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        if (Boolean.getBoolean("tc4modern.jeiSmokeTest")) {
            github.com.gengyoubo.tcmodern.tc4.client.testing.JeiSmokeProbe.ready(index -> {
                if (index == 3) return checkItemInFluid(runtime);
                if (index == 4) return checkMultiblocks(runtime);
                if (index == 5) return checkModernDeskUpgrade(runtime);
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
                    if (!recipe.source().aspects().isEmpty()) {
                        var cost = factory.createFocus(RecipeIngredientRole.INPUT, JeiAspect.TYPE, JeiAspect.of(recipe.source().aspects().get(0).tag()));
                        if (manager.createRecipeLookup(type).limitFocus(List.of(cost)).get()
                                .noneMatch(found -> found.source().id().equals(recipe.source().id())))
                            throw new IllegalStateException("JEI aspect usage lookup failed for " + recipe.source().id());
                    }
                }
                if (index == 0) {
                    var cabinet = recipes.stream().filter(r -> r.output().is(github.com.gengyoubo.tcmodern.tc4.init.Tc4Blocks.ARCHIVE_CABINET.get().asItem()))
                            .findFirst().orElseThrow(() -> new IllegalStateException("Missing archive cabinet arcane recipe"));
                    if (!cabinet.source().research().equals("modern2") || cabinet.source().width() != 3 || cabinet.source().height() != 3
                            || cabinet.source().aspects().stream().filter(a -> (a.tag().equals("terra") || a.tag().equals("ordo")) && a.amount() == 5).count() != 2
                            || cabinet.inputs().get(0).stream().noneMatch(s -> s.is(Items.CHERRY_PLANKS))
                            || cabinet.inputs().get(1).stream().noneMatch(s -> s.is(Items.CHERRY_SLAB))
                            || cabinet.inputs().get(4).stream().noneMatch(s -> s.is(Items.CHEST)))
                        throw new IllegalStateException("Archive cabinet ingredients, aspects or research are incorrect: " + cabinet.source() + " mapped=" + cabinet.inputs());
                    var tagShapeless = recipes.stream().filter(r -> r.source().kind() == github.com.gengyoubo.tcmodern.api.recipe.TcRecipe.Kind.ARCANE_SHAPELESS
                            && r.output().is(Items.AMETHYST_SHARD) && r.source().aspects().stream().anyMatch(a -> a.tag().equals("terra") && a.amount() == 42))
                            .findFirst().orElseThrow(() -> new IllegalStateException("Missing tag-based shapeless arcane recipe"));
                    if (tagShapeless.inputs().get(0).stream().noneMatch(s -> s.is(Items.IRON_INGOT))
                            || tagShapeless.inputs().get(1).stream().noneMatch(s -> s.is(Items.CHERRY_SLAB)))
                        throw new IllegalStateException("Shapeless Forge item tags did not resolve: " + tagShapeless.inputs());
                    var compounds = manager.createRecipeLookup(AspectCompositionCategory.TYPE).get().toList();
                    if (compounds.isEmpty()) throw new IllegalStateException("No aspect compositions");
                    var factory = runtime.getJeiHelpers().getFocusFactory();
                    var composition = manager.getRecipeCategory(AspectCompositionCategory.TYPE);
                    for (var compound : compounds) {
                        if (manager.createRecipeLayoutDrawable(composition, compound, focus).isEmpty())
                            throw new IllegalStateException("JEI could not build composition layout for " + compound.tag());
                        var part = factory.createFocus(RecipeIngredientRole.INPUT, JeiAspect.TYPE, JeiAspect.of(compound.components().get(0)));
                        if (manager.createRecipeLookup(AspectCompositionCategory.TYPE).limitFocus(List.of(part)).get().noneMatch(compound::equals))
                            throw new IllegalStateException("JEI composition usage lookup failed for " + compound.tag());
                    }
                }
                runtime.getRecipesGui().showTypes(List.of(type));
                return recipes.size();
            });
        }
    }

    private static int checkModernDeskUpgrade(IJeiRuntime runtime) {
        var manager = runtime.getRecipeManager();
        var type = WandConversionCategory.TYPE;
        var recipe = manager.createRecipeLookup(type).get().filter(r -> r.structure().equals("research_table"))
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing modern research desk conversion"));
        var category = manager.getRecipeCategory(type);
        var factory = runtime.getJeiHelpers().getFocusFactory();
        if (manager.createRecipeLayoutDrawable(category, recipe, factory.getEmptyFocusGroup()).isEmpty())
            throw new IllegalStateException("Cannot create modern research desk conversion layout");
        var output = factory.createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, recipe.result());
        var input = factory.createFocus(RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, recipe.block());
        for (var focus : List.of(input, output)) {
            if (manager.createRecipeLookup(type).limitFocus(List.of(focus)).get().noneMatch(recipe::equals))
                throw new IllegalStateException("Modern research desk lookup failed for " + focus);
        }
        runtime.getRecipesGui().showRecipes(category, List.of(recipe), List.of(output));
        return 1;
    }

    private static int checkItemInFluid(IJeiRuntime runtime) {
        var manager = runtime.getRecipeManager();
        var type = ItemInFluidCategory.TYPE;
        var recipes = manager.createRecipeLookup(type).get().toList();
        if (recipes.size() != 1) throw new IllegalStateException("Expected one bath salts conversion: " + recipes.size());
        var recipe = recipes.get(0);
        var factory = runtime.getJeiHelpers().getFocusFactory();
        if (manager.createRecipeLayoutDrawable(manager.getRecipeCategory(type), recipe, factory.getEmptyFocusGroup()).isEmpty())
            throw new IllegalStateException("JEI could not build bath salts layout");
        var output = factory.createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, recipe.result());
        var salt = factory.createFocus(RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, recipe.ingredient());
        var bucket = factory.createFocus(RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, new ItemStack(Items.WATER_BUCKET));
        var water = factory.createFocus(RecipeIngredientRole.INPUT, mezz.jei.api.forge.ForgeTypes.FLUID_STACK,
                new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000));
        for (var focus : List.of(output, salt, bucket, water)) {
            if (manager.createRecipeLookup(type).limitFocus(List.of(focus)).get().noneMatch(recipe::equals))
                throw new IllegalStateException("JEI bath salts lookup failed for " + focus);
        }
        runtime.getRecipesGui().showTypes(List.of(type));
        return recipes.size();
    }

    private static int checkMultiblocks(IJeiRuntime runtime) {
        var manager = runtime.getRecipeManager();
        var type = MultiblockCategory.TYPE;
        var recipes = manager.createRecipeLookup(type).get().toList();
        var expected = Set.of("research_table", "infusion_altar", "infernal_furnace", "advanced_alchemy_furnace", "thaumatorium", "node_jar");
        if (recipes.size() != 6 || !recipes.stream().map(MultiblockCategory.Recipe::id).collect(Collectors.toSet()).equals(expected))
            throw new IllegalStateException("Expected all six TC4 multiblocks");
        var factory = runtime.getJeiHelpers().getFocusFactory();
        for (var recipe : recipes) {
            if (recipe.tools().isEmpty() || recipe.results().isEmpty() || recipe.materials().stream().anyMatch(material -> material.part().items().isEmpty()))
                throw new IllegalStateException("Missing multiblock ingredients: " + recipe.id());
            if (manager.createRecipeLayoutDrawable(manager.getRecipeCategory(type), recipe, factory.getEmptyFocusGroup()).isEmpty())
                throw new IllegalStateException("Cannot create multiblock layout: " + recipe.id());
            for (var result : recipe.results()) {
                var focus = factory.createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, result);
                if (manager.createRecipeLookup(type).limitFocus(List.of(focus)).get().noneMatch(recipe::equals))
                    throw new IllegalStateException("Multiblock recipe lookup failed: " + recipe.id());
            }
            for (var material : recipe.materials()) for (var stack : material.part().items()) {
                var focus = factory.createFocus(RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, stack);
                if (manager.createRecipeLookup(type).limitFocus(List.of(focus)).get().noneMatch(recipe::equals))
                    throw new IllegalStateException("Multiblock material lookup failed: " + recipe.id());
            }
            for (var aspect : recipe.cost()) {
                var focus = factory.createFocus(RecipeIngredientRole.INPUT, JeiAspect.TYPE, aspect);
                if (manager.createRecipeLookup(type).limitFocus(List.of(focus)).get().noneMatch(recipe::equals))
                    throw new IllegalStateException("Multiblock vis lookup failed: " + recipe.id());
            }
            if (recipe.id().equals("node_jar")) {
                var api = TcModernApi.runtime().orElseThrow();
                var captured = new JeiRecipeConverter(api, List.of(), Set.of()).map(new LegacyStack("Thaumcraft:blockJar", 2, 1,
                        "{nodeAspects:[{key:\"aer\",amount:20}],nodeId:\"jei-smoke\"}")).orElseThrow();
                var focus = factory.createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, captured);
                if (manager.createRecipeLookup(type).limitFocus(List.of(focus)).get().noneMatch(recipe::equals))
                    throw new IllegalStateException("Captured-node NBT prevented the jar recipe lookup");
            }
        }
        runtime.getRecipesGui().showTypes(List.of(type));
        return recipes.size();
    }


    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        var helper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new IntermediateCrucibleCategory(helper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                new ItemStack(Tc4Blocks.INTERMEDIATE_CRUCIBLE.get()))));
    }
    @Override public void registerRecipes(IRecipeRegistration registration) {
        TcModernApi.runtime().ifPresent(runtime -> {
            var converter = new JeiRecipeConverter(runtime, registration.getIngredientManager().getAllItemStacks(), Set.of());
            registration.addRecipes(IntermediateCrucibleCategory.TYPE, IntermediateCrucibleCategory.recipes(
                    github.com.gengyoubo.tcmodern.tc4.crucible.IntermediateRecipes.all(), converter));
            var wands = TcJeiPlugin.wands(runtime, registration.getIngredientManager().getAllItemStacks());
            if (!wands.isEmpty()) converter.map(new LegacyStack("Thaumcraft:blockTable", 2, 1, "")).ifPresent(table ->
                registration.addRecipes(WandConversionCategory.TYPE, List.of(
                    new WandConversionCategory.Recipe(table, wands, new ItemStack(Tc4Blocks.RESEARCH_DESK.get()),
                            "jei.tc4modern.wand.modern_desk", "research_table"))));
        });
    }
    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(Tc4Blocks.INTERMEDIATE_CRUCIBLE.get()), IntermediateCrucibleCategory.TYPE);
    }
    @Override public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        if (TcModernApi.runtime().isEmpty()) return;
        try {
            var menu = Class.forName("github.com.gengyoubo.mfcl.content.LegacyDeviceMenu", false, Tc4JeiPlugin.class.getClassLoader()).asSubclass(AbstractContainerMenu.class);
            registration.addRecipeTransferHandler(new ArcaneTransferHandler(menu, registration.getTransferHelper()), ARCANE);
        } catch (ClassNotFoundException | ClassCastException | LinkageError unavailable) {
            LogUtils.getLogger().warn("TC4 JEI: MFCL device menu not found; arcane worktable recipe transfer is unavailable.");
        }
    }
}
