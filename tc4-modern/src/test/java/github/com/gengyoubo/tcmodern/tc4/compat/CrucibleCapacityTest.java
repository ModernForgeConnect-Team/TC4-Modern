package github.com.gengyoubo.tcmodern.tc4.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CrucibleCapacityTest {
    @Test
    void totalsUpToTheCapacityNeverLookOverfilled() {
        assertEquals(0, CrucibleCapacity.reported(0));
        assertEquals(37, CrucibleCapacity.reported(37));
        assertEquals(100, CrucibleCapacity.reported(100));
        // The ancient debris recipe needs 220; the original 100-cap check must not spill it.
        assertEquals(100, CrucibleCapacity.reported(220));
        assertEquals(100, CrucibleCapacity.reported(CrucibleCapacity.CAPACITY));
    }

    @Test
    void onlyTotalsAboveTheCapacityTriggerTheOriginalOverflow() {
        assertEquals(101, CrucibleCapacity.reported(CrucibleCapacity.CAPACITY + 1));
        assertEquals(101, CrucibleCapacity.reported(Integer.MAX_VALUE));
    }
}
