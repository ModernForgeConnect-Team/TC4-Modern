package github.com.gengyoubo.tcmodern.tc4.entity.golem;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class GolemItemDataTest {
    @Test void migratesSelectionAndPortableWithoutLosingExistingData() {
        var root = new CompoundTag();
        var id = UUID.randomUUID();
        root.putUUID(GolemItemData.SELECTED, id);
        root.putString(GolemItemData.SELECTED_DIMENSION, "minecraft:overworld");
        var portable = new CompoundTag(); portable.putInt("Tc4Core", 6); portable.putString("Tc4CarriedAspect", "aer");
        root.put(GolemItemData.PORTABLE, portable);
        root.putByte("core", (byte) 6);
        var display = new CompoundTag(); display.putString("Name", "custom name"); root.put("display", display);
        var legacy = new CompoundTag(); legacy.putInt("metadata", 2); root.put("mfcl_legacy", legacy);

        GolemItemData.migrate(root);

        assertFalse(root.contains(GolemItemData.SELECTED));
        assertFalse(root.contains(GolemItemData.SELECTED_DIMENSION));
        assertFalse(root.contains(GolemItemData.PORTABLE));
        assertFalse(root.contains("core"));
        assertEquals("custom name", root.getCompound("display").getString("Name"));
        assertEquals(2, root.getCompound("mfcl_legacy").getInt("metadata"));
        var data = root.getCompound("display").getCompound("tc4modern_golem_data");
        assertEquals(id, data.getUUID(GolemItemData.SELECTED));
        assertEquals("minecraft:overworld", data.getString(GolemItemData.SELECTED_DIMENSION));
        assertEquals(portable, data.getCompound(GolemItemData.PORTABLE));
        assertEquals(6, data.getByte("legacy_core"));
        var once = root.copy();
        GolemItemData.migrate(root);
        assertEquals(once, root);
    }

    @Test void leavesUnrelatedCoreAlone() {
        var root = new CompoundTag(); root.putByte("core", (byte) 4);
        GolemItemData.migrate(root);
        assertEquals(4, root.getByte("core"));
        assertFalse(root.contains("display"));
    }
}
