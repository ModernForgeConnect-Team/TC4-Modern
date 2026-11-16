package github.com.gengyoubo.tcmodern.tc4.entity.projectile;

import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.mla.LegacyField;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.tc4.entity.Tc4Legacy;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** The frost focus shard: bounces off blocks and creatures, slows what it hits and shatters after its bounces. */
public final class FrostShard extends Tc4Throwable {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.projectile.EntityFrostShard";
    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(FrostShard.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> FROSTY = SynchedEntityData.defineId(FrostShard.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FRAGILE = SynchedEntityData.defineId(FrostShard.class, EntityDataSerializers.BOOLEAN);
    public double bounce = 0.5;
    public int bounceLimit = 3;

    public FrostShard(EntityType<? extends FrostShard> type, Level level) { super(type, level, LEGACY_CLASS); }

    public static void host(LegacyRuntime runtime) {
        Tc4Legacy.host(runtime, LEGACY_CLASS, (level, original) -> {
            var shard = Tc4Entities.FROST_SHARD.get().create(level);
            if (shard == null) return null;
            var old = runtime.accessor(Old.class, original);
            shard.setDamage(old.damage()); shard.setFrosty(old.frosty()); shard.setFragile(old.fragile());
            shard.bounce = old.bounce(); shard.bounceLimit = old.bounceLimit();
            shard.setOwner(Tc4Legacy.thrower(runtime, level, original));
            return shard;
        });
    }

    @Override protected void defineSynchedData() { entityData.define(DAMAGE, 0.0F); entityData.define(FROSTY, 0); entityData.define(FRAGILE, false); }
    public float damage() { return entityData.get(DAMAGE); }
    public void setDamage(float damage) { entityData.set(DAMAGE, damage); refreshDimensions(); }
    public int frosty() { return entityData.get(FROSTY); }
    public void setFrosty(int frosty) { entityData.set(FROSTY, frosty); }
    public boolean fragile() { return entityData.get(FRAGILE); }
    public void setFragile(boolean fragile) { entityData.set(FRAGILE, fragile); }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) { super.onSyncedDataUpdated(key); if (DAMAGE.equals(key)) refreshDimensions(); }
    @Override public EntityDimensions getDimensions(Pose pose) { float size = 0.15F + damage() * 0.15F; return EntityDimensions.scalable(size, size); }
    @Override protected float getGravity() { return fragile() ? 0.015F : 0.05F; }

    @Override protected void onHit(HitResult hit) {
        Vec3 motion = getDeltaMovement();
        double mx = motion.x, my = motion.y, mz = motion.z;
        if (hit instanceof EntityHitResult entityHit) {
            var target = entityHit.getEntity();
            if (Mth.floor(getZ()) != Mth.floor(target.getZ())) mz = -mz;
            if (Mth.floor(getX()) != Mth.floor(target.getX())) mx = -mx;
            if (Mth.floor(getY()) != Mth.floor(target.getY())) my *= -0.9;
            mx *= 0.66; my *= 0.66; mz *= 0.66;
            burst(Blocks.ICE.defaultBlockState(), (int) damage());
        } else if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            var face = blockHit.getDirection();
            if (face.getStepZ() != 0) mz = -mz;
            if (face.getStepX() != 0) mx = -mx;
            if (face.getStepY() != 0) my *= -0.9;
            var state = level().getBlockState(blockHit.getBlockPos());
            playSound(state.getSoundType().getBreakSound(), 0.3F, 1.2F / (random.nextFloat() * 0.2F + 0.9F));
            burst(state, (int) damage());
        }
        mx *= bounce; my *= bounce; mz *= bounce;
        setDeltaMovement(mx, my, mz);
        double length = Math.sqrt(mx * mx + my * my + mz * mz);
        if (length > 0) setPos(getX() - mx / length * 0.05, getY() - my / length * 0.05, getZ() - mz / length * 0.05);
        if (!level().isClientSide && hit instanceof EntityHitResult entityHit) {
            var target = entityHit.getEntity();
            Vec3 before = target.getDeltaMovement();
            target.hurt(damageSources().thrown(this, getOwner()), damage());
            if (target instanceof LivingEntity living && frosty() > 0) living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, frosty() - 1));
            if (fragile()) {
                target.invulnerableTime = 0;
                discard();
                playSound(SoundEvents.GLASS_BREAK, 0.3F, 1.2F / (random.nextFloat() * 0.2F + 0.9F));
                target.setDeltaMovement(before.add(target.getDeltaMovement().subtract(before).scale(0.1)));
            }
        }
        if (bounceLimit-- <= 0) {
            discard();
            playSound(SoundEvents.GLASS_BREAK, 0.3F, 1.2F / (random.nextFloat() * 0.2F + 0.9F));
            burst(Blocks.ICE.defaultBlockState(), (int) (8 * damage()));
        }
    }

    private void burst(net.minecraft.world.level.block.state.BlockState state, int count) {
        if (!(level() instanceof ServerLevel server) || count <= 0) return;
        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), getX(), getY(), getZ(), count, 0.1, 0.1, 0.1, 0.15);
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide && frosty() > 0) {
            float s = damage() / 10.0F;
            for (int a = 0; a < frosty(); a++) level().addParticle(ParticleTypes.SNOWFLAKE,
                    getX() - s + random.nextFloat() * s * 2, getY() - s + random.nextFloat() * s * 2, getZ() - s + random.nextFloat() * s * 2, 0, 0, 0);
        }
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("damage", damage()); tag.putBoolean("fragile", fragile()); tag.putInt("frost", frosty());
        tag.putDouble("bounce", bounce); tag.putInt("bounceLimit", bounceLimit);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setDamage(tag.getFloat("damage")); setFragile(tag.getBoolean("fragile")); setFrosty(tag.getInt("frost"));
        if (tag.contains("bounce")) bounce = tag.getDouble("bounce");
        if (tag.contains("bounceLimit")) bounceLimit = tag.getInt("bounceLimit");
    }

    @LegacyType(LEGACY_CLASS) public interface Old {
        @LegacyMethod(name = "getDamage", descriptor = "()F") float damage();
        @LegacyMethod(name = "getFrosty", descriptor = "()I") int frosty();
        @LegacyField(name = "fragile", descriptor = "Z") boolean fragile();
        @LegacyField(name = "bounce", descriptor = "D") double bounce();
        @LegacyField(name = "bounceLimit", descriptor = "I") int bounceLimit();
    }
}
