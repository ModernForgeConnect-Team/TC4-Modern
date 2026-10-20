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
        Fixture(LegacyObject[] aspects) { registry = new Handle("java.util.LinkedHashMap", Map.of("values", aspects)); }
        @Override public boolean isOpen() { return open; }
        @Override public Set<String> loadedMods() { return Set.of("Thaumcraft"); }
        @Override public String sourceVersion() { return "1.7.10"; }
        @Override public <T> T accessor(Class<T> contract) { return accessor(contract, null); }
        @Override public <T> T accessor(Class<T> contract, LegacyObject receiver) {
            return contract.cast(Proxy.newProxyInstance(contract.getClassLoader(), new Class<?>[]{contract}, (proxy, method, args) -> {
                if (contract == TcAspectAccessors.Registry.class) return registry;
                if (contract == TcAspectAccessors.MapFactory.class || contract == TcAspectAccessors.MapView.class) return args == null ? receiver : args[0];
                if (contract == TcAspectAccessors.CollectionView.class) return ((Handle) receiver).members().get("values");
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
        @Override public LegacyStack snapshot(LegacyObject value) { throw new UnsupportedOperationException(); }
        @Override public LegacyObject stack(LegacyStack value) { throw new UnsupportedOperationException(); }
        @Override public Set<String> registryKeys(Registry registry) { throw new UnsupportedOperationException(); }
        @Override public Optional<LegacyObject> find(Registry registry, String key) { throw new UnsupportedOperationException(); }
        @Override public LegacyMappings mappings() { throw new UnsupportedOperationException(); }
        @Override public List<Diagnostic> diagnostics() { return List.of(); }
    }
}
