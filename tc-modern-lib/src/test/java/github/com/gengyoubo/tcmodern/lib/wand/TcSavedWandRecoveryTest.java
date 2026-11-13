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
    @Test void restoresOnlyLegacyTc4TilesAndUnregisters() throws Exception {
        var wand = new CompoundTag(); wand.putString("rod", "tc_t_rod"); wand.putString("cap", "tc_t_cap");
        var root = new CompoundTag(); root.put("wand", wand);
        var bytes = new java.io.ByteArrayOutputStream(); NbtIo.writeCompressed(root, bytes);
        var tile = new CompoundTag(); tile.putString("mfcl_legacy_block", "Thaumcraft:blockTable");
        tile.putByteArray("mfcl_legacy_nbt", bytes.toByteArray());
        var seen = new ArrayList<String>();
        try (var handle = TcSavedWandRecovery.register((runtime, value) -> seen.add(value.getString("rod")))) {
            TcSavedWandRecovery.restoreTile(null, tile);
            tile.putString("mfcl_legacy_block", "Other:block"); TcSavedWandRecovery.restoreTile(null, tile);
        }
        assertEquals(java.util.List.of("tc_t_rod"), seen);
        tile.putString("mfcl_legacy_block", "Thaumcraft:blockTable"); TcSavedWandRecovery.restoreTile(null, tile);
        assertEquals(1, seen.size());
    }
}
