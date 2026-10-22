package github.com.gengyoubo.tcmodern.api.recipe;

import github.com.gengyoubo.mfcl.api.*;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TcRecipesTest {
    @Test
    void shapedRecipePreservesHolesOreAlternativesCountsAndOriginalNbt() {
        var runtime = new Fixture();
        var glass = stack("minecraft:glass", 0, 1, "");
        var ore = collection(glass, stack("minecraft:stained_glass", 32767, 1, ""));
        var output = stack("Thaumcraft:ItemEssence", 1, 8, "{Aspects:[0:{key:aer,amount:3}]}");
        runtime.recipes = collection(shaped(2, 2, new LegacyObject[]{ore, null, glass, glass}, output));
        var snapshot = TcRecipes.snapshot(runtime);
        assertTrue(snapshot.issues().isEmpty());
        var recipe = snapshot.recipes().get(0);
        assertEquals(2, recipe.width());
        assertEquals(2, recipe.height());
        assertTrue(recipe.inputs().get(1).isEmpty());
        assertEquals(2, recipe.inputs().get(0).size());
        assertEquals(32767, recipe.inputs().get(0).get(1).metadata());
        assertEquals(8, recipe.output().count());
        assertEquals("{Aspects:[0:{key:aer,amount:3}]}", recipe.output().snbt());
    }

    @Test
    void readsShapelessCrucibleAndInfusionCostsAndCentralInput() {
        var input = stack("minecraft:iron_ingot", 0, 1, "");
        var output = stack("Thaumcraft:ItemResource", 2, 1, "");
        var costs = aspects("terra", 5);
        var runtime = new Fixture();
        runtime.recipes = collection(
                handle("thaumcraft.api.crafting.ShapelessArcaneRecipe", Map.of(
                        "inputs", collection(input), "output", output, "research", "ARCANE", "aspects", costs)),
                handle("thaumcraft.api.crafting.CrucibleRecipe", Map.of(
                        "catalyst", collection(input), "output", output, "research", "ALCHEMY", "aspects", costs)),
                handle("thaumcraft.api.crafting.InfusionRecipe", Map.of(
                        "central", input, "components", new LegacyObject[]{output}, "output", output,
                        "research", "INFUSION", "aspects", costs, "instability", 4)));
        var snapshot = TcRecipes.snapshot(runtime);
        assertTrue(snapshot.issues().isEmpty());
        assertEquals(List.of(TcRecipe.Kind.ARCANE_SHAPELESS, TcRecipe.Kind.CRUCIBLE, TcRecipe.Kind.INFUSION),
                snapshot.recipes().stream().map(TcRecipe::kind).toList());
        var infusion = snapshot.recipes().get(2);
        assertEquals(2, infusion.inputs().size());
        assertEquals("minecraft:iron_ingot", infusion.inputs().get(0).get(0).key());
        assertEquals(4, infusion.instability());
        assertEquals("terra", infusion.aspects().get(0).tag());
        assertEquals(5, infusion.aspects().get(0).amount());
        assertEquals("thaumcraft:textures/aspects/terra.png", infusion.aspects().get(0).texture());
        assertEquals("INFUSION", infusion.research());
    }

    @Test
    void malformedAndDynamicRecipesAreReportedWithoutHidingGoodRecipes() {
        var input = stack("minecraft:iron_ingot", 0, 1, "");
        var output = stack("Thaumcraft:ItemResource", 2, 1, "");
        var runtime = new Fixture();
        runtime.recipes = collection(
                shaped(1, 1, new LegacyObject[]{input}, output),
                shaped(2, 2, new LegacyObject[]{input}, output),
                shaped(1, 1, new LegacyObject[]{collection()}, output),
                shaped(1, 1, new LegacyObject[]{null}, output),
                shaped(1, 1, new LegacyObject[]{input}, handle("[Ljava.lang.Object;", Map.of())),
                handle("thaumcraft.api.crafting.InfusionEnchantmentRecipe", Map.of()));
        var snapshot = TcRecipes.snapshot(runtime);
        assertEquals(1, snapshot.recipes().size());
        assertEquals(5, snapshot.issues().size());
    }

    @Test
    void stableIdsDoNotDependOnRegistryOrderAndExactDuplicatesCollapse() {
        var input = stack("minecraft:iron_ingot", 0, 1, "");
        var first = shaped(1, 1, new LegacyObject[]{input}, stack("Thaumcraft:ItemResource", 1, 1, ""));
        var second = shaped(1, 1, new LegacyObject[]{input}, stack("Thaumcraft:ItemResource", 2, 1, ""));
        var runtime = new Fixture();
        runtime.recipes = collection(first, second, first);
        var before = TcRecipes.snapshot(runtime).recipes();
        runtime.recipes = collection(second, first);
        var after = TcRecipes.snapshot(runtime).recipes();
        assertEquals(2, before.size());
        assertEquals(before.get(0).id(), after.get(1).id());
        assertNotEquals(before.get(0).id(), before.get(1).id());
    }

    @Test
    void snapshotsAreImmutableAndSurviveRuntimeShutdown() {
        var runtime = new Fixture();
        runtime.recipes = collection(shaped(1, 1, new LegacyObject[]{stack("minecraft:glass", 0, 1, "")},
                stack("Thaumcraft:ItemEssence", 0, 8, "")));
        var snapshot = TcRecipes.snapshot(runtime);
        runtime.open = false;
        assertEquals(8, snapshot.recipes().get(0).output().count());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.recipes().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.recipes().get(0).inputs().get(0).clear());
    }

    @Test
    void unavailableRuntimeIsNotReadAndRegistryFailuresAreVisible() {
        var runtime = new Fixture();
        runtime.open = false;
        assertTrue(TcRecipes.snapshot(runtime).recipes().isEmpty());
        assertEquals(0, runtime.binds);
        runtime.open = true;
        runtime.mods = Set.of("OtherMod");
        assertTrue(TcRecipes.snapshot(runtime).recipes().isEmpty());
        assertEquals(0, runtime.binds);
        runtime.mods = Set.of("Thaumcraft");
        runtime.registryFailure = true;
        var snapshot = TcRecipes.snapshot(runtime);
        assertTrue(snapshot.recipes().isEmpty());
        assertEquals("registry", snapshot.issues().get(0).source());
    }

    private record Handle(String typeName, Map<String, Object> members) implements LegacyObject { }
    private static Handle handle(String type, Map<String, Object> members) { return new Handle(type, members); }
    private static Handle collection(LegacyObject... values) { return handle("java.util.ArrayList", Map.of("elements", values)); }
    private static Handle stack(String key, int metadata, int count, String snbt) {
        return handle("net.minecraft.item.ItemStack", Map.of("snapshot", new LegacyStack(key, metadata, count, snbt)));
    }
    private static Handle shaped(int width, int height, LegacyObject[] inputs, LegacyObject output) {
        return handle("thaumcraft.api.crafting.ShapedArcaneRecipe", Map.of(
                "width", width, "height", height, "inputs", inputs, "output", output, "research", "GLASS", "aspects", aspects("aer", 2)));
    }
    private static Handle aspects(String tag, int amount) {
        return handle("thaumcraft.api.aspects.AspectList", Map.of("amount", amount,
                "values", new LegacyObject[]{handle("thaumcraft.api.aspects.Aspect", Map.of("tag", tag, "color", 0xFFFF00,
                        "image", handle("net.minecraft.util.ResourceLocation", Map.of("namespace", "thaumcraft", "path", "textures/aspects/" + tag + ".png"))))}));
    }

    private static final class Fixture implements LegacyRuntime {
        boolean open = true, registryFailure;
        int binds;
        Set<String> mods = Set.of("Thaumcraft");
        Handle recipes = collection();
        @Override public boolean isOpen() { return open; }
        @Override public Set<String> loadedMods() { return mods; }
        @Override public String sourceVersion() { return "1.7.10"; }
        @Override public <T> T accessor(Class<T> contract) { return accessor(contract, null); }
        @Override public <T> T accessor(Class<T> contract, LegacyObject receiver) {
            binds++;
            if (registryFailure) throw new IllegalStateException("fixture registry failure");
            return contract.cast(Proxy.newProxyInstance(contract.getClassLoader(), new Class<?>[]{contract}, (proxy, method, args) -> {
                if (contract == TcRecipeAccessors.Registry.class) return recipes;
                var members = ((Handle) receiver).members();
                if (!members.containsKey(method.getName())) throw new IllegalArgumentException("Missing fixture member " + method);
                return members.get(method.getName());
            }));
        }
        @Override public LegacyValues values() {
            return (LegacyValues) Proxy.newProxyInstance(LegacyValues.class.getClassLoader(), new Class<?>[]{LegacyValues.class}, (proxy, method, args) -> {
                if (!method.getName().equals("list")) throw new UnsupportedOperationException(method.getName());
                var elements = ((Handle) args[0]).members().get("elements");
                if (elements == null) throw new IllegalArgumentException("Not an old Iterable or array");
                return Collections.unmodifiableList(Arrays.asList((Object[]) elements));
            });
        }
        @Override public LegacyStack snapshot(LegacyObject value) { return (LegacyStack) ((Handle) value).members().get("snapshot"); }
        @Override public LegacyObject stack(LegacyStack value) { throw new UnsupportedOperationException(); }
        @Override public Set<String> registryKeys(Registry registry) { throw new UnsupportedOperationException(); }
        @Override public Optional<LegacyObject> find(Registry registry, String key) { throw new UnsupportedOperationException(); }
        @Override public LegacyMappings mappings() { throw new UnsupportedOperationException(); }
        @Override public List<Diagnostic> diagnostics() { return List.of(); }
    }
}
