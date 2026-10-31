package github.com.gengyoubo.tcmodern.api.research;

import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspectAccessors;
import github.com.gengyoubo.tcmodern.api.recipe.TcRecipeAccessors;
import github.com.gengyoubo.tcmodern.api.recipe.TcRecipes;
import java.util.*;
import static github.com.gengyoubo.tcmodern.api.research.TcBookAccessors.*;

/**
 * Reads and extends the original Thaumonomicon: its categories, research items and pages. Everything goes through
 * MFCL's public MLA API, so the original book, research notes and research table see the same entries.
 *
 * <p>Reading works on either side. Registration changes the original static registry, which the client and the
 * integrated server share: register the same content on every side, from {@link TcModernApi#whenReady} or later,
 * and only from one thread at a time.</p>
 */
public final class TcThaumonomicon {
    /** The original metadata wildcard, accepted in recipe page outputs. */
    public static final int WILDCARD = 32767;

    private TcThaumonomicon() { }

    // ---- Reading --------------------------------------------------------------------------------------------

    /**
     * Every category in tab order with its research, sorted by row then column. Empty when the runtime is closed or
     * Thaumcraft is absent; an entry or page that cannot be read is skipped rather than hiding the rest.
     */
    public static List<TcResearchCategory> snapshot(LegacyRuntime runtime) {
        if (!available(runtime)) return List.of();
        var reader = new Reader(runtime);
        var result = new ArrayList<TcResearchCategory>();
        for (var category : runtime.values().map(runtime.accessor(TcResearchAccessors.Categories.class).categories()).entrySet()) {
            try {
                result.add(reader.category((String) category.getKey(), (LegacyObject) category.getValue()));
            } catch (RuntimeException unreadable) {
                // A malformed addon category must not hide the original ones.
            }
        }
        return List.copyOf(result);
    }

