package github.com.gengyoubo.tcmodern.api;

import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TcModernApiTest {
    @BeforeEach
    void resetProvider() { TestRuntimeProvider.reset(); }

    @Test
    void returnsEmptyBeforeStartupAndAfterShutdown() {
        assertTrue(TcModernApi.runtime().isEmpty());
        var runtime = runtime(true, Set.of("Thaumcraft"));
        TestRuntimeProvider.publish(runtime);
        assertSame(runtime, TcModernApi.runtime().orElseThrow());
        TestRuntimeProvider.runtime = null;
        assertTrue(TcModernApi.runtime().isEmpty());
    }

    @Test
    void rejectsClosedRuntimeAndMissingOriginalMod() {
        TestRuntimeProvider.runtime = runtime(false, Set.of("Thaumcraft"));
        assertTrue(TcModernApi.runtime().isEmpty());
        TestRuntimeProvider.runtime = runtime(true, Set.of("NotEnoughItems"));
        assertTrue(TcModernApi.runtime().isEmpty());
        TestRuntimeProvider.runtime = runtime(true, Set.of("thaumcraft"));
        assertTrue(TcModernApi.runtime().isEmpty());
    }

    @Test
    void forwardsPendingCallbackOnceAndUsesCurrentRuntime() {
        var received = new ArrayList<LegacyRuntime>();
        TcModernApi.whenReady(received::add);
        var first = runtime(true, Set.of("Thaumcraft"));
        TestRuntimeProvider.publish(first);
        assertEquals(1, received.size());
        assertSame(first, received.get(0));
        var next = runtime(true, Set.of("Thaumcraft"));
        TestRuntimeProvider.publish(next);
        assertEquals(1, received.size());
        assertSame(next, TcModernApi.runtime().orElseThrow());
    }

    @Test
    void alreadyReadyCallbackIsSynchronous() {
        TestRuntimeProvider.runtime = runtime(true, Set.of("Thaumcraft"));
        var received = new ArrayList<LegacyRuntime>();
        TcModernApi.whenReady(received::add);
        assertEquals(1, received.size());
        assertSame(TestRuntimeProvider.runtime, received.get(0));
    }

    @Test
    void cancellationAndOtherModsDoNotNotifyTc4Listeners() throws Exception {
        var received = new ArrayList<LegacyRuntime>();
        TcModernApi.whenReady(received::add).close();
        TestRuntimeProvider.publish(runtime(true, Set.of("Thaumcraft")));
        assertTrue(received.isEmpty());
        TestRuntimeProvider.reset();
        TcModernApi.whenReady(received::add);
        TestRuntimeProvider.publish(runtime(true, Set.of("NotEnoughItems")));
        assertTrue(received.isEmpty());
    }

    @Test
    void nullListenerFailsImmediately() {
        assertThrows(NullPointerException.class, () -> TcModernApi.whenReady(null));
    }

    private static LegacyRuntime runtime(boolean open, Set<String> mods) {
        return (LegacyRuntime) Proxy.newProxyInstance(LegacyRuntime.class.getClassLoader(),
                new Class<?>[]{LegacyRuntime.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "isOpen" -> open;
                    case "loadedMods" -> mods;
                    default -> throw new AssertionError("Unexpected runtime call: " + method.getName());
                });
    }
}
