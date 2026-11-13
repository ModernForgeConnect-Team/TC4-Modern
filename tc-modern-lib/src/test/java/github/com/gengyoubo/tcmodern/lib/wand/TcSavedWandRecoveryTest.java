package github.com.gengyoubo.tcmodern.lib.wand;

import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;
class TcSavedWandRecoveryTest {
    @Test void findsWandsInNestedListsWithoutTreatingPartialDataAsWands() {
        var wand = new CompoundTag(); wand.putString("rod", "custom"); wand.putString("cap", "iron");
        var partial = new CompoundTag(); partial.putString("rod", "incomplete");
        var list = new ListTag(); list.add(wand); list.add(partial);
        var root = new CompoundTag(); root.put("items", list);
        var found = new ArrayList<CompoundTag>(); TcSavedWandRecovery.scan(root, found::add);
        assertEquals(1, found.size()); assertEquals("custom", found.get(0).getString("rod"));
    }
}
