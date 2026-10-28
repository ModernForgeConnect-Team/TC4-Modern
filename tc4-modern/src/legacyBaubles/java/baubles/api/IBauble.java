package baubles.api;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

/** Original ABI only. Item behavior remains implemented by the installed original mod. */
public interface IBauble {
    BaubleType getBaubleType(ItemStack stack);
    void onWornTick(ItemStack stack, EntityLivingBase wearer);
    void onEquipped(ItemStack stack, EntityLivingBase wearer);
    void onUnequipped(ItemStack stack, EntityLivingBase wearer);
    boolean canEquip(ItemStack stack, EntityLivingBase wearer);
    boolean canUnequip(ItemStack stack, EntityLivingBase wearer);
}
