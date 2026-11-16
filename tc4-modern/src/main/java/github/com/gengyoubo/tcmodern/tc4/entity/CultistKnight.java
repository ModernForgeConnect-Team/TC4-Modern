package github.com.gengyoubo.tcmodern.tc4.entity;

import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RestrictSunGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

/** A cult knight in crimson plate, usually with an iron sword and rarely a thaumium or void blade. */
public final class CultistKnight extends Cultist {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.monster.EntityCultistKnight";

    public CultistKnight(EntityType<? extends CultistKnight> type, Level level) { super(type, level, LEGACY_CLASS); }

    public static AttributeSupplier.Builder attributes() { return cultistAttributes().add(Attributes.MAX_HEALTH, 36); }

    public static void host(LegacyRuntime runtime) {
        Tc4Legacy.host(runtime, LEGACY_CLASS, (level, original) -> {
            var knight = Tc4Entities.CULTIST_KNIGHT.get().create(level);
            if (knight != null) knight.adopt(runtime, original);
            return knight;
        });
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, false));
        goalSelector.addGoal(5, new OpenDoorGoal(this, true));
        goalSelector.addGoal(6, new MoveTowardsRestrictionGoal(this, 0.8));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new Tc4Goals.CultistHurtByTarget(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override protected void equip(DifficultyInstance difficulty) {
        wear(EquipmentSlot.HEAD, "Thaumcraft:ItemHelmetCultistPlate");
        wear(EquipmentSlot.CHEST, "Thaumcraft:ItemChestplateCultistPlate");
        wear(EquipmentSlot.LEGS, "Thaumcraft:ItemLeggingsCultistPlate");
        wear(EquipmentSlot.FEET, "Thaumcraft:ItemBootsCultist");
        if (random.nextFloat() < (level().getDifficulty() == Difficulty.HARD ? 0.05F : 0.01F)) {
            if (random.nextInt(5) == 0) {
                wear(EquipmentSlot.MAINHAND, "Thaumcraft:ItemSwordVoid");
                wear(EquipmentSlot.HEAD, "Thaumcraft:ItemHelmetCultistRobe");
            } else {
                wear(EquipmentSlot.MAINHAND, "Thaumcraft:ItemSwordThaumium");
                if (random.nextBoolean()) setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            }
        } else {
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }
        float f = difficulty.getSpecialMultiplier();
        var weapon = getMainHandItem();
        if (!weapon.isEmpty() && random.nextFloat() < 0.25F * f)
            setItemSlot(EquipmentSlot.MAINHAND, EnchantmentHelper.enchantItem(random, weapon, (int) (5.0F + f * random.nextInt(18)), false));
    }
}
