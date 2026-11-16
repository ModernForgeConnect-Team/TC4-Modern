package github.com.gengyoubo.tcmodern.tc4.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.level.Level;

/** TC4's giant gains strength and grows as it takes damage. */
public final class GiantBrainyZombie extends BrainyZombie {
    public static final String LEGACY_CLASS = "thaumcraft.common.entities.monster.EntityGiantBrainyZombie";
    private static final EntityDataAccessor<Float> ANGER = SynchedEntityData.defineId(GiantBrainyZombie.class, EntityDataSerializers.FLOAT);

    public GiantBrainyZombie(EntityType<? extends GiantBrainyZombie> type, Level level) {
        super(type, level);
        xpReward = 15;
    }
    @Override protected String legacyClass() { return LEGACY_CLASS; }
    public static AttributeSupplier.Builder giantAttributes() {
        return BrainyZombie.attributes().add(Attributes.MAX_HEALTH, 60).add(Attributes.ATTACK_DAMAGE, 7);
    }
    @Override protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.4F));
    }
    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ANGER, 1.0F);
    }
    public float anger() { return entityData.get(ANGER); }
    private void setAnger(float value) { entityData.set(ANGER, Mth.clamp(value, 1.0F, 2.0F)); }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide) setAnger(anger() + 0.1F);
        return super.hurt(source, amount);
    }
    @Override public void tick() {
        super.tick();
        if (!level().isClientSide) {
            if (anger() > 1.0F) setAnger(anger() - 0.002F);
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(7.0 + (anger() - 1.0) * 5.0);
        }
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (ANGER.equals(key)) refreshDimensions();
    }
    @Override public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(0.6F * (1.2F + anger()), 1.8F * (1.2F + anger()));
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Tc4Anger", anger());
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Tc4Anger")) setAnger(tag.getFloat("Tc4Anger"));
    }
}
