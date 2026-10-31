package github.com.gengyoubo.tcmodern.tc4.compat;

import static org.junit.jupiter.api.Assertions.*;

import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.mfcl.api.spi.LegacyItemProvider;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class Tc4ItemSplitsTest {
    private final Tc4ItemSplits splits = new Tc4ItemSplits();

    @Test void metadataItemsSplitButDamageableSingleAndForeignItemsDoNot() {
        var resource = splits.split("Thaumcraft:ItemResource", declaration(List.of(0, 1, 2), 0, List.of()));
        assertEquals(List.of("meta_0", "meta_1", "meta_2"), resource.variants());
        assertEquals("meta_2", resource.select(stack(2, "", null)));
        assertNull(resource.select(stack(7, "", null)));
        assertNull(splits.split("Thaumcraft:ItemSwordThaumium", declaration(List.of(0), 1300, List.of())));
        assertNull(splits.split("Thaumcraft:ItemThaumometer", declaration(List.of(0), 0, List.of())));
        assertNull(splits.split("Baubles:Ring", declaration(List.of(0, 1), 0, List.of())));
    }

    @Test void wandsSplitByBuildIgnoringCostVisAndFocus() {
        var wand = splits.split(Tc4ItemSplits.WAND, declaration(List.of(0, 9, 54, 128), 0, List.of()));
        assertEquals(Tc4ItemSplits.CAPS.size() * Tc4ItemSplits.RODS.size() * 2, wand.variants().size());
        assertEquals("greatwood_gold", wand.select(stack(9, "", wandTag("gold", "greatwood", false))));
        var charged = wandTag("gold", "greatwood", false);
        charged.putInt("aer", 5000);
        assertEquals("greatwood_gold", wand.select(stack(0, "", charged)));
        assertEquals("silverwood_thaumium_sceptre", wand.select(stack(128, "", wandTag("thaumium", "silverwood", true))));
        assertEquals("primal_staff_void", wand.select(stack(0, "", wandTag("void", "primal_staff", false))));
        // Unknown builds and bare stacks stay on the original host item.
        assertNull(wand.select(stack(0, "", wandTag("mithril", "greatwood", false))));
        assertNull(wand.select(stack(0, "", null)));
    }

    @Test void creativeListedAddonBuildsGetTheirOwnItem() {
        var listed = List.of(new LegacyStack(Tc4ItemSplits.WAND, 0, 1, "{cap:\"mithril\",rod:\"greatwood\",sceptre:1b,}"),
                new LegacyStack(Tc4ItemSplits.WAND, 0, 1, "{cap:\"Bad Name\",rod:\"greatwood\"}"));
        var wand = splits.split(Tc4ItemSplits.WAND, declaration(List.of(0), 0, listed));
        assertTrue(wand.variants().contains("greatwood_mithril_sceptre"));
        assertEquals(Tc4ItemSplits.CAPS.size() * Tc4ItemSplits.RODS.size() * 2 + 1, wand.variants().size());
        assertEquals("greatwood_mithril_sceptre", wand.select(stack(0, "", wandTag("mithril", "greatwood", true))));
    }

    private static CompoundTag wandTag(String cap, String rod, boolean sceptre) {
        var tag = new CompoundTag();
        tag.putString("cap", cap);
        tag.putString("rod", rod);
        if (sceptre) tag.putByte("sceptre", (byte) 1);
        return tag;
    }

    private static LegacyItemProvider.Stack stack(int metadata, String snbt, CompoundTag tag) {
        return new LegacyItemProvider.Stack() {
            @Override public int metadata() { return metadata; }
            @Override public String snbt() { return snbt; }
            @Override public Object hostTag() { return tag == null ? null : tag.copy(); }
        };
    }

    private static LegacyItemProvider.ItemDeclaration declaration(List<Integer> metadata, int maxDamage, List<LegacyStack> catalog) {
        return new LegacyItemProvider.ItemDeclaration() {
            @Override public List<Integer> metadata() { return metadata; }
            @Override public int maxDamage() { return maxDamage; }
            @Override public List<LegacyStack> catalog() { return catalog; }
        };
    }
}
