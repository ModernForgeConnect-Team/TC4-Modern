package github.com.gengyoubo.tcmodern.tc4.entity;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.api.LegacyMappings;
import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.mla.LegacyConstructor;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/** A corpse controlled by an eldritch crab; the crab leaves when the host dies. */
public final class InhabitedZombie extends BrainyZombie {
    public static final String LEGACY_CLASS = "thaumcraft.common.entities.monster.EntityInhabitedZombie";
    private boolean crabReleased;
    public InhabitedZombie(EntityType<? extends InhabitedZombie> type, Level level) { super(type, level); }
    @Override protected String legacyClass() { return LEGACY_CLASS; }
    public static AttributeSupplier.Builder inhabitedAttributes() {
        return BrainyZombie.attributes().add(Attributes.MAX_HEALTH, 30).add(Attributes.ATTACK_DAMAGE, 5);
    }
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            MobSpawnType reason, @Nullable SpawnGroupData group, @Nullable CompoundTag data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, group, data);
        equipCultistArmor();
        return result;
    }
    public void equipCultistArmor() {
        equip(EquipmentSlot.HEAD, "item_helmet_cultist_plate");
        float chance = level().getDifficulty() == Difficulty.HARD ? 0.9F : 0.6F;
        if (random.nextFloat() < chance) equip(EquipmentSlot.CHEST, "item_chest_cultist_plate");
        if (random.nextFloat() < chance) equip(EquipmentSlot.LEGS, "item_legs_cultist_plate");
    }
    private void equip(EquipmentSlot slot, String name) {
        var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("thaumcraft", name));
        if (item != null) { setItemSlot(slot, new ItemStack(item)); setDropChance(slot, 0.0F); }
    }
    @Override protected void tickDeath() {
        if (!level().isClientSide && deathTime == 19 && !crabReleased) {
            crabReleased = true;
            // The released crab wears the zombie's chestplate as its shell, exactly as the original setHelm(true).
            var modern = github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities.ELDRITCH_CRAB.get().create(level());
            if (modern != null) {
                modern.moveTo(getX(), getY() + getEyeHeight(), getZ(), getYRot(), getXRot());
                modern.setHelm(true);
                level().addFreshEntity(modern);
            } else TcModernApi.runtime().ifPresent(runtime -> {
                try {
                    var world = runtime.mappings().toLegacy(LegacyMappings.WORLD, level()).orElseThrow();
                    var crab = runtime.accessor(CrabFactory.class).create(world);
                    var old = runtime.accessor(Crab.class, crab);
                    old.position(getX(), getY() + getEyeHeight(), getZ(), getYRot(), getXRot());
                    old.helmet(true);
                    if (!runtime.accessor(OldWorld.class, world).spawn(crab))
                        LogUtils.getLogger().warn("TC4 inhabited zombie could not release its eldritch crab");
                } catch (RuntimeException failure) {
                    LogUtils.getLogger().error("TC4 inhabited zombie could not release its eldritch crab", failure);
                }
            });
        }
        super.tickDeath();
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putBoolean("Tc4CrabReleased", crabReleased); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); crabReleased = tag.getBoolean("Tc4CrabReleased"); }

    @LegacyType("thaumcraft.common.entities.monster.EntityEldritchCrab") public interface CrabFactory {
        @LegacyConstructor(descriptor="(Lnet/minecraft/world/World;)V") LegacyObject create(LegacyObject world);
    }
    @LegacyType("thaumcraft.common.entities.monster.EntityEldritchCrab") public interface Crab {
        @LegacyMethod(name="func_70080_a", descriptor="(DDDFF)V") void position(double x, double y, double z, float yaw, float pitch);
        @LegacyMethod(name="setHelm", descriptor="(Z)V") void helmet(boolean value);
    }
    @LegacyType("net.minecraft.world.World") public interface OldWorld {
        @LegacyMethod(name="func_72838_d", descriptor="(Lnet/minecraft/entity/Entity;)Z") boolean spawn(LegacyObject entity);
    }
}
