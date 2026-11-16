package github.com.gengyoubo.tcmodern.tc4.entity;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.level.Level;

public final class TaintChicken extends TaintCow {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.monster.EntityTaintChicken";
    public float flap, previousFlap, flapAmount, previousFlapAmount;
    private float flapSpeed = 1.0F;
    public TaintChicken(EntityType<? extends TaintChicken> type, Level level) { super(type, level); xpReward = 2; }
    @Override protected String legacyClass() { return LEGACY_CLASS; }
    public static AttributeSupplier.Builder chickenAttributes() {
        return TaintCow.attributes().add(Attributes.MAX_HEALTH, 8).add(Attributes.ATTACK_DAMAGE, 3).add(Attributes.MOVEMENT_SPEED, 0.4);
    }
    @Override protected void registerGoals() { super.registerGoals(); goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.3F)); }
    @Override public void aiStep() {
        super.aiStep();
        previousFlap = flap; previousFlapAmount = flapAmount;
        flapAmount = Mth.clamp(flapAmount + (onGround() ? -0.3F : 1.2F), 0.0F, 1.0F);
        if (!onGround() && flapSpeed < 1.0F) flapSpeed = 1.0F;
        flapSpeed *= 0.9F;
        if (!onGround() && getDeltaMovement().y < 0) setDeltaMovement(getDeltaMovement().multiply(1, 0.9, 1));
        flap += flapSpeed * 2.0F;
    }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.CHICKEN_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.CHICKEN_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.CHICKEN_DEATH; }
}
