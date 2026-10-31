package github.com.gengyoubo.tcmodern.tc4.research;

import github.com.gengyoubo.tcmodern.api.aspect.TcAspect;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AspectOrderTest {
    private static TcAspect aspect(String tag, String... parts) { return new TcAspect(tag, 0xFFFFFF, "", List.of(parts)); }

    /** The original compositions along the chain from the primals to Meto (harvest), the deepest tier. */
    private static AspectOrder order() {
        return new AspectOrder(List.of(aspect("aer"), aspect("terra"), aspect("ignis"), aspect("aqua"), aspect("ordo"), aspect("perditio"),
                aspect("lux", "aer", "ignis"), aspect("victus", "aqua", "terra"), aspect("herba", "victus", "terra"),
                aspect("mortuus", "victus", "perditio"), aspect("bestia", "motus", "victus"), aspect("motus", "aer", "ordo"),
                aspect("spiritus", "victus", "mortuus"), aspect("cognitio", "ignis", "spiritus"),
                aspect("humanus", "bestia", "cognitio"), aspect("instrumentum", "humanus", "ordo"),
                aspect("messis", "herba", "humanus"), aspect("meto", "messis", "instrumentum")));
    }

    private static final Map<String, String> NAMES = Map.of("aer", "风", "ignis", "火", "lux", "光明", "humanus", "人类", "meto", "收获");

    private static String name(String tag) { return NAMES.getOrDefault(tag, tag); }

    @Test void tiersFollowCompositionDepthAsInTheReferenceTable() {
        var order = order();
        assertEquals(0, order.tier("aer"));
        assertEquals(1, order.tier("lux"));
        assertEquals(2, order.tier("mortuus"));
        assertEquals(3, order.tier("spiritus"));
        assertEquals(4, order.tier("cognitio"));
        assertEquals(5, order.tier("humanus"));
        assertEquals(6, order.tier("messis"));
        assertEquals(7, order.tier("meto"));
    }

    @Test void everyModeOrdersOnlyKnownAspectsWithRegistrationTieBreaks() {
        var order = order();
        var amounts = new LinkedHashMap<String, Integer>();
        amounts.put("meto", 3); amounts.put("lux", 9); amounts.put("aer", 3); amounts.put("humanus", 0); amounts.put("ignis", 12);
        assertEquals(List.of("aer", "ignis", "lux", "humanus", "meto"), order.apply(amounts, AspectOrder.Mode.ORIGINAL, "", AspectOrderTest::name));
        assertEquals(List.of("ignis", "lux", "aer", "meto", "humanus"), order.apply(amounts, AspectOrder.Mode.AMOUNT, "", AspectOrderTest::name));
        assertEquals(List.of("aer", "ignis", "lux", "humanus", "meto"), order.apply(amounts, AspectOrder.Mode.TIER, "", AspectOrderTest::name));
        assertEquals(List.of("aer", "humanus", "ignis", "lux", "meto"), order.apply(amounts, AspectOrder.Mode.ALPHABETICAL, "", AspectOrderTest::name));
    }

    @Test void searchMatchesTagOrLocalizedNameIgnoringCase() {
        var order = order();
        var amounts = Map.of("lux", 1, "humanus", 1, "meto", 1, "aer", 1);
        assertEquals(List.of("humanus"), order.apply(amounts, AspectOrder.Mode.ORIGINAL, "人", AspectOrderTest::name));
        assertEquals(List.of("meto"), order.apply(amounts, AspectOrder.Mode.ORIGINAL, " MET ", AspectOrderTest::name));
        assertEquals(List.of("lux"), order.apply(amounts, AspectOrder.Mode.ORIGINAL, "光", AspectOrderTest::name));
        assertTrue(order.apply(amounts, AspectOrder.Mode.ORIGINAL, "xyz", AspectOrderTest::name).isEmpty());
    }

    @Test void modesCycle() {
        assertEquals(AspectOrder.Mode.AMOUNT, AspectOrder.Mode.ORIGINAL.next());
        assertEquals(AspectOrder.Mode.ORIGINAL, AspectOrder.Mode.ALPHABETICAL.next());
    }
}
