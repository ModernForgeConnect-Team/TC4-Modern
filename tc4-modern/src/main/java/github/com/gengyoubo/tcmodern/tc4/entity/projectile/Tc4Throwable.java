package github.com.gengyoubo.tcmodern.tc4.entity.projectile;

import github.com.gengyoubo.tcmodern.tc4.entity.LegacyHost;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Base of TC4's EntityThrowable projectiles: per-type gravity, an optional lifetime and the bound original. */
public abstract class Tc4Throwable extends ThrowableProjectile {
    protected final LegacyHost legacy;

    protected Tc4Throwable(EntityType<? extends Tc4Throwable> type, Level level, String legacyClass) {
        super(type, level);
        legacy = new LegacyHost(legacyClass);
    }

    /** EntityThrowable(World, EntityLivingBase): from the thrower's eyes along its look, plus TC4's pitch offset. */
    public void throwFrom(LivingEntity thrower, float velocity, float pitchOffset, float inaccuracy) {
        setOwner(thrower);
        setPos(thrower.getX(), thrower.getEyeY() - 0.1, thrower.getZ());
        shootFromRotation(thrower, thrower.getXRot(), thrower.getYRot(), pitchOffset, velocity, inaccuracy);
    }

    /** setThrowableHeading toward a direction with TC4's 0.0075 spread per inaccuracy unit. */
    public void heading(double x, double y, double z, float velocity, float inaccuracy) { shoot(x, y, z, velocity, inaccuracy); }

    @Override protected void defineSynchedData() { }
    @Override protected float getGravity() { return 0.03F; }
    /** Ticks before the projectile disappears in flight; 0 keeps it until it hits something. */
    protected int lifetime() { return 0; }

    @Override public void tick() {
        super.tick();
        if (!level().isClientSide && lifetime() > 0 && tickCount > lifetime()) discard();
        legacy.tick(this);
    }

    @Nullable protected LivingEntity livingOwner() { return getOwner() instanceof LivingEntity living ? living : null; }

    /** The orbs bounce back along the attacker's look when hit, as TC4's attackEntityFrom did. */
    protected boolean deflect(DamageSource source, @Nullable SoundEvent sound) {
        if (isInvulnerableTo(source)) return false;
        markHurt();
        Entity attacker = source.getEntity();
        if (attacker == null) return false;
        Vec3 look = attacker.getLookAngle();
        setDeltaMovement(look.scale(0.9));
        if (attacker instanceof LivingEntity living) setOwner(living);
        if (sound != null) playSound(sound, 1.0F, 1.0F + (random.nextFloat() - random.nextFloat()) * 0.2F);
        return true;
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); legacy.save(this, tag); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); legacy.load(tag); }
}
