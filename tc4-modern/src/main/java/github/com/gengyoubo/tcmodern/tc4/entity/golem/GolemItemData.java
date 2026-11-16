package github.com.gengyoubo.tcmodern.tc4.entity.golem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** Modern golem state inside MFCL's preserved host display data. */
final class GolemItemData {
    static final String PORTABLE = "tc4modern_golem";
    static final String SELECTED = "tc4modern_selected_golem";
    static final String SELECTED_DIMENSION = "tc4modern_selected_dimension";
    private static final String STATE = "tc4modern_golem_data";

    private GolemItemData() { }

    static CompoundTag get(ItemStack stack) {
        var root = stack.getTag();
        if (root == null) return null;
        migrate(root);
        var display = root.getCompound("display");
        return display.contains(STATE, Tag.TAG_COMPOUND) ? display.getCompound(STATE) : null;
    }

    static CompoundTag getOrCreate(ItemStack stack) {
        migrate(stack.getOrCreateTag());
        return stack.getOrCreateTagElement("display").getCompound(STATE).isEmpty()
                ? createIfMissing(stack) : stack.getTagElement("display").getCompound(STATE);
    }

    private static CompoundTag createIfMissing(ItemStack stack) {
        var display = stack.getOrCreateTagElement("display");
        if (!display.contains(STATE, Tag.TAG_COMPOUND)) display.put(STATE, new CompoundTag());
        return display.getCompound(STATE);
    }

    static void migrate(CompoundTag root) {
        boolean portable = root.contains(PORTABLE, Tag.TAG_COMPOUND);
        if (!portable && !root.contains(SELECTED) && !root.contains(SELECTED_DIMENSION)) return;
        CompoundTag display = root.contains("display", Tag.TAG_COMPOUND) ? root.getCompound("display") : new CompoundTag();
        CompoundTag data = display.contains(STATE, Tag.TAG_COMPOUND) ? display.getCompound(STATE) : new CompoundTag();
        for (String key : new String[]{PORTABLE, SELECTED, SELECTED_DIMENSION}) {
            if (root.contains(key)) {
                if (!data.contains(key)) data.put(key, root.get(key).copy());
                root.remove(key);
            }
        }
        if (portable && root.contains("core")) {
            if (!data.contains("legacy_core")) data.put("legacy_core", root.get("core").copy());
            root.remove("core");
        }
        display.put(STATE, data);
        root.put("display", display);
    }
}
