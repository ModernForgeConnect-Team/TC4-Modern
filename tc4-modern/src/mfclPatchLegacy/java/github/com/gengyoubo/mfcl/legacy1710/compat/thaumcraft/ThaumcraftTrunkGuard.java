package github.com.gengyoubo.mfcl.legacy1710.compat.thaumcraft;

/** Called by the patched TC4 trunk inventory: its items are usable only while the trunk is alive and still owns them. */
public final class ThaumcraftTrunkGuard {
    private ThaumcraftTrunkGuard() {}
    public static boolean live(Object inventory) {
        try {
            Object trunk = inventory.getClass().getField("ent").get(inventory);
            if (trunk == null || trunk.getClass().getField("field_70128_L").getBoolean(trunk)) return false;
            return trunk.getClass().getField("inventory").get(trunk) == inventory;
        } catch (ReflectiveOperationException failure) {
            return false;
        }
    }
}
