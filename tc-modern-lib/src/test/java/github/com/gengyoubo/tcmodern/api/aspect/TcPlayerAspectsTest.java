package github.com.gengyoubo.tcmodern.api.aspect;

import github.com.gengyoubo.mfcl.api.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TcPlayerAspectsTest {
    private record Handle(String typeName, String tag) implements LegacyObject { }

    @Test
    void addsToThePoolOfTheTransactionPlayerAndCapsAtShortRange() {
        var runtime = new Fixture();
        var totals = TcPlayerAspects.give(runtime, "host", new LinkedHashMap<>(Map.of("terra", 5)));
        assertEquals(Map.of("terra", 5), totals);
        assertEquals(Map.of("terra", 8), TcPlayerAspects.give(runtime, "host", Map.of("terra", 3)));
        assertEquals(Map.of("terra", TcPlayerAspects.MAX_POOL), TcPlayerAspects.give(runtime, "host", Map.of("terra", TcPlayerAspects.MAX_POOL)));
        assertEquals(TcPlayerAspects.MAX_POOL, runtime.pools.get("Steve:terra"));
        assertEquals(3, runtime.transactions);
    }

    @Test
    void rejectsUnknownAspectsAndBadAmountsBeforeTouchingThePlayer() {
        var runtime = new Fixture();
        var amounts = new LinkedHashMap<String, Integer>();
        amounts.put("terra", 2);
        amounts.put("nonexistent", 1);
        assertThrows(IllegalArgumentException.class, () -> TcPlayerAspects.give(runtime, "host", amounts));
        assertThrows(IllegalArgumentException.class, () -> TcPlayerAspects.give(runtime, "host", Map.of("terra", 0)));
        assertThrows(IllegalArgumentException.class, () -> TcPlayerAspects.give(runtime, "host", Map.of()));
        assertEquals(0, runtime.transactions);
        assertTrue(runtime.pools.isEmpty());
        runtime.open = false;
        assertThrows(IllegalStateException.class, () -> TcPlayerAspects.give(runtime, "host", Map.of("terra", 1)));
    }

    private static final class Fixture implements LegacyRuntime {
        boolean open = true;
        int transactions;
        final Map<String, Integer> pools = new HashMap<>();
        final Handle player = new Handle("net.minecraft.entity.player.EntityPlayerMP", null);

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
                if (contract == TcAspectAccessors.AspectLookup.class)
                    return List.of("terra", "ignis").contains(args[0]) ? new Handle("thaumcraft.api.aspects.Aspect", (String) args[0]) : null;
                if (contract == TcAspectAccessors.Thaumcraft.class) return new Handle("thaumcraft.common.CommonProxy", null);
                if (contract == TcAspectAccessors.Proxy.class) return new Handle("thaumcraft.common.lib.research.PlayerKnowledge", null);
                if (contract == TcAspectAccessors.Player.class) { assertSame(player, receiver); return "Steve"; }
                if (contract == TcAspectAccessors.Knowledge.class) {
                    String key = args[0] + ":" + ((Handle) args[1]).tag();
                    if (method.getName().equals("add")) { pools.merge(key, (int) (short) args[2], Integer::sum); return true; }
                    return (short) (int) pools.getOrDefault(key, 0);
                }
                throw new IllegalArgumentException("Unexpected " + method);
            }));
        }
        @Override public LegacyStack snapshot(LegacyObject value) { throw new UnsupportedOperationException(); }
        @Override public LegacyObject stack(LegacyStack value) { throw new UnsupportedOperationException(); }
        @Override public Set<String> registryKeys(Registry registry) { throw new UnsupportedOperationException(); }
        @Override public Optional<LegacyObject> find(Registry registry, String key) { throw new UnsupportedOperationException(); }
        @Override public LegacyMappings mappings() { throw new UnsupportedOperationException(); }
        @Override public List<Diagnostic> diagnostics() { return List.of(); }
    }
}
