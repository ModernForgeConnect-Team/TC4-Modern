package github.com.gengyoubo.tcmodern.tc4.content;

import github.com.gengyoubo.tcmodern.api.aspect.TcAspect;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PrimalDecompositionTest {
    private static TcAspect aspect(String tag, String... components) {
        return new TcAspect(tag, 0, "", List.of(components));
    }

    @Test void expandsRepeatedAndNestedComponentsWithoutLosingAmounts() {
        var registry = List.of(aspect("aer"), aspect("ignis"), aspect("lux", "aer", "ignis"),
                aspect("compound", "lux", "lux"));
        assertEquals(Map.of("aer", 5, "ignis", 4),
                PrimalDecomposition.expand(Map.of("compound", 2, "aer", 1), registry));
    }

    @Test void rejectsMissingOrCyclicComponents() {
        assertThrows(IllegalArgumentException.class, () -> PrimalDecomposition.expand(Map.of("unknown", 1), List.of()));
        var cyclic = List.of(aspect("a", "b", "b"), aspect("b", "a", "a"));
        assertThrows(IllegalArgumentException.class, () -> PrimalDecomposition.expand(Map.of("a", 1), cyclic));
    }
}
