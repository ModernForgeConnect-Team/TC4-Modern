package github.com.gengyoubo.tcmodern.api.world;

import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import java.lang.reflect.Proxy;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OriginalOreTest {
    @Test
    void metadataMatchesOriginalBlock() {
        assertEquals(0, OriginalOre.CINNABAR.metadata());
        assertEquals(OriginalOre.ORDER_INFUSED, OriginalOre.byMetadata(5));
        assertEquals(7, OriginalOre.AMBER.metadata());
        assertThrows(IllegalArgumentException.class, () -> OriginalOre.byMetadata(8));
    }

    @Test
    void resolvesEveryVariantThroughMfcl() {
        var states = OriginalOre.hostStates(runtime(true), String.class);
        assertEquals(8, states.size());
        assertEquals("Thaumcraft:blockCustomOre@6", states.get(OriginalOre.ENTROPY_INFUSED));
    }

    @Test
    void failsWhenAVariantIsMissing() {
        assertThrows(IllegalStateException.class, () -> OriginalOre.hostStates(runtime(false), String.class));
    }

    private static LegacyRuntime runtime(boolean amber) {
        return (LegacyRuntime) Proxy.newProxyInstance(LegacyRuntime.class.getClassLoader(), new Class<?>[] { LegacyRuntime.class },
                (proxy, method, args) -> {
                    if (!method.getName().equals("hostBlock")) throw new UnsupportedOperationException(method.getName());
                    int metadata = (Integer) args[1];
                    return amber || metadata != 7 ? Optional.of(args[0] + "@" + metadata) : Optional.empty();
                });
    }
}
