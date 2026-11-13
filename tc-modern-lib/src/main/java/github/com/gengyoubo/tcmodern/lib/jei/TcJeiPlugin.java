package github.com.gengyoubo.tcmodern.lib.jei;

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

/** Discovered only by JEI. Common mod initialization never links any JEI or client class. */
@JeiPlugin
public final class TcJeiPlugin implements IModPlugin {
    public static final RecipeType<JeiTcRecipe> ARCANE = RecipeType.create("tc4modern", "arcane", JeiTcRecipe.class);
    public static final RecipeType<JeiTcRecipe> CRUCIBLE = RecipeType.create("tc4modern", "crucible", JeiTcRecipe.class);
    public static final RecipeType<JeiTcRecipe> INFUSION = RecipeType.create("tc4modern", "infusion", JeiTcRecipe.class);

    @Override public ResourceLocation getPluginUid() { return ResourceLocation.fromNamespaceAndPath("tc4modern", "jei"); }

    /** Opt-in standalone acceptance; runs without loading TC4: Modern. */
    @Override public void onRuntimeAvailable(IJeiRuntime runtime) {
        if (!Boolean.getBoolean("tcmodernlib.jeiSmokeTest")) return;
        try {
            if (net.minecraftforge.fml.ModList.get().isLoaded("tc4modern")) throw new IllegalStateException("TC4: Modern must be absent");
            var manager = runtime.getRecipeManager();
            for (var type : List.of(ARCANE, CRUCIBLE, INFUSION)) {
                var recipes = manager.createRecipeLookup(type).get().toList();
                if (recipes.isEmpty()) throw new IllegalStateException("No original recipes: " + type);
                var category = manager.getRecipeCategory(type);
                if (manager.createRecipeLayoutDrawable(category, recipes.get(0), runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).isEmpty())
                    throw new IllegalStateException("Cannot render " + type);
            }
            if (manager.createRecipeLookup(AspectCompositionCategory.TYPE).get().findAny().isEmpty())
                throw new IllegalStateException("No aspect compositions");
            var tc = TcModernApi.runtime().orElseThrow();
            var stick = new LegacyStack("minecraft:stick", 0, 1, "");
            String rod = github.com.gengyoubo.tcmodern.api.wand.TcWandPartRegistrar.rod(tc, "tcmodernlib_smoke", 25, stick, 1, false, null);
            String staff = github.com.gengyoubo.tcmodern.api.wand.TcWandPartRegistrar.rod(tc, "tcmodernlib_smoke", 40, stick, 1, true, null);
            String cap = github.com.gengyoubo.tcmodern.api.wand.TcWandPartRegistrar.cap(tc, "tcmodernlib_smoke", .9f, List.of("aer"), .8f, stick, 1, null);
            var rods = github.com.gengyoubo.tcmodern.api.wand.TcWandParts.rods(tc);
            if (rods.stream().noneMatch(r -> r.tag().equals(rod) && r.capacity() == 25)
                    || rods.stream().noneMatch(r -> r.tag().equals(staff) && r.staff())
                    || github.com.gengyoubo.tcmodern.api.wand.TcWandParts.caps(tc).stream().noneMatch(c -> c.tag().equals(cap) && c.specialAspects().contains("aer")))
                throw new IllegalStateException("Registered wand parts not found");
            var path = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("lib-jei-smoke.txt");
            java.nio.file.Files.writeString(path, "PASS");
        } catch (Throwable failure) {
            try {
                var out = new java.io.StringWriter(); failure.printStackTrace(new java.io.PrintWriter(out));
                java.nio.file.Files.writeString(net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("lib-jei-smoke.txt"), out.toString());
            } catch (Exception ignored) { }
        }
        net.minecraft.client.Minecraft.getInstance().execute(() -> net.minecraft.client.Minecraft.getInstance().stop());
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        TcModernApi.runtime().ifPresent(runtime -> {
            Set<Item> registered = new HashSet<>();
            Set<ResourceLocation> originals = new HashSet<>();
            var converter = new JeiRecipeConverter(runtime, List.of(), Set.of());
            for (String key : runtime.registryKeys(LegacyRuntime.Registry.ITEM)) {
                if (!key.startsWith(TcModernApi.LEGACY_MOD_ID + ":")) continue;
                converter.map(new LegacyStack(key, 0, 1, "")).ifPresent(stack -> {
                    if (!registered.add(stack.getItem())) return;
                    registration.registerSubtypeInterpreter(stack.getItem(), (ingredient, context) -> subtype(ingredient));
                    originals.add(original(BuiltInRegistries.ITEM.getKey(stack.getItem())));
                });
            }
            // MFCL registers each split of an original item as <original id>/<variant>; metadata 0 reaches only one of them.
            for (var item : BuiltInRegistries.ITEM) {
                if (originals.contains(original(BuiltInRegistries.ITEM.getKey(item))) && registered.add(item))
                    registration.registerSubtypeInterpreter(item, (ingredient, context) -> subtype(ingredient));
            }
        });
    }

