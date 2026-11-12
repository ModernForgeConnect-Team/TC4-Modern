package github.com.gengyoubo.tcmodern.tc4.content;

import github.com.gengyoubo.tcmodern.tc4.Tc4Modern;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Items;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

/**
 * Ancient thaumium armour: thaumium armour upgraded at a smithing table. Besides its protection, each piece lowers the
 * vis its wearer's wands spend and the warp the original counts from worn gear; {@code AncientThaumiumGear} hands both
 * to the original Thaumcraft.
 */
public final class AncientThaumiumArmorItem extends ArmorItem {
    /** Netherite's durability, thaumium's enchantability; protection per piece is set by {@link #defense}. */
    public static final ArmorMaterial MATERIAL = new ArmorMaterial() {
        /** Vanilla's per-piece base durability times netherite's 37. */
        @Override public int getDurabilityForType(Type type) {
            int base = switch (type) {
                case HELMET -> 11;
                case CHESTPLATE -> 16;
                case LEGGINGS -> 15;
                case BOOTS -> 13;
            };
            return base * 37;
        }
        @Override public int getDefenseForType(Type type) { return defense(type); }
        @Override public int getEnchantmentValue() { return 25; }
        @Override public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_NETHERITE; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.of(Tc4Items.ANCIENT_THAUMIUM_INGOT.get()); }
        /** Worn textures: tc4modern:textures/models/armor/ancient_thaumium_layer_1.png and _layer_2.png. */
        @Override public String getName() { return Tc4Modern.MOD_ID + ":ancient_thaumium"; }
        @Override public float getToughness() { return 4F; }
        /** Shown as +1 Knockback Resistance per piece, like netherite. */
        @Override public float getKnockbackResistance() { return 0.1F; }
    };

    private final int visDiscount, warp;

    /**
     * @param visDiscount percent of vis saved, as the original's IVisDiscountGear
     * @param warp        warp added while worn (negative lowers it), as the original's IWarpingGear
     */
    public AncientThaumiumArmorItem(Type type, int visDiscount, int warp) {
        super(MATERIAL, type, new Properties().fireResistant());
        this.visDiscount = visDiscount;
        this.warp = warp;
    }

    public int visDiscount() { return visDiscount; }
    public int warp() { return warp; }

    static int defense(Type type) {
        return switch (type) {
            case HELMET, BOOTS -> 4;
            case CHESTPLATE -> 10;
            case LEGGINGS -> 8;
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.tc4modern.ancient_thaumium_armor.vis_discount", visDiscount).withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.translatable("item.tc4modern.ancient_thaumium_armor.warp", warp).withStyle(ChatFormatting.DARK_PURPLE));
    }
}
