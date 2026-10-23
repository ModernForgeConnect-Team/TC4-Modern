package github.com.gengyoubo.tcmodern.api.research;

import github.com.gengyoubo.mfcl.api.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TcResearchTest {
    private record Handle(String typeName, String key) implements LegacyObject { }
    private record Research(String[] parents, String[] hidden, String[] siblings) { }

    @Test
    void listsKeysByCategoryInRegistrationOrder() {
        assertEquals(List.of("ALCHEMY", "CRUCIBLE", "NITOR", "THAUMIUM", "WARDED"), TcResearch.keys(new Fixture()));
        var closed = new Fixture();
        closed.open = false;
        assertEquals(List.of(), TcResearch.keys(closed));
    }

    @Test
    void completesResearchWithItsRequisitesAndSkipsMissingOnes() {
        var runtime = new Fixture();
        // THAUMIUM <- NITOR <- ALCHEMY (hidden parent), sibling WARDED, and a parent no addon registered.
        assertEquals(4, TcResearch.complete(runtime, "host", "THAUMIUM"));
        assertEquals(Set.of("THAUMIUM", "NITOR", "ALCHEMY", "WARDED"), runtime.completed);
        assertEquals(0, TcResearch.complete(runtime, "host", "NITOR"));
        assertEquals(2, runtime.transactions);
    }

    @Test
    void completesAllResearchOnce() {
        var runtime = new Fixture();
        runtime.completed.add("ALCHEMY");
        assertEquals(4, TcResearch.completeAll(runtime, "host"));
        assertEquals(Set.copyOf(TcResearch.keys(runtime)), runtime.completed);
        assertEquals(1, runtime.transactions);
    }

    @Test
    void rejectsUnknownResearchBeforeTouchingThePlayer() {
        var runtime = new Fixture();
        assertThrows(IllegalArgumentException.class, () -> TcResearch.complete(runtime, "host", "NONEXISTENT"));
        assertEquals(0, runtime.transactions);
        runtime.open = false;
        assertThrows(IllegalStateException.class, () -> TcResearch.completeAll(runtime, "host"));
    }

    private static final class Fixture implements LegacyRuntime {
        boolean open = true;
        int transactions;
        final Set<String> completed = new HashSet<>();
        final Handle player = new Handle("net.minecraft.entity.player.EntityPlayerMP", null);
        final Map<String, Map<String, Research>> categories = new LinkedHashMap<>();

        Fixture() {
            var alchemy = new LinkedHashMap<String, Research>();
            alchemy.put("ALCHEMY", new Research(null, null, null));
            alchemy.put("CRUCIBLE", new Research(new String[]{"ALCHEMY"}, null, null));
            alchemy.put("NITOR", new Research(null, new String[]{"ALCHEMY"}, null));
            categories.put("ALCHEMY", alchemy);
            var artifice = new LinkedHashMap<String, Research>();
            artifice.put("THAUMIUM", new Research(new String[]{"NITOR", "MISSING"}, null, new String[]{"WARDED"}));
            artifice.put("WARDED", new Research(null, null, null));
            categories.put("ARTIFICE", artifice);
        }

        private Research find(String key) {
            for (var category : categories.values()) if (category.containsKey(key)) return category.get(key);
            return null;
        }

        @Override public <T> T withPlayer(Object hostPlayer, Function<LegacyObject, T> operation) {
            assertEquals("host", hostPlayer);
            transactions++;
            return operation.apply(player);
        }
        @Override public boolean isOpen() { return open; }
        @Override public Set<String> loadedMods() { return Set.of("Thaumcraft"); }
        @Override public String sourceVersion() { return "1.7.10"; }
        @Override public <T> T accessor(Class<T> contract) { return accessor(contract, null); }
        @Override public <T> T accessor(Class<T> contract, LegacyObject receiver) {
            return contract.cast(Proxy.newProxyInstance(contract.getClassLoader(), new Class<?>[]{contract}, (proxy, method, args) -> {
                var handle = (Handle) receiver;
                if (contract == TcResearchAccessors.Categories.class) {
                    if (method.getName().equals("categories")) return new Handle("java.util.LinkedHashMap", null);
                    return find((String) args[0]) == null ? null : new Handle("thaumcraft.api.research.ResearchItem", (String) args[0]);
                }
                if (contract == TcResearchAccessors.Item.class) {
                    var research = find(handle.key());
                    return switch (method.getName()) {
                        case "key" -> handle.key();
                        case "parents" -> research.parents();
                        case "parentsHidden" -> research.hidden();
                        default -> research.siblings();
                    };
                }
                if (contract == TcResearchAccessors.Category.class) return new Handle("java.util.HashMap", handle.key());
                if (contract == TcResearchAccessors.Thaumcraft.class) return new Handle("thaumcraft.common.CommonProxy", null);
                if (contract == TcResearchAccessors.Proxy.class) return new Handle("thaumcraft.common.lib.research.ResearchManager", null);
                if (contract == TcResearchAccessors.Player.class) { assertSame(player, receiver); return "Steve"; }
                if (contract == TcResearchAccessors.Progress.class) { assertEquals("Steve", args[0]); return completed.contains((String) args[1]); }
                if (contract == TcResearchAccessors.Manager.class) { assertSame(player, args[0]); completed.add((String) args[1]); return null; }
                throw new IllegalArgumentException("Unexpected " + method);
            }));
        }
        @Override public LegacyValues values() {
            return (LegacyValues) Proxy.newProxyInstance(LegacyValues.class.getClassLoader(), new Class<?>[]{LegacyValues.class}, (proxy, method, args) -> {
                if (!method.getName().equals("map")) throw new UnsupportedOperationException(method.getName());
                var handle = (Handle) args[0];
                var result = new LinkedHashMap<Object, Object>();
                if (handle.key() == null) categories.keySet().forEach(key -> result.put(key, new Handle("thaumcraft.api.research.ResearchCategoryList", key)));
                else categories.get(handle.key()).keySet().forEach(key -> result.put(key, new Handle("thaumcraft.api.research.ResearchItem", key)));
                return result;
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
