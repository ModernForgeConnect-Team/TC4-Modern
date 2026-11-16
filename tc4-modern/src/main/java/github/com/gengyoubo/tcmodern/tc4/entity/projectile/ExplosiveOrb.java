package github.com.gengyoubo.tcmodern.tc4.entity.projectile;

import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.mla.LegacyField;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.tc4.entity.Tc4Legacy;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** The fire focus fireball upgrade: an explosion that hurts creatures but leaves blocks intact. */
public final class ExplosiveOrb extends Tc4Throwable {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.projectile.EntityExplosiveOrb";
    public float strength = 1.0F;
    public boolean onFire;

    public ExplosiveOrb(EntityType<? extends ExplosiveOrb> type, Level level) { super(type, level, LEGACY_CLASS); }

    public static void host(LegacyRuntime runtime) {
        Tc4Legacy.host(runtime, LEGACY_CLASS, (level, original) -> {
            var orb = Tc4Entities.EXPLOSIVE_ORB.get().create(level);
            if (orb == null) return null;
            var old = runtime.accessor(Old.class, original);
            orb.strength = old.strength(); orb.onFire = old.onFire();
            orb.setOwner(Tc4Legacy.thrower(runtime, level, original));
            return orb;
        });
    }

    @Override protected float getGravity() { return 0.01F; }
    @Override protected int lifetime() { return 500; }
    @Override public boolean isPickable() { return true; }
    @Override public boolean hurt(DamageSource source, float amount) { return deflect(source, null); }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide) level().addParticle(ParticleTypes.FLAME, xo + (random.nextFloat() - random.nextFloat()) * 0.3,
                yo + (random.nextFloat() - random.nextFloat()) * 0.3, zo + (random.nextFloat() - random.nextFloat()) * 0.3, 0, 0, 0);
    }

    @Override protected void onHit(HitResult hit) {
        if (!level().isClientSide) {
            if (hit instanceof EntityHitResult entityHit)
                entityHit.getEntity().hurt(Tc4Legacy.damage(level(), DamageTypes.FIREBALL, this, getOwner() == null ? this : getOwner()), strength * 1.5F);
            level().explode(null, getX(), getY(), getZ(), strength, onFire, Level.ExplosionInteraction.NONE);
        }
        discard();
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putFloat("strength", strength); tag.putBoolean("onFire", onFire); }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); if (tag.contains("strength")) strength = tag.getFloat("strength"); onFire = tag.getBoolean("onFire");
    }

    @LegacyType(LEGACY_CLASS) public interface Old {
        @LegacyField(name = "strength", descriptor = "F") float strength();
        @LegacyField(name = "onFire", descriptor = "Z") boolean onFire();
    }
}
