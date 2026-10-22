package github.com.gengyoubo.tcmodern.api.aspect;

import github.com.gengyoubo.mfcl.api.*;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TcAspectsTest {
    @Test
    void readsPrimalAndCompoundAspectsInRegistrationOrder() {
        var aer = aspect("aer", 0xFFFF7E, null);
        var ignis = aspect("ignis", 0xFF5A01, null);
        var lux = aspect("lux", 0xFFF663, new LegacyObject[]{aer, ignis});
        var runtime = new Fixture(new LegacyObject[]{aer, ignis, lux});
        var aspects = TcAspects.snapshot(runtime);
        assertEquals(List.of("aer", "ignis", "lux"), aspects.stream().map(TcAspect::tag).toList());
        assertTrue(aspects.get(0).primal());
        assertEquals(List.of("aer", "ignis"), aspects.get(2).components());
        assertEquals(0xFFF663, aspects.get(2).color());
        assertEquals("thaumcraft:textures/aspects/lux.png", aspects.get(2).texture());
    }

    @Test
    void unreadableAspectIsSkippedAndClosedRuntimeIsNotRead() {
        var broken = new Handle("thaumcraft.api.aspects.Aspect", Map.of());
        var runtime = new Fixture(new LegacyObject[]{broken, aspect("terra", 0x56C000, null)});
        assertEquals(List.of("terra"), TcAspects.snapshot(runtime).stream().map(TcAspect::tag).toList());
        runtime.open = false;
        assertTrue(TcAspects.snapshot(runtime).isEmpty());
    }

    @Test
    void itemAspectsIncludeBonusTagsAndNoAspectsMeansNothingToSmelt() {
        var ignis = aspect("ignis", 0xFF5A01, null);
        var terra = aspect("terra", 0x56C000, null);
        var runtime = new Fixture(new LegacyObject[]{ignis, terra});
        var list = new Handle("thaumcraft.api.aspects.AspectList", Map.of("values", new LegacyObject[]{ignis, terra},
                "amounts", Map.of("ignis", 2, "terra", 0)));
        var coal = new Handle("net.minecraft.item.ItemStack", Map.of("tags", list));
        assertEquals(Map.of("ignis", 3), TcItemAspects.of(runtime, coal).orElseThrow().amounts());
        var dirt = new Handle("net.minecraft.item.ItemStack", Map.of());
        assertTrue(TcItemAspects.of(runtime, dirt).isEmpty());
        assertTrue(TcItemAspects.of(runtime, (LegacyObject) null).isEmpty());
    }

    @Test
    void registersCompoundAspectThroughTheOriginalConstructor() {
        var cognitio = aspect("cognitio", 0xFFC2B3, null);
        var permutatio = aspect("permutatio", 0x578357, null);
        var runtime = new Fixture(new LegacyObject[]{cognitio, permutatio});
        var modernitas = TcAspects.register(runtime, "modernitas", 0x3FA9F5, "cognitio", "permutatio", "tc4modern:textures/aspects/modernitas.png");
        assertEquals(new TcAspect("modernitas", 0x3FA9F5, "tc4modern:textures/aspects/modernitas.png", List.of("cognitio", "permutatio")), modernitas);
        assertEquals(List.of("cognitio", "permutatio", "modernitas"), TcAspects.snapshot(runtime).stream().map(TcAspect::tag).toList());
    }

    @Test
    void rejectsTakenTagsMissingComponentsAndReusedPairs() {
        var terra = aspect("terra", 0x56C000, null);
        var cognitio = aspect("cognitio", 0xFFC2B3, null);
        var runtime = new Fixture(new LegacyObject[]{terra, cognitio});
        TcAspects.register(runtime, "antiquitas", 0x8C7853, "terra", "cognitio", "tc4modern:textures/aspects/antiquitas.png");
        assertThrows(IllegalArgumentException.class, () -> TcAspects.register(runtime, "antiquitas", 0, "terra", "terra", "a:b"));
        assertThrows(IllegalArgumentException.class, () -> TcAspects.register(runtime, "echo", 0, "sensus", "motus", "a:b"));
        assertThrows(IllegalArgumentException.class, () -> TcAspects.register(runtime, "vetus", 0, "cognitio", "terra", "a:b"));
        assertThrows(IllegalArgumentException.class, () -> TcAspects.register(runtime, "Vetus", 0, "terra", "terra", "a:b"));
        assertThrows(IllegalArgumentException.class, () -> TcAspects.register(runtime, "vetus", 0, "terra", "terra", "no-namespace"));
        assertEquals(3, TcAspects.snapshot(runtime).size());
        runtime.open = false;
        assertThrows(IllegalStateException.class, () -> TcAspects.register(runtime, "vetus", 0, "terra", "terra", "a:b"));
    }

    private record Handle(String typeName, Map<String, Object> members) implements LegacyObject { }

    private static Handle aspect(String tag, int color, LegacyObject[] components) {
        var members = new HashMap<String, Object>(Map.of("tag", tag, "color", color,
                "image", new Handle("net.minecraft.util.ResourceLocation", Map.of("namespace", "thaumcraft", "path", "textures/aspects/" + tag + ".png"))));
        members.put("components", components);
        return new Handle("thaumcraft.api.aspects.Aspect", members);
    }

    private static final class Fixture implements LegacyRuntime {
        boolean open = true;
        final Handle registry;
        Fixture(LegacyObject[] aspects) { registry = new Handle("java.util.LinkedHashMap", new HashMap<>(Map.of("values", aspects))); }
        private LegacyObject[] registered() { return (LegacyObject[]) registry.members().get("values"); }
        @Override public boolean isOpen() { return open; }
        @Override public Set<String> loadedMods() { return Set.of("Thaumcraft"); }
        @Override public String sourceVersion() { return "1.7.10"; }
        @Override public <T> T accessor(Class<T> contract) { return accessor(contract, null); }
        @Override public <T> T accessor(Class<T> contract, LegacyObject receiver) {
            return contract.cast(Proxy.newProxyInstance(contract.getClassLoader(), new Class<?>[]{contract}, (proxy, method, args) -> {
                if (contract == TcAspectAccessors.Registry.class) return registry;
                if (contract == TcAspectAccessors.AspectLookup.class) return Arrays.stream(registered())
                        .filter(aspect -> ((Handle) aspect).members().get("tag").equals(args[0])).findFirst().orElse(null);
                if (contract == TcAspectAccessors.ResourceFactory.class)
                    return new Handle("net.minecraft.util.ResourceLocation", Map.of("namespace", args[0], "path", args[1]));
                if (contract == TcAspectAccessors.AspectFactory.class) {
                    var members = new HashMap<String, Object>(Map.of("tag", args[0], "color", args[1], "components", args[2], "image", args[3]));
                    var created = new Handle("thaumcraft.api.aspects.Aspect", members);
                    var values = Arrays.copyOf(registered(), registered().length + 1);
                    values[values.length - 1] = created;
                    registry.members().put("values", values);
                    return created;
                }
                // Bonus tags add one to every aspect the item already has.
                if (contract == TcAspectAccessors.Crafting.class) return method.getName().equals("tags")
                        ? ((Handle) args[0]).members().get("tags") : bonus((Handle) args[1]);
                if (contract == TcAspectAccessors.Aspects.class && method.getName().equals("amount"))
                    return ((Map<?, ?>) ((Handle) receiver).members().get("amounts")).get(((Handle) args[0]).members().get("tag"));
                var members = ((Handle) receiver).members();
                if (!members.containsKey(method.getName())) throw new IllegalArgumentException("Missing fixture member " + method);
                return members.get(method.getName());
            }));
        }
        private static Handle bonus(Handle tags) {
            if (tags == null) return null;
            var amounts = new HashMap<String, Integer>();
            ((Map<?, ?>) tags.members().get("amounts")).forEach((tag, amount) -> amounts.put((String) tag, (Integer) amount > 0 ? (Integer) amount + 1 : 0));
            return new Handle(tags.typeName(), Map.of("values", tags.members().get("values"), "amounts", amounts));
        }
        @Override public LegacyValues values() {
            return (LegacyValues) Proxy.newProxyInstance(LegacyValues.class.getClassLoader(), new Class<?>[]{LegacyValues.class}, (proxy, method, args) -> {
                if (!method.getName().equals("map")) throw new UnsupportedOperationException(method.getName());
                var map = new LinkedHashMap<Object, Object>();
                for (var aspect : (LegacyObject[]) ((Handle) args[0]).members().get("values")) map.put(((Handle) aspect).members().get("tag"), aspect);
                return Collections.unmodifiableMap(map);
            });
        }
        @Override public LegacyStack snapshot(LegacyObject value) { throw new UnsupportedOperationException(); }
        @Override public LegacyObject stack(LegacyStack value) { throw new UnsupportedOperationException(); }
        @Override public Set<String> registryKeys(Registry registry) { throw new UnsupportedOperationException(); }
        @Override public Optional<LegacyObject> find(Registry registry, String key) { throw new UnsupportedOperationException(); }
        @Override public LegacyMappings mappings() { throw new UnsupportedOperationException(); }
        @Override public List<Diagnostic> diagnostics() { return List.of(); }
    }
}
