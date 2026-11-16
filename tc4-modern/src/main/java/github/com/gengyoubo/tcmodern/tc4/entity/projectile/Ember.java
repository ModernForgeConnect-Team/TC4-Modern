package github.com.gengyoubo.tcmodern.tc4.entity.projectile;

import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.mla.LegacyField;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.tc4.entity.Tc4Legacy;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** A short-lived flame of the fire focus: sets what it touches alight and, rarely, the block it lands on. */
public final class Ember extends Tc4Throwable {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.projectile.EntityEmber";
    private static final EntityDataAccessor<Integer> DURATION = SynchedEntityData.defineId(Ember.class, EntityDataSerializers.INT);
    public int firey;
    public float damage = 1.0F;

    public Ember(EntityType<? extends Ember> type, Level level) { super(type, level, LEGACY_CLASS); }

    public static void host(LegacyRuntime runtime) {
        Tc4Legacy.host(runtime, LEGACY_CLASS, (level, original) -> {
            var ember = Tc4Entities.EMBER.get().create(level);
            if (ember == null) return null;
            var old = runtime.accessor(Old.class, original);
            ember.setDuration(old.duration()); ember.firey = old.firey(); ember.damage = old.damage();
            ember.setOwner(Tc4Legacy.thrower(runtime, level, original));
            return ember;
        });
    }

    @Override protected void defineSynchedData() { entityData.define(DURATION, 20); }
    public int duration() { return entityData.get(DURATION); }
    public void setDuration(int duration) { entityData.set(DURATION, duration); }
    @Override protected float getGravity() { return 0.0F; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean hurt(DamageSource source, float amount) { return false; }

    @Override public void tick() {
        if (tickCount > duration()) discard();
        double drag = duration() <= 20 ? 0.95 : 0.975;
        setDeltaMovement(getDeltaMovement().scale(onGround() ? drag * 0.66 : drag));
        super.tick();
    }

    @Override protected void onHit(HitResult hit) {
        if (!level().isClientSide) {
            if (hit instanceof EntityHitResult entityHit) {
                var target = entityHit.getEntity();
                if (!target.fireImmune() && target.hurt(Tc4Legacy.damage(level(), DamageTypes.FIREBALL, this, getOwner()), damage))
                    target.setSecondsOnFire(3 + firey);
            } else if (hit instanceof BlockHitResult blockHit && random.nextFloat() < 0.025F * firey) {
                var pos = blockHit.getBlockPos().relative(blockHit.getDirection());
                if (level().isEmptyBlock(pos)) level().setBlockAndUpdate(pos, BaseFireBlock.getState(level(), pos));
            }
        }
        discard();
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag); tag.putFloat("damage", damage); tag.putInt("firey", firey); tag.putInt("duration", duration());
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); damage = tag.getFloat("damage"); firey = tag.getInt("firey"); setDuration(tag.getInt("duration"));
    }

    @LegacyType(LEGACY_CLASS) public interface Old {
        @LegacyField(name = "duration", descriptor = "I") int duration();
        @LegacyField(name = "firey", descriptor = "I") int firey();
        @LegacyField(name = "damage", descriptor = "F") float damage();
    }
}
