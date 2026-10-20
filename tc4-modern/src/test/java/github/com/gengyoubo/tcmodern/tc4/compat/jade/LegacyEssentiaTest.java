package github.com.gengyoubo.tcmodern.tc4.compat.jade;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class LegacyEssentiaTest {
    @Test void jarKeepsAmountAndLabel() {
        var jar = new CompoundTag();
        jar.putString("Aspect", "aqua");
        jar.putString("AspectFilter", "aqua");
        jar.putShort("Amount", (short) 37);
        var read = LegacyEssentia.parse(jar).orElseThrow();
        assertEquals(Map.of("aqua", 37), read.aspects());
        assertEquals("aqua", read.filter());
    }

    @Test void emptyJarIsStillAContainer() {
        var jar = new CompoundTag();
        jar.putString("Aspect", "");
        jar.putShort("Amount", (short) 0);
        assertTrue(LegacyEssentia.parse(jar).orElseThrow().aspects().isEmpty());
    }

    @Test void aspectListKeepsOrderAndSurvivesServerRoundTrip() {
        var list = new ListTag();
        for (var entry : List.of(Map.entry("ignis", 5), Map.entry("aer", 3), Map.entry("perditio", 12))) {
            var aspect = new CompoundTag();
            aspect.putString("key", entry.getKey());
            aspect.putInt("amount", entry.getValue());
            list.add(aspect);
        }
        var crucible = new CompoundTag();
        crucible.putShort("Heat", (short) 200);
        crucible.put("Aspects", list);
        var read = LegacyEssentia.parse(crucible).orElseThrow();
        assertEquals(List.of("ignis", "aer", "perditio"), List.copyOf(read.aspects().keySet()));
        assertEquals(20, read.total());
        assertEquals(read, LegacyEssentia.fromTag(read.toTag()).orElseThrow());
    }

    @Test void alembicAndTube() {
        var alembic = new CompoundTag();
        alembic.putString("aspect", "terra");
        alembic.putShort("amount", (short) 8);
        assertEquals(Map.of("terra", 8), LegacyEssentia.parse(alembic).orElseThrow().aspects());
        var tube = new CompoundTag();
        tube.putString("type", "ordo");
        tube.putString("stype", "");
        tube.putInt("amount", 1);
        assertEquals(Map.of("ordo", 1), LegacyEssentia.parse(tube).orElseThrow().aspects());
    }

    @Test void otherTilesAreIgnored() {
        var other = new CompoundTag();
        other.putString("type", "ordo");
        other.putInt("amount", 1);
        assertTrue(LegacyEssentia.parse(other).isEmpty());
    }
}
