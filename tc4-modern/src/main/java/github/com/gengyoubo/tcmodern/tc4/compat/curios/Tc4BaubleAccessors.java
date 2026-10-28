package github.com.gengyoubo.tcmodern.tc4.compat.curios;

import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;

/** Exact public contracts of Baubles 1.0.1.10 (1.7.10). No old classes are linked. */
public final class Tc4BaubleAccessors {
    private Tc4BaubleAccessors() { }

    @LegacyType("baubles.api.IBauble")
    public interface Bauble {
        @LegacyMethod(name = "getBaubleType", descriptor = "(Lnet/minecraft/item/ItemStack;)Lbaubles/api/BaubleType;")
        LegacyObject baubleType(LegacyObject stack);

        @LegacyMethod(name = "canEquip", descriptor = "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;)Z")
        boolean canEquip(LegacyObject stack, LegacyObject player);
        @LegacyMethod(name = "canUnequip", descriptor = "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;)Z")
        boolean canUnequip(LegacyObject stack, LegacyObject player);
        @LegacyMethod(name = "onEquipped", descriptor = "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;)V")
        void equipped(LegacyObject stack, LegacyObject player);
        @LegacyMethod(name = "onUnequipped", descriptor = "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;)V")
        void unequipped(LegacyObject stack, LegacyObject player);
    }

    @LegacyType("net.minecraft.item.ItemStack")
    public interface Stack {
        @LegacyMethod(name = "func_77973_b", descriptor = "()Lnet/minecraft/item/Item;")
        LegacyObject item();
    }

    @LegacyType("baubles.api.BaubleType")
    public interface BaubleType {
        @LegacyMethod(name = "name", descriptor = "()Ljava/lang/String;")
        String name();
    }
}
