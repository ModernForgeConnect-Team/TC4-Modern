package github.com.gengyoubo.tcmodern.tc4.entity;

import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.tc4.entity.projectile.GolemOrb;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The eldritch golem: smashes through soft blocks and loot urns, and when a blow would kill it loses its head instead,
 * then fights on with charged bursts of homing orbs and sparks from the open neck.
 */
public final class EldritchGolem extends Tc4Boss implements RangedAttackMob, EldritchMob {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.monster.boss.EntityEldritchGolem";
    private static final EntityDataAccessor<Boolean> HEADLESS = SynchedEntityData.defineId(EldritchGolem.class, EntityDataSerializers.BOOLEAN);
    private int beamCharge, attackTimer;
    private boolean chargingBeam, rangedAdded;
    public int arcing;
    public BlockPos arcTarget = BlockPos.ZERO;

    public EldritchGolem(EntityType<? extends EldritchGolem> type, Level level) { super(type, level, LEGACY_CLASS); bossBar.setColor(net.minecraft.world.BossEvent.BossBarColor.WHITE); }

    public static AttributeSupplier.Builder attributes() {
        return bossAttributes().add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.ATTACK_DAMAGE, 10).add(Attributes.MAX_HEALTH, 250);
    }

    public static void host(LegacyRuntime runtime) {
        Tc4Legacy.host(runtime, LEGACY_CLASS, (level, original) -> {
            var golem = Tc4Entities.ELDRITCH_GOLEM.get().create(level);
            if (golem == null) return null;
            Tc4Legacy.copyHome(runtime, original, golem);
            if (runtime.accessor(Old.class, original).headless()) golem.makeHeadless();
            golem.spawnTimer = 100;
            return golem;
        });
    }

    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(HEADLESS, false); }
    public boolean isHeadless() { return entityData.get(HEADLESS); }
    public int attackTimer() { return attackTimer; }

    private void makeHeadless() {
        entityData.set(HEADLESS, true);
        if (!rangedAdded) { rangedAdded = true; goalSelector.addGoal(2, new Tc4Goals.LongRangeAttack<>(this, 3.0, 1.0, 5, 5, 24.0F)); }
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1, false));
        goalSelector.addGoal(6, new MoveTowardsRestrictionGoal(this, 0.8));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
            @Nullable SpawnGroupData group, @Nullable CompoundTag tag) {
        spawnTimer = 100;
        return super.finalizeSpawn(level, difficulty, reason, group, tag);
    }

    @Override public float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) { return entityData == null || !isHeadless() ? 3.0F : 3.33F; }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) { super.onSyncedDataUpdated(key); if (HEADLESS.equals(key)) refreshDimensions(); }
    @Override public int getArmorValue() { return super.getArmorValue() + 6; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.IRON_GOLEM_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.IRON_GOLEM_DEATH; }
    @Override protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.IRON_GOLEM_STEP, 1.0F, 1.0F); }

    @Override public void aiStep() {
        super.aiStep();
        if (attackTimer > 0) attackTimer--;
        var motion = getDeltaMovement();
        if (motion.x * motion.x + motion.z * motion.z > 2.5000003E-7F && random.nextInt(5) == 0) {
            var below = BlockPos.containing(getX(), getY() - 0.2, getZ());
            var state = level().getBlockState(below);
            if (!state.isAir()) level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), getX() + (random.nextFloat() - 0.5) * getBbWidth(),
                    getBoundingBox().minY + 0.1, getZ() + (random.nextFloat() - 0.5) * getBbWidth(), 4.0 * (random.nextFloat() - 0.5), 0.5, (random.nextFloat() - 0.5) * 4.0);
            if (!level().isClientSide && isLootBlock(state)) level().destroyBlock(below, true, this);
        }
        if (!level().isClientSide && net.minecraftforge.event.ForgeEventFactory.getMobGriefingEvent(level(), this)) {
            var ahead = BlockPos.containing(getX() + motion.x, getBoundingBox().minY, getZ() + motion.z);
            var state = level().getBlockState(ahead);
            float hardness = state.getDestroySpeed(level(), ahead);
            if (!state.isAir() && hardness >= 0.0F && hardness <= 0.15F) level().destroyBlock(ahead, true, this);
        }
    }

    private static boolean isLootBlock(BlockState state) {
        for (int meta = 0; meta < 3; meta++) {
            var urn = Tc4Legacy.block("Thaumcraft:blockLootUrn", meta);
            var crate = Tc4Legacy.block("Thaumcraft:blockLootCrate", meta);
            if (urn != null && state.is(urn.getBlock()) || crate != null && state.is(crate.getBlock())) return true;
        }
        return false;
    }

    @Override public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && amount > getHealth() && !isHeadless() && !isInvulnerableTo(source)) {
            makeHeadless();
            spawnTimer = 100;
            float yaw = getYRot() % 360.0F * Mth.DEG_TO_RAD;
            level().explode(this, getX() + Mth.cos(yaw) * 0.75, getY() + getEyeHeight(), getZ() + Mth.sin(yaw) * 0.75, 2.0F, Level.ExplosionInteraction.NONE);
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override public boolean doHurtTarget(Entity target) {
        if (attackTimer > 0) return false;
        attackTimer = 10;
        level().broadcastEntityEvent(this, (byte) 4);
        boolean hit = target.hurt(damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.75F);
        if (hit) {
            target.setDeltaMovement(target.getDeltaMovement().add(0, 0.2, 0));
            if (isHeadless()) {
                float yaw = getYRot() * Mth.DEG_TO_RAD;
                target.push(-Mth.sin(yaw) * 1.5, 0.1, Mth.cos(yaw) * 1.5);
            }
            target.hurtMarked = true;
        }
        return hit;
    }

    @Override public void performRangedAttack(LivingEntity target, float power) {
        if (!hasLineOfSight(target) || chargingBeam || beamCharge <= 0) return;
        beamCharge -= 15 + random.nextInt(5);
        getLookControl().setLookAt(target.getX(), target.getBoundingBox().minY + target.getBbHeight() / 2.0, target.getZ(), 30.0F, 30.0F);
        var look = getViewVector(1.0F);
        var orb = GolemOrb.create(level(), this, target, false);
        orb.setPos(orb.getX() + look.x, orb.getY(), orb.getZ() + look.z);
        var motion = target.getDeltaMovement();
        orb.heading(target.getX() + motion.x - getX(), target.getY() - getY() - target.getBbHeight() / 2.0F, target.getZ() + motion.z - getZ(), 0.66F, 5.0F);
        playSound(Tc4Legacy.sound("egattack"), 1.0F, 1.0F + random.nextFloat() * 0.1F);
        level().addFreshEntity(orb);
    }

    @Override public void handleEntityEvent(byte event) {
        if (event == 4) { attackTimer = 10; playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.0F, 1.0F); }
        else if (event == 18) spawnTimer = 150;
        else if (event == 19) startArc();
        else super.handleEntityEvent(event);
    }

    /** The open neck arcs lightning to a nearby block while the beam charges. */
    private void startArc() {
        if (arcing != 0) return;
        float radius = 2.0F + random.nextFloat() * 2.0F;
        double radians = Math.toRadians(random.nextInt(360));
        int bx = Mth.floor(getX() + radius * Math.cos(radians)), by = Mth.floor(getY()), bz = Mth.floor(getZ() + radius * Math.sin(radians));
        for (int c = 0; c < 5 && level().isEmptyBlock(new BlockPos(bx, by, bz)); by--) c++;
        if (level().isEmptyBlock(new BlockPos(bx, by + 1, bz)) && !level().isEmptyBlock(new BlockPos(bx, by, bz))) {
            arcTarget = new BlockPos(bx, by, bz);
            arcing = 8 + random.nextInt(5);
            level().playLocalSound(getX(), getY(), getZ(), Tc4Legacy.sound("jacobs"), getSoundSource(), 0.8F, 1.0F + (random.nextFloat() - random.nextFloat()) * 0.05F, false);
        }
    }

    @Override public void tick() {
        if (spawnTimer == 150 && !level().isClientSide) level().broadcastEntityEvent(this, (byte) 18);
        if (spawnTimer > 0) heal(2.0F);
        super.tick();
        if (level().isClientSide) {
            if (!isHeadless()) return;
            setXRot(0.0F);
            float body = -yBodyRot * Mth.DEG_TO_RAD - Mth.PI;
            double nx = Mth.sin(body) * -1.0, nz = Mth.cos(body) * -1.0;
            if (random.nextInt(20) == 0) level().addParticle(ParticleTypes.ELECTRIC_SPARK, getX() + nx + (random.nextFloat() - random.nextFloat()) / 2.0,
                    getY() + getEyeHeight() - 0.25, getZ() + nz + (random.nextFloat() - random.nextFloat()) / 2.0, 0, 0, 0);
            level().addParticle(ParticleTypes.SMOKE, getX() + nx * 0.66, getY() + getEyeHeight() - 0.75, getZ() + nz * 0.66, 0, 0.05, 0);
            if (arcing > 0) {
                var from = position().add(0, getBbHeight() / 2.0, 0);
                var to = net.minecraft.world.phys.Vec3.atBottomCenterOf(arcTarget.above());
                for (int i = 0; i <= 6; i++) {
                    var at = from.lerp(to, i / 6.0);
                    level().addParticle(ParticleTypes.ELECTRIC_SPARK, at.x + (random.nextFloat() - 0.5) * 0.2, at.y + (random.nextFloat() - 0.5) * 0.2, at.z + (random.nextFloat() - 0.5) * 0.2, 0, 0, 0);
                }
                arcing--;
            }
        } else {
            if (isHeadless() && beamCharge <= 0) chargingBeam = true;
            if (isHeadless() && chargingBeam) {
                beamCharge++;
                level().broadcastEntityEvent(this, (byte) 19);
                if (beamCharge == 150) chargingBeam = false;
            }
        }
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putBoolean("headless", isHeadless()); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); if (tag.getBoolean("headless")) makeHeadless(); }

    @LegacyType(LEGACY_CLASS) public interface Old { @LegacyMethod(name = "isHeadless", descriptor = "()Z") boolean headless(); }
}
