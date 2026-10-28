package baubles.common.container;

import baubles.api.BaubleType;
import baubles.api.IBauble;
import java.lang.ref.WeakReference;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

/** Four-slot SRG inventory view. No GUI, packets, persistence or independent item ownership. */
public final class InventoryBaubles implements IInventory {
    // Names and types form the public ABI MFCL's equipment sources discover.
    public ItemStack[] stackList = new ItemStack[4];
    public WeakReference<EntityPlayer> player;
    public boolean blockEvents;

    public InventoryBaubles(EntityPlayer player) { this.player = new WeakReference<>(player); }
    public int func_70302_i_() { return stackList.length; }
    public ItemStack func_70301_a(int slot) { return valid(slot) ? stackList[slot] : null; }
    public String func_145825_b() { return "container.tc4modern.curios"; }
    public boolean func_145818_k_() { return false; }
    public int func_70297_j_() { return 1; }
    public boolean func_70300_a(EntityPlayer player) { return this.player.get() == player; }
    public void func_70296_d() { }
    public void func_70295_k_() { }
    public void func_70305_f() { }
    public ItemStack func_70304_b(int slot) {
        ItemStack result = func_70301_a(slot);
        if (valid(slot)) stackList[slot] = null;
        return result;
    }
    public ItemStack func_70298_a(int slot, int count) {
        ItemStack stack = func_70301_a(slot);
        if (stack == null || count <= 0) return null;
        if (count >= stack.field_77994_a) return func_70304_b(slot);
        return stack.func_77979_a(count);
    }
    public void func_70299_a(int slot, ItemStack stack) {
        if (valid(slot)) stackList[slot] = stack;
    }
    public boolean func_94041_b(int slot, ItemStack stack) {
        if (!valid(slot) || stack == null || !(stack.func_77973_b() instanceof IBauble)) return false;
        IBauble item = (IBauble) stack.func_77973_b();
        BaubleType required = slot == 0 ? BaubleType.AMULET : slot == 3 ? BaubleType.BELT : BaubleType.RING;
        EntityPlayer wearer = player.get();
        return wearer != null && item.getBaubleType(stack) == required && item.canEquip(stack, wearer);
    }
    private boolean valid(int slot) { return slot >= 0 && slot < stackList.length; }
}
