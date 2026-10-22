package github.com.gengyoubo.tcmodern.api.recipe;

import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspectAccessors;
import github.com.gengyoubo.tcmodern.api.recipe.TcRecipeDefinition.Ingredient;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TcRecipeRegistrarTest {
    private static final LegacyStack GLASS = new LegacyStack("minecraft:glass", 0, 1, "");
    private static final LegacyStack RESULT = new LegacyStack("Thaumcraft:ItemResource", 2, 4, "");

    @Test
    void shapedRecipePassesPatternCharacterKeysOreNamesAndStacksToTheOriginal() {
        var runtime = new Fixture();
        var key = new LinkedHashMap<Character, Ingredient>();
        key.put('G', new Ingredient.Ore("blockGlass"));
        key.put('I', new Ingredient.Item(GLASS));
        TcRecipeRegistrar.register(runtime, new TcRecipeDefinition.ArcaneShaped("KNOWN", List.of("G G", " I "), key,
                Map.of("aer", 5), RESULT, false));
        var call = runtime.calls.get(0);
        assertEquals("arcane", call.name());
        assertEquals("KNOWN", call.args()[0]);
        assertEquals(RESULT, ((Handle) call.args()[1]).members().get("stack"));
        assertEquals(Map.of("aer", 5), ((Handle) call.args()[2]).members());
        var recipe = Arrays.asList((Object[]) call.args()[3]);
        assertEquals(List.of("G G", " I "), recipe.subList(0, 2));
        int g = recipe.indexOf('G'), i = recipe.indexOf('I');
        assertEquals("blockGlass", recipe.get(g + 1));
        assertEquals(GLASS, ((Handle) recipe.get(i + 1)).members().get("stack"));
        assertEquals("mirrored", runtime.calls.get(1).name());
        assertEquals(false, runtime.calls.get(1).args()[0]);
    }

    @Test
    void shapelessCrucibleAndInfusionUseTheirOwnEntryPoints() {
        var runtime = new Fixture();
        TcRecipeRegistrar.register(runtime, new TcRecipeDefinition.ArcaneShapeless("KNOWN",
                List.of(new Ingredient.Item(GLASS), new Ingredient.Ore("blockGlass")), Map.of(), RESULT));
        TcRecipeRegistrar.register(runtime, new TcRecipeDefinition.Crucible("KNOWN", new Ingredient.Ore("blockGlass"), Map.of("ordo", 2), RESULT));
        TcRecipeRegistrar.register(runtime, new TcRecipeDefinition.Infusion("KNOWN", GLASS, List.of(GLASS, GLASS), Map.of("aer", 8), RESULT, 3));
        assertEquals(List.of("shapeless", "crucible", "infusion"), runtime.calls.stream().map(Call::name).toList());
        assertEquals("blockGlass", ((Object[]) runtime.calls.get(0).args()[3])[1]);
        assertEquals("blockGlass", runtime.calls.get(1).args()[2]);
        var infusion = runtime.calls.get(2).args();
        assertEquals(3, infusion[2]);
        assertEquals(2, ((LegacyObject[]) infusion[5]).length);
        assertEquals(GLASS, ((Handle) infusion[4]).members().get("stack"));
    }

    @Test
    void anythingUnknownIsRejectedBeforeTheOriginalListChanges() {
        var runtime = new Fixture();
        var shapeless = (java.util.function.Function<String, TcRecipeDefinition>) research ->
                new TcRecipeDefinition.ArcaneShapeless(research, List.of(new Ingredient.Item(GLASS)), Map.of("aer", 1), RESULT);
        assertThrows(IllegalArgumentException.class, () -> TcRecipeRegistrar.register(runtime, shapeless.apply("MISSING")));
        assertThrows(IllegalArgumentException.class, () -> TcRecipeRegistrar.register(runtime,
                new TcRecipeDefinition.ArcaneShapeless("KNOWN", List.of(new Ingredient.Item(GLASS)), Map.of("nope", 1), RESULT)));
        assertThrows(IllegalArgumentException.class, () -> TcRecipeRegistrar.register(runtime,
                new TcRecipeDefinition.ArcaneShapeless("KNOWN", List.of(new Ingredient.Ore("ingotNothing")), Map.of(), RESULT)));
        assertThrows(IllegalArgumentException.class, () -> TcRecipeRegistrar.register(runtime,
                new TcRecipeDefinition.ArcaneShapeless("KNOWN", List.of(new Ingredient.Item(new LegacyStack("Bad:item", 0, 1, ""))), Map.of(), RESULT)));
        assertTrue(runtime.calls.isEmpty());
    }

    @Test
    void runtimeWithoutObjectArgumentsOrThaumcraftIsRefused() {
        var runtime = new Fixture();
        runtime.features = Set.of();
        var recipe = new TcRecipeDefinition.Crucible("KNOWN", new Ingredient.Item(GLASS), Map.of("aer", 1), RESULT);
        assertThrows(IllegalStateException.class, () -> TcRecipeRegistrar.register(runtime, recipe));
        runtime.features = Set.of(LegacyFeatures.MLA_OBJECT_ARGUMENTS);
        runtime.open = false;
        assertThrows(IllegalStateException.class, () -> TcRecipeRegistrar.register(runtime, recipe));
        assertTrue(runtime.calls.isEmpty());
    }

    @Test
    void definitionsRejectMalformedShapes() {
        var key = Map.<Character, Ingredient>of('G', new Ingredient.Item(GLASS));
        assertThrows(IllegalArgumentException.class, () -> new TcRecipeDefinition.ArcaneShaped("KNOWN", List.of("GX"), key, Map.of(), RESULT, true));
        assertThrows(IllegalArgumentException.class, () -> new TcRecipeDefinition.ArcaneShaped("KNOWN", List.of("GG", "G"), key, Map.of(), RESULT, true));
        assertThrows(IllegalArgumentException.class, () -> new TcRecipeDefinition.ArcaneShaped("KNOWN", List.of("  "), key, Map.of(), RESULT, true));
        assertThrows(IllegalArgumentException.class, () -> new TcRecipeDefinition.ArcaneShaped("KNOWN", List.of("GGGG"), key, Map.of(), RESULT, true));
        assertThrows(IllegalArgumentException.class, () -> new TcRecipeDefinition.ArcaneShapeless("KNOWN", Collections.nCopies(10, new Ingredient.Item(GLASS)), Map.of(), RESULT));
        assertThrows(IllegalArgumentException.class, () -> new TcRecipeDefinition.Crucible("KNOWN", new Ingredient.Item(GLASS), Map.of(), RESULT));
        assertThrows(IllegalArgumentException.class, () -> new TcRecipeDefinition.Infusion("KNOWN", GLASS, List.of(), Map.of(), RESULT, 0));
        assertThrows(IllegalArgumentException.class, () -> new TcRecipeDefinition.Infusion(" ", GLASS, List.of(GLASS), Map.of(), RESULT, 0));
        assertThrows(IllegalArgumentException.class, () -> new TcRecipeDefinition.Crucible("KNOWN", new Ingredient.Item(GLASS), Map.of("aer", 0), RESULT));
    }

    private record Handle(String typeName, Map<String, Object> members) implements LegacyObject { }
    private record Call(String name, Object[] args) { }

    private static final class Fixture implements LegacyRuntime {
        boolean open = true;
        Set<String> features = Set.of(LegacyFeatures.MLA_OBJECT_ARGUMENTS);
        final List<Call> calls = new ArrayList<>();
        @Override public boolean isOpen() { return open; }
        @Override public Set<String> loadedMods() { return Set.of("Thaumcraft"); }
        @Override public Set<String> features() { return features; }
        @Override public String sourceVersion() { return "1.7.10"; }
        @Override public <T> T accessor(Class<T> contract) { return accessor(contract, null); }
        @Override public <T> T accessor(Class<T> contract, LegacyObject receiver) {
            return contract.cast(Proxy.newProxyInstance(contract.getClassLoader(), new Class<?>[]{contract}, (proxy, method, args) -> {
                if (contract == TcRecipeAccessors.ResearchLookup.class) return args[0].equals("KNOWN") ? new Handle("ResearchItem", Map.of()) : null;
                if (contract == TcAspectAccessors.AspectLookup.class)
                    return Set.of("aer", "ordo").contains(args[0]) ? new Handle("thaumcraft.api.aspects.Aspect", Map.of("tag", args[0])) : null;
                if (contract == TcRecipeAccessors.AspectListFactory.class) return new Handle("thaumcraft.api.aspects.AspectList", new LinkedHashMap<>());
                if (contract == TcRecipeAccessors.Aspects.class) {
                    ((Handle) receiver).members().put((String) ((Handle) args[0]).members().get("tag"), args[1]);
                    return receiver;
                }
                if (contract == TcRecipeAccessors.Ores.class)
                    return new Handle("java.util.ArrayList", Map.of("size", args[0].equals("blockGlass") ? 2 : 0));
                calls.add(new Call(method.getName(), args));
                return new Handle("recipe", Map.of());
            }));
        }
        @Override public LegacyValues values() {
            return (LegacyValues) Proxy.newProxyInstance(LegacyValues.class.getClassLoader(), new Class<?>[]{LegacyValues.class}, (proxy, method, args) -> {
                if (!method.getName().equals("size")) throw new UnsupportedOperationException(method.getName());
                return ((Handle) args[0]).members().get("size");
            });
        }
        @Override public LegacyObject stack(LegacyStack value) {
            if (value.key().startsWith("Bad:")) throw new LegacyAccessException("MLA_STACK", "stack", null);
            return new Handle("net.minecraft.item.ItemStack", Map.of("stack", value));
        }
        @Override public LegacyStack snapshot(LegacyObject value) { throw new UnsupportedOperationException(); }
        @Override public Set<String> registryKeys(Registry registry) { throw new UnsupportedOperationException(); }
        @Override public Optional<LegacyObject> find(Registry registry, String key) { throw new UnsupportedOperationException(); }
        @Override public LegacyMappings mappings() { throw new UnsupportedOperationException(); }
        @Override public List<Diagnostic> diagnostics() { return List.of(); }
    }
}
