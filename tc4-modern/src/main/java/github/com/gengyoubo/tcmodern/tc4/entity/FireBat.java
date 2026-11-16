package github.com.gengyoubo.tcmodern.tc4.entity;

import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.mla.LegacyField;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The firebat of the Nether, also summoned by the hellbat focus. Summoned bats fight for their caster and burn out
 * without a target; devil bats are tougher, vampire bats heal their master, explosive bats burst on contact.
 */
public final class FireBat extends Monster {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.monster.EntityFireBat";
    private static final EntityDataAccessor<Byte> FLAGS = SynchedEntityData.defineId(FireBat.class, EntityDataSerializers.BYTE);
    private static final int HANGING = 0, SUMMONED = 1, EXPLOSIVE = 2, DEVIL = 3, VAMPIRE = 4;
    private final LegacyHost legacy = new LegacyHost(LEGACY_CLASS);
    @Nullable private BlockPos flightTarget;
    @Nullable public Player owner;
    public int damBonus;
    private int attackTime;

    public FireBat(EntityType<? extends FireBat> type, Level level) { super(type, level); setFlag(HANGING, true); }

    public static AttributeSupplier.Builder attributes() { return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 5).add(Attributes.ATTACK_DAMAGE, 1); }

    public static void host(LegacyRuntime runtime) {
        Tc4Legacy.host(runtime, LEGACY_CLASS, (level, original) -> {
            var bat = Tc4Entities.FIRE_BAT.get().create(level);
            if (bat == null) return null;
            var old = runtime.accessor(Old.class, original);
            bat.damBonus = old.damBonus();
            bat.setFlag(HANGING, old.hanging()); bat.setFlag(SUMMONED, old.summoned()); bat.setFlag(EXPLOSIVE, old.explosive());
            bat.setFlag(DEVIL, old.devil()); bat.setFlag(VAMPIRE, old.vampire());
            if (Tc4Legacy.hostOf(runtime, level, old.owner()) instanceof Player player) bat.owner = player;
            if (Tc4Legacy.hostOf(runtime, level, old.target()) instanceof LivingEntity target) bat.setTarget(target);
            bat.refreshStats();
            return bat;
        });
    }

    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(FLAGS, (byte) 0); }
    private boolean flag(int bit) { return (entityData.get(FLAGS) & 1 << bit) != 0; }
    private void setFlag(int bit, boolean value) {
        byte flags = entityData.get(FLAGS);
        entityData.set(FLAGS, (byte) (value ? flags | 1 << bit : flags & ~(1 << bit)));
    }
    public boolean isHanging() { return flag(HANGING); }
    public boolean isSummoned() { return flag(SUMMONED); }
    public boolean isExplosive() { return flag(EXPLOSIVE); }
    public boolean isDevil() { return flag(DEVIL); }
    public boolean isVampire() { return flag(VAMPIRE); }
    public void setSummoned(boolean value) { setFlag(SUMMONED, value); refreshStats(); }
    public void setDevil(boolean value) { setFlag(DEVIL, value); refreshStats(); }
    public void setExplosive(boolean value) { setFlag(EXPLOSIVE, value); }
    public void setVampire(boolean value) { setFlag(VAMPIRE, value); }

    private void refreshStats() {
        var health = getAttribute(Attributes.MAX_HEALTH);
        if (health != null && health.getBaseValue() != (isDevil() ? 15 : 5)) { health.setBaseValue(isDevil() ? 15 : 5); setHealth(getMaxHealth()); }
        var attack = getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) attack.setBaseValue(isSummoned() ? (isDevil() ? 3 : 2) + damBonus : 1);
    }

    @Override public float getLightLevelDependentMagicValue() { return 1.0F; }
    @Override protected float getSoundVolume() { return 0.1F; }
    @Override public float getVoicePitch() { return super.getVoicePitch() * 0.95F; }
    @Override @Nullable protected SoundEvent getAmbientSound() { return isHanging() && random.nextInt(4) != 0 ? null : SoundEvents.BAT_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.BAT_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.BAT_DEATH; }
    @Override public boolean isPushable() { return false; }
    @Override protected void doPush(Entity entity) { }
    @Override protected void pushEntities() { }
    @Override public boolean isIgnoringBlockTriggers() { return true; }
    @Override protected MovementEmission getMovementEmission() { return MovementEmission.EVENTS; }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }
    @Override protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) { }
    @Override protected void registerGoals() { }

    @Override public void aiStep() {
        if (isInWaterRainOrBubble()) hurt(damageSources().drown(), 1.0F);
        super.aiStep();
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide && isExplosive())
            level().addParticle(ParticleTypes.LAVA, xo + (random.nextFloat() - random.nextFloat()) * 0.1, yo + getBbHeight() / 2 + (random.nextFloat() - random.nextFloat()) * 0.1,
                    zo + (random.nextFloat() - random.nextFloat()) * 0.1, 0, 0, 0);
        if (isHanging()) {
            setDeltaMovement(Vec3.ZERO);
            setPosRaw(getX(), Mth.floor(getY()) + 1.0 - getBbHeight(), getZ());
        } else setDeltaMovement(getDeltaMovement().multiply(1, 0.6, 1));
        if (level().isClientSide && !isVampire()) {
            double x = xo + (random.nextFloat() - random.nextFloat()) * 0.2, y = yo + getBbHeight() / 2 + (random.nextFloat() - random.nextFloat()) * 0.2,
                    z = zo + (random.nextFloat() - random.nextFloat()) * 0.2;
            level().addParticle(ParticleTypes.SMOKE, x, y, z, 0, 0, 0);
            level().addParticle(ParticleTypes.FLAME, x, y, z, 0, 0, 0);
        }
        legacy.tick(this);
    }

    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        if (attackTime > 0) attackTime--;
        var above = BlockPos.containing(getX(), getY() + 1, getZ());
        if (isHanging()) {
            if (!level().getBlockState(above).isRedstoneConductor(level(), above) || level().getNearestPlayer(this, 4.0) != null) {
                setFlag(HANGING, false);
                level().levelEvent(null, 1025, blockPosition(), 0);
            } else if (random.nextInt(200) == 0) yHeadRot = random.nextInt(360);
            return;
        }
        if (getTarget() == null && !isSummoned()) {
            Player player = level().getNearestPlayer(this, 12.0);
            if (player != null && !player.isCreative() && !player.isSpectator()) setTarget(player);
        }
        var target = getTarget();
        if (target != null && (!target.isAlive() || target instanceof Player p && (p.isCreative() || p.isSpectator()))) { setTarget(null); target = null; }
        Vec3 goal;
        if (target == null) {
            if (isSummoned()) { hurt(damageSources().generic(), 2.0F); return; }
            if (flightTarget != null && (!level().isEmptyBlock(flightTarget) || flightTarget.getY() < level().getMinBuildHeight() + 1)) flightTarget = null;
            if (flightTarget == null || random.nextInt(30) == 0 || flightTarget.closerToCenterThan(position(), 2.0))
                flightTarget = BlockPos.containing(getX() + random.nextInt(7) - random.nextInt(7), getY() + random.nextInt(6) - 2, getZ() + random.nextInt(7) - random.nextInt(7));
            goal = new Vec3(flightTarget.getX() + 0.5, flightTarget.getY() + 0.1, flightTarget.getZ() + 0.5);
            if (random.nextInt(100) == 0 && level().getBlockState(above).isRedstoneConductor(level(), above)) setFlag(HANGING, true);
        } else {
            goal = new Vec3(target.getX(), target.getY() + target.getEyeHeight() * 0.66, target.getZ());
            if (hasLineOfSight(target)) attack(target);
        }
        var motion = getDeltaMovement();
        double dx = goal.x - getX(), dy = goal.y - getY(), dz = goal.z - getZ();
        motion = new Vec3(motion.x + (Math.signum(dx) * 0.5 - motion.x) * 0.1, motion.y + (Math.signum(dy) * 0.7 - motion.y) * 0.1, motion.z + (Math.signum(dz) * 0.5 - motion.z) * 0.1);
        setDeltaMovement(motion);
        float yaw = (float) (Mth.atan2(motion.z, motion.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(getYRot() + Mth.wrapDegrees(yaw - getYRot()));
        zza = 0.5F;
    }

    private void attack(LivingEntity target) {
        float distance = distanceTo(target);
        if (attackTime > 0 || distance >= Math.max(2.5F, target.getBbWidth() * 1.1F)
                || target.getBoundingBox().maxY <= getBoundingBox().minY || target.getBoundingBox().minY >= getBoundingBox().maxY) return;
        if (isSummoned() && owner != null) target.setLastHurtByPlayer(owner);
        if (isVampire()) {
            if (owner != null && !owner.hasEffect(MobEffects.REGENERATION)) owner.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 26, 1));
            heal(1.0F);
        }
        attackTime = 20;
        if ((isExplosive() || random.nextInt(10) == 0) && !isDevil()) {
            target.invulnerableTime = 0;
            level().explode(this, getX(), getY(), getZ(), 1.5F + (isExplosive() ? damBonus * 0.33F : 0.0F), false, Level.ExplosionInteraction.NONE);
            discard();
        } else if (!isVampire() && !random.nextBoolean()) {
            target.setSecondsOnFire(isSummoned() ? 4 : 2);
        } else {
            Vec3 motion = target.getDeltaMovement();
            doHurtTarget(target);
            target.hasImpulse = false;
            target.setDeltaMovement(motion);
        }
        playSound(SoundEvents.BAT_HURT, 0.5F, 0.9F + random.nextFloat() * 0.2F);
    }

    @Override public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION)) return false;
        if (!level().isClientSide && isHanging()) setFlag(HANGING, false);
        return super.hurt(source, amount);
    }

    @Override protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        if (isSummoned()) return;
        int count = random.nextInt(3) + (looting > 0 ? random.nextInt(looting + 1) : 0);
        if (count > 0) spawnAtLocation(new net.minecraft.world.item.ItemStack(Items.GUNPOWDER, count));
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag); tag.putByte("BatFlags", entityData.get(FLAGS)); tag.putByte("damBonus", (byte) damBonus); legacy.save(this, tag);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); entityData.set(FLAGS, tag.getByte("BatFlags")); damBonus = tag.getByte("damBonus"); refreshStats(); legacy.load(tag);
    }

    @LegacyType(LEGACY_CLASS) public interface Old {
        @LegacyField(name = "damBonus", descriptor = "I") int damBonus();
        @LegacyField(name = "owner", descriptor = "Lnet/minecraft/entity/player/EntityPlayer;") LegacyObject owner();
        @LegacyField(name = "field_70789_a", descriptor = "Lnet/minecraft/entity/Entity;") LegacyObject target();
        @github.com.gengyoubo.mfcl.api.mla.LegacyMethod(name = "getIsBatHanging", descriptor = "()Z") boolean hanging();
        @github.com.gengyoubo.mfcl.api.mla.LegacyMethod(name = "getIsSummoned", descriptor = "()Z") boolean summoned();
        @github.com.gengyoubo.mfcl.api.mla.LegacyMethod(name = "getIsExplosive", descriptor = "()Z") boolean explosive();
        @github.com.gengyoubo.mfcl.api.mla.LegacyMethod(name = "getIsDevil", descriptor = "()Z") boolean devil();
        @github.com.gengyoubo.mfcl.api.mla.LegacyMethod(name = "getIsVampire", descriptor = "()Z") boolean vampire();
    }
}
