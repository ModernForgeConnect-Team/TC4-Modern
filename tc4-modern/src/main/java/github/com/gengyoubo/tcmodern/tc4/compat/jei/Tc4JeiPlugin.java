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
                    if (!recipe.source().aspects().isEmpty()) {
                        var cost = factory.createFocus(RecipeIngredientRole.INPUT, JeiAspect.TYPE, JeiAspect.of(recipe.source().aspects().get(0).tag()));
                        if (manager.createRecipeLookup(type).limitFocus(List.of(cost)).get()
                                .noneMatch(found -> found.source().id().equals(recipe.source().id())))
                            throw new IllegalStateException("JEI aspect usage lookup failed for " + recipe.source().id());
                    }
                }
                if (index == 0) {
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
    public void registerIngredients(IModIngredientRegistration registration) {
        List<TcAspect> aspects = List.of();
        var runtime = TcModernApi.runtime();
        if (runtime.isPresent()) {
            try {
                aspects = TcAspects.snapshot(runtime.get());
            } catch (RuntimeException failure) {
                LogUtils.getLogger().warn("TC4 JEI: cannot read the original aspects; aspects are not clickable.", failure);
            }
        }
        JeiAspect.catalog(aspects);
        // Registered even when empty: recipe slots always use this type.
        registration.register(JeiAspect.TYPE, aspects.stream().map(aspect -> new JeiAspect(aspect, 0)).toList(),
                new JeiAspect.Helper(), new JeiAspect.Renderer());
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        var helper = registration.getJeiHelpers().getGuiHelper();
        // Generic vanilla icons avoid requiring original assets during category discovery.
        registration.addRecipeCategories(
                new TcRecipeCategory(ARCANE, "jei.tc4modern.arcane", helper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(Items.CRAFTING_TABLE))),
                new TcRecipeCategory(CRUCIBLE, "jei.tc4modern.crucible", helper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(Items.CAULDRON))),
                new TcRecipeCategory(INFUSION, "jei.tc4modern.infusion", helper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(Items.END_CRYSTAL))),
                new AspectCompositionCategory(),
                new AlchemyFurnaceCategory(helper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(Items.FURNACE))),
                new WandConversionCategory(helper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(Items.STICK))),
                new MultiblockCategory(helper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(Items.BRICKS))));
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
        var compounds = JeiAspect.all().stream().filter(aspect -> aspect.components().size() == 2).toList();
        registration.addRecipes(AspectCompositionCategory.TYPE, compounds);
        var furnace = furnaceRecipes(runtime, registration.getIngredientManager().getAllItemStacks());
        registration.addRecipes(AlchemyFurnaceCategory.TYPE, furnace);
        LogUtils.getLogger().info("TC4 JEI: arcane={}, crucible={}, infusion={}, aspects={}, unsupported={}, unmapped={}.",
                recipes.get(ARCANE).size(), recipes.get(CRUCIBLE).size(), recipes.get(INFUSION).size(), compounds.size(), snapshot.issues().size(), unmapped);
        var wand = wandConversions(runtime, registration.getIngredientManager().getAllItemStacks());
        registration.addRecipes(WandConversionCategory.TYPE, wand);
        var multiblocks = Tc4Multiblocks.all(runtime, new JeiRecipeConverter(runtime, List.of(), Set.of()),
                wands(runtime, registration.getIngredientManager().getAllItemStacks()));
        registration.addRecipes(MultiblockCategory.TYPE, multiblocks);
        LogUtils.getLogger().info("TC4 JEI: alchemical furnace={} items with essentia, wand conversions={}, multiblocks={}.",
                furnace.size(), wand.size(), multiblocks.size());
        snapshot.issues().forEach(issue -> LogUtils.getLogger().debug("TC4 JEI source {}: {}", issue.source(), issue.detail()));
    }

    /** Every listed item the original alchemical furnace accepts, with the essentia TC4 gives it. */
    private static List<AlchemyFurnaceCategory.Recipe> furnaceRecipes(LegacyRuntime runtime, java.util.Collection<ItemStack> stacks) {
        var result = new ArrayList<AlchemyFurnaceCategory.Recipe>();
        for (var stack : stacks) {
            try {
                var original = runtime.mappings().toLegacy(LegacyMappings.ITEM_STACK, stack.copyWithCount(1));
                if (original.isEmpty()) continue;
                TcItemAspects.of(runtime, original.get()).ifPresent(values -> {
                    if (values.amounts().size() > AlchemyFurnaceCategory.MAX_ESSENTIA) return;
                    var essentia = values.amounts().entrySet().stream().map(entry ->
                            JeiAspect.of(entry.getKey()).withAmount(entry.getValue())).toList();
                    result.add(new AlchemyFurnaceCategory.Recipe(stack, essentia));
                });
            } catch (RuntimeException unreadable) {
                LogUtils.getLogger().debug("TC4 JEI: no essentia for {}: {}", stack, unreadable.toString());
            }
        }
        return result;
    }

    private static final String WAND = "Thaumcraft:WandCasting";
    private static final LegacyStack IRON_WOOD_WAND = new LegacyStack(WAND, 0, 1, "{cap:\"iron\",rod:\"wood\"}");

    /** Every listed wand, plus TC4's starting iron-capped wooden wand when JEI lists none. */
    private static List<ItemStack> wands(LegacyRuntime runtime, java.util.Collection<ItemStack> stacks) {
        var result = new ArrayList<ItemStack>();
        for (var stack : stacks) {
            try {
                runtime.mappings().toLegacy(LegacyMappings.ITEM_STACK, stack.copyWithCount(1)).map(runtime::snapshot)
                        .filter(old -> old.key().equals(WAND)).ifPresent(old -> result.add(stack.copyWithCount(1)));
            } catch (RuntimeException unsupported) {
                // Modern-only stacks have no legacy form and are never wands.
            }
        }
        if (result.isEmpty()) new JeiRecipeConverter(runtime, List.of(), Set.of()).map(IRON_WOOD_WAND).ifPresent(result::add);
        return result;
    }

    /** The single-block wand conversions: BlockTable's arcane worktable and WandManager triggers 0 and 1. */
    private static List<WandConversionCategory.Recipe> wandConversions(LegacyRuntime runtime, java.util.Collection<ItemStack> stacks) {
        var wands = wands(runtime, stacks);
        if (wands.isEmpty()) return List.of();
        var converter = new JeiRecipeConverter(runtime, List.of(), Set.of());
        var result = new ArrayList<WandConversionCategory.Recipe>();
        converter.map(new LegacyStack("Thaumcraft:blockTable", 0, 1, "")).ifPresent(table ->
                converter.map(new LegacyStack("Thaumcraft:blockTable", 15, 1, "")).ifPresent(worktable ->
                        result.add(new WandConversionCategory.Recipe(table, wands, worktable, "jei.tc4modern.wand.worktable"))));
        converter.map(new LegacyStack("Thaumcraft:blockMetalDevice", 0, 1, "")).ifPresent(crucible ->
                result.add(new WandConversionCategory.Recipe(new ItemStack(Items.CAULDRON), wands, crucible, "jei.tc4modern.wand.any")));
        converter.map(new LegacyStack("Thaumcraft:ItemThaumonomicon", 0, 1, "")).ifPresent(book ->
                result.add(new WandConversionCategory.Recipe(new ItemStack(Items.BOOKSHELF), wands, book, "jei.tc4modern.wand.any")));
        return result;
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
            converter.map(new LegacyStack("Thaumcraft:blockStoneDevice", 0, 1, ""))
                    .ifPresent(stack -> registration.addRecipeCatalyst(stack, AlchemyFurnaceCategory.TYPE));
            converter.map(IRON_WOOD_WAND).ifPresent(stack -> {
                registration.addRecipeCatalyst(stack, WandConversionCategory.TYPE);
                registration.addRecipeCatalyst(stack, MultiblockCategory.TYPE);
            });
            converter.map(new LegacyStack("Thaumcraft:ItemInkwell", 0, 1, ""))
                    .ifPresent(stack -> registration.addRecipeCatalyst(stack, MultiblockCategory.TYPE));
        });
    }
}