    /** The original host item ID of a split item ({@code thaumcraft:item_resource/meta_2}); other IDs unchanged. */
    static ResourceLocation original(ResourceLocation id) {
        int slash = id.getPath().indexOf('/');
        return slash < 0 ? id : ResourceLocation.fromNamespaceAndPath(id.getNamespace(), id.getPath().substring(0, slash));
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
                        .map(old -> {
                            // Every captured node has different aspects/id. All use the same jar-building recipe.
                            if (old.key().equals("Thaumcraft:blockJar") && old.metadata() == 2) return "2:node_jar";
                            if (old.key().equals(WAND)) return wandSubtype(old.snbt());
                            return (stack.isDamageableItem() ? 0 : old.metadata()) + ":" + old.snbt();
                        })
                        .orElse(IIngredientSubtypeInterpreter.NONE);
            } catch (RuntimeException unsupported) {
                return IIngredientSubtypeInterpreter.NONE;
            }
        }).orElse(IIngredientSubtypeInterpreter.NONE);
    }

    private static final Pattern WAND_PART = Pattern.compile("[{,]\\s*(cap|rod|sceptre)\\s*:\\s*\"?([^\",}]*)");

    /**
     * Wands are split into one host item per build ({@code Tc4ItemSplits}); a build no split covers stays on the original
     * host item. Either way the damage (the craft cost the wand recipes store), stored vis and focus are not identity.
     */
    static String wandSubtype(String snbt) {
        String cap = "", rod = "";
        boolean sceptre = false;
        var parts = WAND_PART.matcher(snbt);
        while (parts.find()) {
            switch (parts.group(1)) {
                case "cap" -> cap = parts.group(2);
                case "rod" -> rod = parts.group(2);
                default -> sceptre = !parts.group(2).matches("0[bB]?");
            }
        }
        return "wand:" + cap + "/" + rod + (sceptre ? "/sceptre" : "");
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
                new ThaumometerCategory(helper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(Items.SPYGLASS))),
                new WandConversionCategory(helper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(Items.STICK))),
                new ItemInFluidCategory(helper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(Items.WATER_BUCKET))),
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
        var scanned = scanRecipes(runtime, registration.getIngredientManager().getAllItemStacks());
        registration.addRecipes(ThaumometerCategory.TYPE, scanned);
        LogUtils.getLogger().info("TC4 JEI: arcane={}, crucible={}, infusion={}, aspects={}, unsupported={}, unmapped={}.",
                recipes.get(ARCANE).size(), recipes.get(CRUCIBLE).size(), recipes.get(INFUSION).size(), compounds.size(), snapshot.issues().size(), unmapped);
        var wand = wandConversions(runtime, registration.getIngredientManager().getAllItemStacks());
        registration.addRecipes(WandConversionCategory.TYPE, wand);
        var fluid = itemInFluidRecipes(converter);
        registration.addRecipes(ItemInFluidCategory.TYPE, fluid);
        var multiblocks = Tc4Multiblocks.all(runtime, new JeiRecipeConverter(runtime, List.of(), Set.of()),
                wands(runtime, registration.getIngredientManager().getAllItemStacks()));
        registration.addRecipes(MultiblockCategory.TYPE, multiblocks);
        LogUtils.getLogger().info("TC4 JEI: thaumometer={} items with aspects, wand conversions={}, multiblocks={}, item in fluid={}.",
                scanned.size(), wand.size(), multiblocks.size(), fluid.size());
        snapshot.issues().forEach(issue -> LogUtils.getLogger().debug("TC4 JEI source {}: {}", issue.source(), issue.detail()));
    }

    /** Original ItemBathSalts expires after 200 ticks; EventHandlerEntity turns still source water into fluidPure. */
    private static List<ItemInFluidCategory.Recipe> itemInFluidRecipes(JeiRecipeConverter converter) {
        return converter.map(new LegacyStack("Thaumcraft:ItemBathSalts", 0, 1, ""))
                .flatMap(salt -> converter.map(new LegacyStack("Thaumcraft:blockFluidPure", 0, 1, ""))
                        .map(result -> new ItemInFluidCategory.Recipe(salt, result)))
                .map(List::of).orElseGet(List::of);
    }

    /** Every listed item with an original counterpart, with the aspects TC4 reports for it. */
    private static List<ThaumometerCategory.Recipe> scanRecipes(LegacyRuntime runtime, java.util.Collection<ItemStack> stacks) {
        var result = new ArrayList<ThaumometerCategory.Recipe>();
        for (var stack : stacks) {
            try {
                var original = runtime.mappings().toLegacy(LegacyMappings.ITEM_STACK, stack.copyWithCount(1));
                if (original.isEmpty()) continue;
                TcItemAspects.of(runtime, original.get()).ifPresent(values -> {
                    if (values.amounts().size() > ThaumometerCategory.MAX_ESSENTIA) return;
                    var essentia = values.amounts().entrySet().stream().map(entry ->
                            JeiAspect.of(entry.getKey()).withAmount(entry.getValue())).toList();
                    result.add(new ThaumometerCategory.Recipe(stack, essentia));
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
    public static List<ItemStack> wands(LegacyRuntime runtime, java.util.Collection<ItemStack> stacks) {
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
            converter.map(new LegacyStack("Thaumcraft:ItemThaumometer", 0, 1, ""))
                    .ifPresent(stack -> registration.addRecipeCatalyst(stack, ThaumometerCategory.TYPE));
            converter.map(IRON_WOOD_WAND).ifPresent(stack -> {
                registration.addRecipeCatalyst(stack, WandConversionCategory.TYPE);
                registration.addRecipeCatalyst(stack, MultiblockCategory.TYPE);
            });
            converter.map(new LegacyStack("Thaumcraft:ItemInkwell", 0, 1, ""))
                    .ifPresent(stack -> registration.addRecipeCatalyst(stack, MultiblockCategory.TYPE));
        });
    }
}
