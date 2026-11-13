package github.com.gengyoubo.tcmodern.api.wand;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TcWandCapacitiesTest {
    @Test void prefixesAreOptInUniqueAndRemovable() throws Exception {
        assertTrue(TcWandCapacities.prefixes().contains(TcWandCapacities.DEFAULT_PREFIX));
        try (var registration = TcWandCapacities.registerPrefix("tc_t_capacity_")) {
            assertTrue(TcWandCapacities.prefixes().contains("tc_t_capacity_"));
            assertThrows(IllegalArgumentException.class, () -> TcWandCapacities.registerPrefix("tc_t_capacity_"));
        }
        assertFalse(TcWandCapacities.prefixes().contains("tc_t_capacity_"));
        assertThrows(IllegalArgumentException.class, () -> TcWandCapacities.registerPrefix(""));
    }
}
