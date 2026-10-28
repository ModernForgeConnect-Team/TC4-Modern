package net.minecraft.inventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
/** Compile-only SRG contract; never packaged. */
public interface IInventory {
    int func_70302_i_();
    ItemStack func_70301_a(int slot);
    ItemStack func_70298_a(int slot, int count);
    ItemStack func_70304_b(int slot);
    void func_70299_a(int slot, ItemStack stack);
    String func_145825_b();
    boolean func_145818_k_();
    int func_70297_j_();
    void func_70296_d();
    boolean func_70300_a(EntityPlayer player);
    void func_70295_k_();
    void func_70305_f();
    boolean func_94041_b(int slot, ItemStack stack);
}
