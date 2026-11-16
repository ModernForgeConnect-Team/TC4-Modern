package github.com.gengyoubo.tcmodern.tc4.entity;

import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The thaumic slime: a tainted slime that grows by absorbing others, spits small slimes at distant players and
 * splits apart when killed.
 */
public final class ThaumicSlime extends Monster {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.monster.EntityThaumicSlime";
    private static final EntityDataAccessor<Integer> SIZE = SynchedEntityData.defineId(ThaumicSlime.class, EntityDataSerializers.INT);
    private final LegacyHost legacy = new LegacyHost(LEGACY_CLASS);
    public float squish, previousSquish, targetSquish;
    private int jumpDelay;
    private int launched = 10;
    private int spitCounter = 100;

    public ThaumicSlime(EntityType<? extends ThaumicSlime> type, Level level) {
        super(type, level);
        jumpDelay = random.nextInt(20) + 10;
        setSlimeSize(1 << random.nextInt(3));
    }

    public static AttributeSupplier.Builder attributes() { return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 1).add(Attributes.MOVEMENT_SPEED, 0.7); }

    public static void host(LegacyRuntime runtime) {
        Tc4Legacy.host(runtime, LEGACY_CLASS, (level, original) -> {
            var slime = Tc4Entities.THAUMIC_SLIME.get().create(level);
            if (slime != null) slime.setSlimeSize(runtime.accessor(Old.class, original).size());
            return slime;
        });
    }

    /** EntityThaumicSlime(world, spitter, target): a size-1 slime flung from the spitter at its target. */
    static ThaumicSlime spit(ThaumicSlime spitter, LivingEntity target) {
        var slime = new ThaumicSlime(Tc4Entities.THAUMIC_SLIME.get(), spitter.level());
        slime.setSlimeSize(1);
        double y = (spitter.getBoundingBox().minY + spitter.getBoundingBox().maxY) / 2.0;
        double dx = target.getX() - spitter.getX(), dy = target.getBoundingBox().minY + target.getBbHeight() / 3.0 - y, dz = target.getZ() - spitter.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        slime.moveTo(spitter.getX(), y, spitter.getZ(), 0, 0);
        if (horizontal >= 1.0E-7) {
            slime.moveTo(spitter.getX() + dx / horizontal, y, spitter.getZ() + dz / horizontal,
                    (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F, (float) -(Mth.atan2(dy, horizontal) * Mth.RAD_TO_DEG));
            double length = Math.sqrt(dx * dx + (dy + horizontal * 0.2) * (dy + horizontal * 0.2) + dz * dz);
            slime.setDeltaMovement(dx / length * 1.5, (dy + horizontal * 0.2) / length * 1.5, dz / length * 1.5);
        }
        return slime;
    }

    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(SIZE, 1); }
    public int slimeSize() { return entityData.get(SIZE); }
    public void setSlimeSize(int size) {
        size = Math.max(1, Math.min(100, size));
        entityData.set(SIZE, size);
        refreshDimensions();
        reapplyPosition();
        var health = getAttribute(Attributes.MAX_HEALTH);
        if (health != null) health.setBaseValue(size);
        setHealth(getMaxHealth());
        xpReward = (int) Math.sqrt(size);
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) { super.onSyncedDataUpdated(key); if (SIZE.equals(key)) refreshDimensions(); }
    @Override public EntityDimensions getDimensions(Pose pose) { float s = 0.25F * Mth.sqrt(slimeSize()) + 0.25F; return EntityDimensions.scalable(s, s); }

    @Override public void tick() {
        if (!level().isClientSide && level().getDifficulty() == Difficulty.PEACEFUL) { discard(); return; }
        squish += (targetSquish - squish) * 0.5F;
        previousSquish = squish;
        boolean wasOnGround = onGround();
        super.tick();
        int i = (int) Math.sqrt(slimeSize());
        if (launched > 0) {
            launched--;
            if (level().isClientSide) for (int j = 0; j < i * (launched + 1); j++) slimeParticle(i);
        }
        if (onGround() && !wasOnGround) {
            if (level().isClientSide) for (int j = 0; j < i * 8; j++) slimeParticle(i);
            if (slimeSize() > 5) playSound(jumpSound(), getSoundVolume(), ((random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F) / 0.8F);
            targetSquish = -0.5F;
        } else if (!onGround() && wasOnGround) targetSquish = 1.0F;
        targetSquish *= 0.6F;
        legacy.tick(this);
    }

    private void slimeParticle(int size) {
        float angle = random.nextFloat() * Mth.TWO_PI, spread = random.nextFloat() * 0.5F + 0.5F;
        level().addParticle(ParticleTypes.WITCH, getX() + Mth.sin(angle) * size * 0.25F * spread, getBoundingBox().minY, getZ() + Mth.cos(angle) * size * 0.25F * spread, 0, 0, 0);
    }

    @Nullable private ThaumicSlime mergeable() {
        ThaumicSlime closest = null;
        double distance = Double.MAX_VALUE;
        for (var slime : level().getEntitiesOfClass(ThaumicSlime.class, getBoundingBox().inflate(16, 8, 16))) {
            if (slime == this || slime.tickCount <= 100 || slime.slimeSize() >= 100) continue;
            double d = distanceToSqr(slime);
            if (d < distance) { distance = d; closest = slime; }
        }
        return closest;
    }

    @Override protected void customServerAiStep() {
        Player player = level().getNearestPlayer(this, 16.0);
        if (player != null && (player.isCreative() || player.isSpectator())) player = null;
        if (player != null) {
            if (spitCounter > 0) spitCounter--;
            lookAt(player, 10.0F, 20.0F);
            if (distanceTo(player) > 4.0F && spitCounter <= 0 && slimeSize() > 3) {
                spitCounter = 101;
                level().addFreshEntity(spit(this, player));
                playSound(Tc4Legacy.sound("gore"), 1.0F, ((random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F) * 0.8F);
                setSlimeSize(slimeSize() - 1);
            }
        } else {
            var slime = mergeable();
            if (slime != null) {
                lookAt(slime, 10.0F, 20.0F);
                if (distanceTo(slime) < getBbWidth() + slime.getBbWidth()) {
                    slime.setSlimeSize(Math.min(100, slime.slimeSize() + slimeSize()));
                    discard();
                    return;
                }
            }
        }
        if (onGround() && jumpDelay-- <= 0) {
            jumpDelay = random.nextInt(16) + 8;
            if (player != null) jumpDelay /= 3;
            if (slimeSize() > 3) playSound(jumpSound(), getSoundVolume(), ((random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F) * 0.8F);
            float forward = Mth.sqrt(slimeSize()) * 0.1F, strafe = (1.0F - random.nextFloat() * 2.0F) * 0.1F;
            float yaw = getYRot() * Mth.DEG_TO_RAD;
            setDeltaMovement(-Mth.sin(yaw) * forward + Mth.cos(yaw) * strafe, getJumpPower(), Mth.cos(yaw) * forward + Mth.sin(yaw) * strafe);
            hasImpulse = true;
        }
    }

    @Override protected float getJumpPower() { return 0.42F; }

    @Override public void remove(RemovalReason reason) {
        int i = (int) Math.sqrt(slimeSize());
        if (!level().isClientSide && i > 1 && isDeadOrDying() && reason == RemovalReason.KILLED) {
            for (int k = 0; k < i; k++) {
                float f = ((k % 2) - 0.5F) * i / 4.0F, f1 = ((k / 2) - 0.5F) * i / 4.0F;
                var child = new ThaumicSlime(Tc4Entities.THAUMIC_SLIME.get(), level());
                child.setSlimeSize(1);
                child.moveTo(getX() + f, getY() + 0.5, getZ() + f1, random.nextFloat() * 360.0F, 0.0F);
                level().addFreshEntity(child);
            }
        }
        super.remove(reason);
    }

    @Override public void playerTouch(Player player) {
        if (slimeSize() <= 0 || level().isClientSide) return;
        int i = (int) Math.max(1.0, Math.sqrt(slimeSize()));
        if (launched > 0 && i == 2) i = 3;
        double reach = 0.8 * i;
        if (hasLineOfSight(player) && distanceToSqr(player) < reach * reach && player.hurt(damageSources().mobAttack(this), slimeSize()))
            playSound(SoundEvents.SLIME_ATTACK, 1.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F);
    }

    @Override protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        if (slimeSize() < 3 && random.nextInt(3) == 0) Tc4Legacy.drop(this, Tc4Legacy.stack("Thaumcraft:ItemResource", 11, 1), getBbHeight() / 2.0F);
    }

    private SoundEvent jumpSound() { return slimeSize() > 3 ? SoundEvents.SLIME_JUMP : SoundEvents.SLIME_JUMP_SMALL; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return slimeSize() > 3 ? SoundEvents.SLIME_HURT : SoundEvents.SLIME_HURT_SMALL; }
    @Override protected SoundEvent getDeathSound() { return slimeSize() > 3 ? SoundEvents.SLIME_DEATH : SoundEvents.SLIME_DEATH_SMALL; }
    @Override protected float getSoundVolume() { return 0.1F * Mth.sqrt(slimeSize()); }
    @Override public int getMaxHeadXRot() { return 0; }
    @Override public boolean isAlliedTo(Entity other) { return other instanceof ThaumicSlime || super.isAlliedTo(other); }

    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putInt("Size", slimeSize() - 1); legacy.save(this, tag); }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        float health = getHealth();
        super.readAdditionalSaveData(tag);
        setSlimeSize(tag.getInt("Size") + 1);
        if (tag.contains("Health")) setHealth(tag.getFloat("Health")); else setHealth(health);
        legacy.load(tag);
    }

    @LegacyType(LEGACY_CLASS) public interface Old { @LegacyMethod(name = "getSlimeSize", descriptor = "()I") int size(); }
}