    /** One research by key, or empty. */
    public static Optional<TcResearchEntry> find(LegacyRuntime runtime, String key) {
        if (!available(runtime) || key == null) return Optional.empty();
        var item = runtime.accessor(TcResearchAccessors.Categories.class).find(key);
        if (item == null) return Optional.empty();
        try {
            return Optional.of(new Reader(runtime).entry(item));
        } catch (RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    /**
     * The research keys a player has completed, by player name; {@code "@KEY"} marks a hidden research they have
     * discovered. On a client this is the local player's synchronized copy.
     */
    public static Set<String> completed(LegacyRuntime runtime, String player) {
        if (!available(runtime)) return Set.of();
        var result = new LinkedHashSet<String>();
        for (Object key : runtime.values().list(runtime.accessor(Progress.class).completed(player)))
            if (key instanceof String text) result.add(text);
        return Collections.unmodifiableSet(result);
    }

    /** The original research difficulty: -1 buy everything, 0 buy secondary research, 1 notes for everything. */
    public static int difficulty(LegacyRuntime runtime) {
        return runtime.accessor(Options.class).difficulty();
    }

    /** Translates an original language key, or returns it unchanged. */
    public static String translate(LegacyRuntime runtime, String key) {
        if (key == null || key.isEmpty()) return "";
        try {
            return runtime.accessor(Translations.class).translate(key);
        } catch (RuntimeException unavailable) {
            return key;
        }
    }

    public static String categoryName(LegacyRuntime runtime, String category) { return translate(runtime, "tc.research_category." + category); }
    public static String name(LegacyRuntime runtime, String research) { return translate(runtime, "tc.research_name." + research); }
    public static String subtitle(LegacyRuntime runtime, String research) { return translate(runtime, "tc.research_text." + research); }

    // ---- Registration ---------------------------------------------------------------------------------------

    /** @throws IllegalArgumentException when the key is taken */
    public static void registerCategory(LegacyRuntime runtime, TcResearchDefinition.Category category) {
        require(runtime);
        var registry = runtime.accessor(CategoryRegistry.class);
        if (registry.find(category.key()) != null) throw new IllegalArgumentException("Category already registered: " + category.key());
        registry.register(category.key(), resource(runtime, category.icon()), resource(runtime, category.background()));
    }

    /**
     * Registers one research item with its pages. Parents and siblings may name research registered later.
     *
     * @throws IllegalArgumentException when the key is taken, the category or an aspect is unknown, the slot is
     *                                  occupied, or a recipe page matches no recipe; nothing is changed then
     */
    public static void register(LegacyRuntime runtime, TcResearchDefinition research) {
        require(runtime);
        var categories = runtime.accessor(CategoryRegistry.class);
        var list = categories.find(research.category());
        if (list == null) throw new IllegalArgumentException(research.key() + ": unknown category " + research.category());
        if (runtime.accessor(TcResearchAccessors.Categories.class).find(research.key()) != null)
            throw new IllegalArgumentException("Research already registered: " + research.key());
        boolean virtual = research.flags().contains(TcResearchEntry.Flag.VIRTUAL);
        if (!virtual) for (Object value : runtime.values().map(runtime.accessor(CategoryList.class, list).research()).values()) {
            var other = runtime.accessor(Research.class, (LegacyObject) value);
            if (!other.virtual() && other.column() == research.column() && other.row() == research.row())
                throw new IllegalArgumentException(research.key() + " overlaps " + other.key() + " at " + research.column() + "," + research.row());
        }
        var tags = aspectList(runtime, research.aspects(), research.key());
        var pages = new ArrayList<LegacyObject>();
        for (var page : research.pages()) pages.add(page(runtime, page, research.key()));
        var aspectTriggers = new ArrayList<LegacyObject>();
        for (String tag : research.aspectTriggers()) aspectTriggers.add(aspect(runtime, tag, research.key()));

        LegacyObject item;
        if (virtual) item = runtime.accessor(VirtualFactory.class).create(research.key(), research.category());
        else if (research.iconItem() != null) item = runtime.accessor(ResearchFactory.class).withItem(research.key(), research.category(),
                tags, research.column(), research.row(), research.complexity(), runtime.stack(research.iconItem()));
        else item = runtime.accessor(ResearchTextureFactory.class).withTexture(research.key(), research.category(),
                tags, research.column(), research.row(), research.complexity(), resource(runtime, research.iconTexture()));
        var builder = runtime.accessor(ResearchBuilder.class, item);
        for (var flag : research.flags()) switch (flag) {
            case SPECIAL -> builder.special();
            case SECONDARY -> builder.secondary();
            case ROUND -> builder.round();
            case STUB -> builder.stub();
            case CONCEALED -> builder.concealed();
            case HIDDEN -> builder.hidden();
            case LOST -> builder.lost();
            case AUTO_UNLOCK -> builder.autoUnlock();
            case VIRTUAL -> { }
        }
        if (!research.parents().isEmpty()) builder.parents(research.parents().toArray(String[]::new));
        if (!research.hiddenParents().isEmpty()) builder.parentsHidden(research.hiddenParents().toArray(String[]::new));
        if (!research.siblings().isEmpty()) builder.siblings(research.siblings().toArray(String[]::new));
        if (!pages.isEmpty()) builder.pages(pages.toArray(LegacyObject[]::new));
        if (!research.itemTriggers().isEmpty())
            builder.itemTriggers(research.itemTriggers().stream().map(runtime::stack).toArray(LegacyObject[]::new));
        if (!research.entityTriggers().isEmpty()) builder.entityTriggers(research.entityTriggers().toArray(String[]::new));
        if (!aspectTriggers.isEmpty()) builder.aspectTriggers(aspectTriggers.toArray(LegacyObject[]::new));
        builder.register();
        if (runtime.accessor(TcResearchAccessors.Categories.class).find(research.key()) == null)
            throw new IllegalArgumentException("The original registry refused " + research.key());
        if (research.warp() > 0) runtime.accessor(Warp.class).set(research.key(), research.warp());
        resetCaches(runtime);
    }

    /** Removes one research item; true when it was registered. Players keep it in their completed list. */
    public static boolean unregister(LegacyRuntime runtime, String key) {
        require(runtime);
        var item = runtime.accessor(TcResearchAccessors.Categories.class).find(key);
        if (item == null) return false;
        var list = runtime.accessor(CategoryRegistry.class).find(runtime.accessor(Research.class, item).category());
        if (list == null) return false;
        var category = runtime.accessor(CategoryList.class, list);
        runtime.values().removeKey(category.research(), key);
        int minColumn = 0, minRow = 0, maxColumn = 0, maxRow = 0;
        for (Object value : runtime.values().map(category.research()).values()) {
            var other = runtime.accessor(Research.class, (LegacyObject) value);
            minColumn = Math.min(minColumn, other.column());
            minRow = Math.min(minRow, other.row());
            maxColumn = Math.max(maxColumn, other.column());
            maxRow = Math.max(maxRow, other.row());
        }
        category.minColumn(minColumn);
        category.minRow(minRow);
        category.maxColumn(maxColumn);
        category.maxRow(maxRow);
        runtime.accessor(Warp.class).set(key, 0);
        resetCaches(runtime);
        return true;
    }

    /** Removes a category together with any research still in it; true when it was registered. */
    public static boolean unregisterCategory(LegacyRuntime runtime, String key) {
        require(runtime);
        var list = runtime.accessor(CategoryRegistry.class).find(key);
        if (list == null) return false;
        var keys = new ArrayList<String>();
        for (Object research : runtime.values().map(runtime.accessor(CategoryList.class, list).research()).keySet()) keys.add((String) research);
        keys.forEach(research -> unregister(runtime, research));
        runtime.values().removeKey(runtime.accessor(TcResearchAccessors.Categories.class).categories(), key);
        return true;
    }

    /** Lets the original rebuild its hidden-research and research-clue lists after the registry changed. */
    public static void resetCaches(LegacyRuntime runtime) {
        try {
            var caches = runtime.accessor(Caches.class);
            caches.hidden(null);
            caches.valid(null);
        } catch (RuntimeException unavailable) {
            // Older builds keep the stale lists until restart; registration itself is unaffected.
        }
    }

    // ---- Helpers --------------------------------------------------------------------------------------------

    private static boolean available(LegacyRuntime runtime) {
        return runtime.isOpen() && runtime.loadedMods().contains(TcModernApi.LEGACY_MOD_ID);
    }

    private static void require(LegacyRuntime runtime) {
        if (!available(runtime)) throw new IllegalStateException("Thaumcraft runtime is not available");
    }

    private static LegacyObject resource(LegacyRuntime runtime, String location) {
        int colon = location.indexOf(':');
        if (colon <= 0 || colon == location.length() - 1) throw new IllegalArgumentException("Expected namespace:path, got " + location);
        return runtime.accessor(TcAspectAccessors.ResourceFactory.class).create(location.substring(0, colon), location.substring(colon + 1));
    }

    private static LegacyObject aspect(LegacyRuntime runtime, String tag, String owner) {
        var aspect = runtime.accessor(TcAspectAccessors.AspectLookup.class).find(tag);
        if (aspect == null) throw new IllegalArgumentException(owner + ": unknown aspect " + tag);
        return aspect;
    }

    private static LegacyObject aspectList(LegacyRuntime runtime, Map<String, Integer> aspects, String owner) {
        var list = runtime.accessor(TcRecipeAccessors.AspectListFactory.class).create();
        var add = runtime.accessor(TcRecipeAccessors.Aspects.class, list);
        aspects.forEach((tag, amount) -> {
            if (amount == null || amount < 1) throw new IllegalArgumentException(owner + ": invalid amount for " + tag);
            add.add(aspect(runtime, tag, owner), amount);
        });
        return list;
    }

    private static LegacyObject page(LegacyRuntime runtime, TcResearchDefinition.Page page, String owner) {
        if (page instanceof TcResearchDefinition.Text text) return text.requires().isEmpty()
                ? runtime.accessor(TextPages.class).text(text.text()) : runtime.accessor(ConcealedPages.class).concealed(text.requires(), text.text());
        if (page instanceof TcResearchDefinition.Image image)
            return runtime.accessor(ImagePages.class).image(resource(runtime, image.image()), image.caption());
        if (page instanceof TcResearchDefinition.Aspects aspects)
            return runtime.accessor(AspectPages.class).aspects(aspectList(runtime, aspects.aspects(), owner));
        if (page instanceof TcResearchDefinition.Smelting smelting)
            return runtime.accessor(SmeltingPages.class).smelting(runtime.stack(smelting.input()));
        var recipe = (TcResearchDefinition.Recipe) page;
        var matches = recipes(runtime, recipe.kind(), recipe.output());
        if (matches.isEmpty())
            throw new IllegalArgumentException(owner + ": no " + recipe.kind().name().toLowerCase(Locale.ROOT) + " recipe makes " + recipe.output());
        String type = switch (recipe.kind()) {
            case ARCANE -> "thaumcraft.api.crafting.IArcaneRecipe";
            case CRUCIBLE -> "thaumcraft.api.crafting.CrucibleRecipe";
            case INFUSION -> "thaumcraft.api.crafting.InfusionRecipe";
            case CRAFTING -> "net.minecraft.item.crafting.IRecipe";
        };
        var array = runtime.values().newArray(type, matches.size());
        for (int i = 0; i < matches.size(); i++) runtime.values().set(array, i, matches.get(i));
        return switch (recipe.kind()) {
            case ARCANE -> runtime.accessor(ArcanePages.class).arcane(array);
            case CRUCIBLE -> runtime.accessor(CruciblePages.class).crucible(array);
            case INFUSION -> runtime.accessor(InfusionPages.class).infusion(array);
            case CRAFTING -> runtime.accessor(CraftingPages.class).crafting(array);
        };
    }

    /** Registered recipes of one kind whose output matches by item, metadata (or the wildcard) and NBT when given. */
    static List<LegacyObject> recipes(LegacyRuntime runtime, TcResearchDefinition.RecipeKind kind, LegacyStack wanted) {
        var values = runtime.values();
        var result = new ArrayList<LegacyObject>();
        List<Object> candidates = kind == TcResearchDefinition.RecipeKind.CRAFTING
                ? values.list(runtime.accessor(CraftingList.class, runtime.accessor(CraftingRegistry.class).instance()).recipes())
                : values.list(runtime.accessor(TcRecipeAccessors.Registry.class).recipes());
        for (Object value : candidates) {
            if (!(value instanceof LegacyObject recipe)) continue;
            LegacyObject output;
            try {
                output = switch (kind) {
                    case ARCANE -> values.isInstance(recipe, "thaumcraft.api.crafting.IArcaneRecipe")
                            ? runtime.accessor(TcRecipeAccessors.Arcane.class, recipe).output() : null;
                    case CRUCIBLE -> values.isInstance(recipe, "thaumcraft.api.crafting.CrucibleRecipe")
                            ? runtime.accessor(TcRecipeAccessors.Crucible.class, recipe).output() : null;
                    case INFUSION -> values.isInstance(recipe, "thaumcraft.api.crafting.InfusionRecipe")
                            ? runtime.accessor(TcRecipeAccessors.Infusion.class, recipe).output() : null;
                    case CRAFTING -> runtime.accessor(Recipe.class, recipe).output();
                };
            } catch (RuntimeException dynamic) {
                continue;
            }
            if (output == null || !values.isInstance(output, "net.minecraft.item.ItemStack")) continue;
            LegacyStack stack;
            try {
                stack = runtime.snapshot(output);
            } catch (RuntimeException unexportable) {
                // An output MFCL cannot describe (a broken addon stack) cannot match a page either.
                continue;
            }
            if (stack.key().equals(wanted.key()) && (wanted.metadata() == WILDCARD || stack.metadata() == wanted.metadata())
                    && (wanted.snbt() == null || wanted.snbt().isEmpty() || wanted.snbt().equals(stack.snbt())))
                result.add(recipe);
        }
        return result;
    }

    /** Reads detached copies of categories, research and pages. */
    private static final class Reader {
        private final LegacyRuntime runtime;
        private final PageTypes types;

        Reader(LegacyRuntime runtime) {
            this.runtime = runtime;
            this.types = runtime.accessor(PageTypes.class);
        }

        TcResearchCategory category(String key, LegacyObject value) {
            var list = runtime.accessor(CategoryList.class, value);
            var entries = new ArrayList<TcResearchEntry>();
            for (Object item : runtime.values().map(list.research()).values()) {
                try {
                    entries.add(entry((LegacyObject) item));
                } catch (RuntimeException unreadable) {
                    // Skip one malformed entry, keep the category.
                }
            }
            entries.sort(Comparator.comparingInt(TcResearchEntry::row).thenComparingInt(TcResearchEntry::column)
                    .thenComparing(TcResearchEntry::key));
            return new TcResearchCategory(key, location(list.icon()), location(list.background()), entries);
        }

        TcResearchEntry entry(LegacyObject item) {
            var research = runtime.accessor(Research.class, item);
            var flags = EnumSet.noneOf(TcResearchEntry.Flag.class);
            if (research.special()) flags.add(TcResearchEntry.Flag.SPECIAL);
            if (research.secondary()) flags.add(TcResearchEntry.Flag.SECONDARY);
            if (research.round()) flags.add(TcResearchEntry.Flag.ROUND);
            if (research.stub()) flags.add(TcResearchEntry.Flag.STUB);
            if (research.virtual()) flags.add(TcResearchEntry.Flag.VIRTUAL);
            if (research.concealed()) flags.add(TcResearchEntry.Flag.CONCEALED);
            if (research.hidden()) flags.add(TcResearchEntry.Flag.HIDDEN);
            if (research.lost()) flags.add(TcResearchEntry.Flag.LOST);
            if (research.autoUnlock()) flags.add(TcResearchEntry.Flag.AUTO_UNLOCK);
            var itemTriggers = new ArrayList<LegacyStack>();
            var triggerStacks = research.itemTriggers();
            if (triggerStacks != null) for (var stack : triggerStacks) if (stack != null) itemTriggers.add(runtime.snapshot(stack));
            var aspectTriggers = new ArrayList<String>();
            var triggerAspects = research.aspectTriggers();
            if (triggerAspects != null) for (var aspect : triggerAspects) if (aspect != null) aspectTriggers.add(tag(aspect));
            var pages = new ArrayList<TcResearchPage>();
            var originals = research.pages();
            if (originals != null) for (var page : originals) {
                if (page == null) continue;
                try {
                    pages.add(page(page));
                } catch (RuntimeException unreadable) {
                    pages.add(new TcResearchPage.Unsupported("unreadable"));
                }
            }
            int warp;
            try {
                warp = runtime.accessor(Warp.class).get(research.key());
            } catch (RuntimeException unavailable) {
                warp = 0;
            }
            var icon = research.iconItem();
            return new TcResearchEntry(research.key(), research.category(), research.column(), research.row(), research.complexity(),
                    icon == null ? null : runtime.snapshot(icon), location(research.iconResource()), aspects(research.tags()),
                    strings(research.parents()), strings(research.parentsHidden()), strings(research.siblings()), flags,
                    itemTriggers, strings(research.entityTriggers()), aspectTriggers, warp, pages);
        }

        TcResearchPage page(LegacyObject value) {
            var page = runtime.accessor(Page.class, value);
            var type = page.type();
            var values = runtime.values();
            if (values.same(type, types.text())) return new TcResearchPage.Text(page.text(), "");
            if (values.same(type, types.concealed())) return new TcResearchPage.Text(page.text(), page.research());
            if (values.same(type, types.image())) return new TcResearchPage.Image(location(page.image()), page.text());
            if (values.same(type, types.aspects())) return new TcResearchPage.Aspects(aspects(page.aspects()));
            if (values.same(type, types.arcane()) || values.same(type, types.crucible()) || values.same(type, types.infusion())) {
                var recipes = new ArrayList<github.com.gengyoubo.tcmodern.api.recipe.TcRecipe>();
                for (var recipe : elements(page.recipe())) recipes.addAll(TcRecipes.read(runtime, recipe));
                return recipes.isEmpty() ? new TcResearchPage.Unsupported("recipe") : new TcResearchPage.Recipes(recipes);
            }
            if (values.same(type, types.crafting())) {
                var grids = new ArrayList<TcResearchPage.Grid>();
                for (var recipe : elements(page.recipe())) {
                    var grid = grid(recipe);
                    if (grid != null) grids.add(grid);
                }
                return grids.isEmpty() ? new TcResearchPage.Unsupported("crafting") : new TcResearchPage.Crafting(grids);
            }
            if (values.same(type, types.smelting())) {
                var input = page.recipe();
                var output = page.output();
                if (input == null || output == null) return new TcResearchPage.Unsupported("smelting");
                return new TcResearchPage.Smelting(runtime.snapshot(input), runtime.snapshot(output));
            }
            if (values.same(type, types.compound())) return new TcResearchPage.Unsupported("compound");
            if (values.same(type, types.enchantment())) return new TcResearchPage.Unsupported("infusion_enchantment");
            return new TcResearchPage.Unsupported("unknown");
        }

        /** A recipe field holds one recipe or an array of them. */
        List<LegacyObject> elements(LegacyObject value) {
            if (value == null) return List.of();
            String type = value.typeName();
            if (!type.startsWith("[") && !type.endsWith("[]")) return List.of(value);
            var result = new ArrayList<LegacyObject>();
            for (int i = 0; i < runtime.values().length(value); i++)
                if (runtime.values().get(value, i) instanceof LegacyObject element) result.add(element);
            return result;
        }

        TcResearchPage.Grid grid(LegacyObject recipe) {
            var output = runtime.accessor(Recipe.class, recipe).output();
            if (output == null) return null;
            var result = runtime.snapshot(output);
            return switch (recipe.typeName()) {
                case "net.minecraft.item.crafting.ShapedRecipes" -> {
                    var shaped = runtime.accessor(ShapedVanilla.class, recipe);
                    yield new TcResearchPage.Grid(shaped.width(), shaped.height(), slots(Arrays.asList(shaped.inputs())), result, false);
                }
                case "net.minecraftforge.oredict.ShapedOreRecipe" -> {
                    var shaped = runtime.accessor(ShapedOre.class, recipe);
                    yield new TcResearchPage.Grid(shaped.width(), shaped.height(), slots(Arrays.asList(shaped.inputs())), result, false);
                }
                case "net.minecraft.item.crafting.ShapelessRecipes" -> shapeless(runtime.values().list(runtime.accessor(ShapelessVanilla.class, recipe).inputs()), result);
                case "net.minecraftforge.oredict.ShapelessOreRecipe" -> shapeless(runtime.values().list(runtime.accessor(ShapelessOre.class, recipe).inputs()), result);
                default -> null;
            };
        }

        TcResearchPage.Grid shapeless(List<Object> inputs, LegacyStack result) {
            if (inputs.isEmpty() || inputs.size() > 9) return null;
            return new TcResearchPage.Grid(inputs.size(), 1, slots(inputs), result, true);
        }

        /** Each slot: empty, one stack, or the stacks of an ore name. */
        List<List<LegacyStack>> slots(List<?> inputs) {
            var slots = new ArrayList<List<LegacyStack>>();
            for (Object input : inputs) {
                if (!(input instanceof LegacyObject value)) { slots.add(List.of()); continue; }
                if (runtime.values().isInstance(value, "net.minecraft.item.ItemStack")) { slots.add(List.of(runtime.snapshot(value))); continue; }
                var choices = new ArrayList<LegacyStack>();
                if (runtime.values().isInstance(value, "java.util.List"))
                    for (Object choice : runtime.values().list(value))
                        if (choice instanceof LegacyObject stack && runtime.values().isInstance(stack, "net.minecraft.item.ItemStack"))
                            choices.add(runtime.snapshot(stack));
                slots.add(choices);
            }
            return slots;
        }

        Map<String, Integer> aspects(LegacyObject list) {
            var result = new LinkedHashMap<String, Integer>();
            if (list == null) return result;
            var aspects = runtime.accessor(TcAspectAccessors.Aspects.class, list);
            var values = aspects.values();
            if (values != null) for (var aspect : values) if (aspect != null) result.put(tag(aspect), aspects.amount(aspect));
            return result;
        }

        String tag(LegacyObject aspect) { return runtime.accessor(TcAspectAccessors.Aspect.class, aspect).tag(); }

        String location(LegacyObject resource) {
            if (resource == null) return "";
            var location = runtime.accessor(TcAspectAccessors.Resource.class, resource);
            return location.namespace() + ":" + location.path();
        }

        static List<String> strings(String[] values) {
            if (values == null) return List.of();
            return Arrays.stream(values).filter(Objects::nonNull).toList();
        }
    }
}
