package fixture;

import baubles.api.BaubleType;
import baubles.api.IBauble;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Test-only addon outside the Thaumcraft namespace; not part of any production JAR. */
@Mod(modid = "tc4modern_bauble_fixture", name = "TC4 Modern bauble test fixture", version = "1.0",
        acceptedMinecraftVersions = "[1.7.10]", dependencies = "required-after:Baubles")
public final class BaubleAddonFixture {
    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        GameRegistry.registerItem(new Variant(), "variant");
    }
    public static final class Variant extends Item implements IBauble {
        public BaubleType getBaubleType(ItemStack stack) {
            if (stack.func_77978_p() != null && stack.func_77978_p().func_74767_n("amulet")) return BaubleType.AMULET;
            return stack.func_77960_j() == 1 ? BaubleType.BELT : BaubleType.RING;
        }
        public void onWornTick(ItemStack stack, EntityLivingBase wearer) { }
        public void onEquipped(ItemStack stack, EntityLivingBase wearer) { }
        public void onUnequipped(ItemStack stack, EntityLivingBase wearer) { }
        public boolean canEquip(ItemStack stack, EntityLivingBase wearer) { return true; }
        public boolean canUnequip(ItemStack stack, EntityLivingBase wearer) { return true; }
    }
}
