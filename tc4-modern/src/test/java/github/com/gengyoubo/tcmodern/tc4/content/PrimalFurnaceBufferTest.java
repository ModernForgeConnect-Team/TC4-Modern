package github.com.gengyoubo.tcmodern.tc4.content;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PrimalFurnaceBufferTest {
    @Test void backpressureDoesNotPartiallyAcceptADecomposition() {
        var buffer=new PrimalFurnaceBuffer();
        assertTrue(buffer.add(Map.of("aer",255,"ignis",4)));
        assertFalse(buffer.add(Map.of("aer",2,"ignis",3)));
        assertEquals(Map.of("aer",255,"ignis",4),buffer.snapshot());
        assertTrue(buffer.takeOne("aer"));
        assertTrue(buffer.add(Map.of("aer",2,"ignis",3)));
        assertEquals(Map.of("aer",256,"ignis",7),buffer.snapshot());
    }
    @Test void refusesInvalidInputAndCannotTakeMissingValues() {
        var buffer=new PrimalFurnaceBuffer();
        assertFalse(buffer.add(Map.of()));
        assertFalse(buffer.add(Map.of("aer",257)));
        assertFalse(buffer.takeOne("aer"));
        assertEquals(Map.of(),buffer.snapshot());
    }
}
