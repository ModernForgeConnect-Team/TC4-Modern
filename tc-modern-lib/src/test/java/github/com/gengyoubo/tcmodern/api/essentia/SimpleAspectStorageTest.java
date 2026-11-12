package github.com.gengyoubo.tcmodern.api.essentia;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SimpleAspectStorageTest {
    @Test void respectsSharedCapacityLimitsAndSimulation() {
        var s = new SimpleAspectStorage(10, 6, 3);
        assertEquals(6, s.receive("aer", 9, true));
        assertTrue(s.contents().isEmpty());
        assertEquals(6, s.receive("aer", 9, false));
        assertEquals(4, s.receive("ignis", 6, false));
        var snapshot = s.contents();
        assertEquals(0, s.receive("aqua", 1, false));
        assertEquals(3, s.extract("aer", 6, true));
        assertEquals(6, s.stored("aer"));
        assertEquals(3, s.extract("aer", 6, false));
        assertEquals(6, snapshot.get("aer"));
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        assertEquals(3, s.extract("aer", 10, false));
        assertFalse(s.contents().containsKey("aer"));
        assertThrows(IllegalArgumentException.class, () -> s.receive("aer", -1, false));
    }
    @Test void restoringIsAtomicAndIgnoresTransferLimits() {
        var s = new SimpleAspectStorage(8, 0, 1);
        s.load(java.util.Map.of("aer", 7));
        assertEquals(7, s.stored("aer"));
        assertEquals(0, s.receive("aer", 1, false));
        assertThrows(IllegalArgumentException.class, () -> s.load(java.util.Map.of("ignis", 9)));
        assertEquals(java.util.Map.of("aer", 7), s.contents());
    }
}
