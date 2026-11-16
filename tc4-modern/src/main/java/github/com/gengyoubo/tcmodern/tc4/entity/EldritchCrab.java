package github.com.gengyoubo.tcmodern.tc4.entity;

import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The eldritch crab: leaps at its prey and clamps onto the head of whatever hurt it. Crabs released from inhabited
 * zombies wear the zombie's cultist chestplate as a shell until they are badly hurt.
 */
public final class EldritchCrab extends Monster {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.monster.EntityEldritchCrab";
    private static final EntityDataAccessor<Boolean> HELM = SynchedEntityData.defineId(EldritchCrab.class, EntityDataSerializers.BOOLEAN);
    private final LegacyHost legacy = new LegacyHost(LEGACY_CLASS);
    private int clawCooldown;

    public EldritchCrab(EntityType<? extends EldritchCrab> type, Level level) { super(type, level); xpReward = 6; }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.ATTACK_DAMAGE, 4).add(Attributes.MOVEMENT_SPEED, 0.275);
    }

    public static void host(LegacyRuntime runtime) {
        Tc4Legacy.host(runtime, LEGACY_CLASS, (level, original) -> {
            var crab = Tc4Entities.ELDRITCH_CRAB.get().create(level);
            if (crab != null) crab.setHelm(runtime.accessor(Old.class, original).helm());
            return crab;
        });
    }

    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(HELM, false); }
    public boolean hasHelm() { return entityData.get(HELM); }
    public void setHelm(boolean helm) {
        entityData.set(HELM, helm);
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(helm ? 0.275 : 0.3);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.63F));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, false));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Cultist.class, true));
    }

    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
            @Nullable SpawnGroupData group, @Nullable CompoundTag tag) {
        var result = super.finalizeSpawn(level, difficulty, reason, group, tag);
        setHelm(level.getDifficulty() == Difficulty.HARD || random.nextFloat() < 0.33F);
        if (level.getDifficulty() == Difficulty.HARD && random.nextFloat() < 0.1F * difficulty.getSpecialMultiplier()) {
            var effect = switch (random.nextInt(4)) {
                case 0 -> MobEffects.MOVEMENT_SPEED; case 1 -> MobEffects.DAMAGE_BOOST; case 2 -> MobEffects.REGENERATION; default -> MobEffects.INVISIBILITY;
            };
            addEffect(new MobEffectInstance(effect, -1));
        }
        return result;
    }

    @Override public int getArmorValue() { return super.getArmorValue() + (hasHelm() ? 5 : 0); }
    @Override public double getMyRidingOffset() { return isPassenger() ? 0.5 : 0.0; }

    @Override public void tick() {
        super.tick();
        if (tickCount < 20) fallDistance = 0;
        if (level().isClientSide) return;
        var attacker = getLastHurtByMob();
        if (getVehicle() == null && attacker != null && attacker.getPassengers().isEmpty() && !onGround() && !hasHelm() && attacker.isAlive()
                && getY() - attacker.getY() >= attacker.getBbHeight() / 2.0F && distanceToSqr(attacker) < 4.0)
            startRiding(attacker, true);
        var vehicle = getVehicle();
        if (vehicle != null && --clawCooldown <= 0) {
            clawCooldown = 10 + random.nextInt(10);
            if (!vehicle.isAlive()) stopRiding();
            else doHurtTarget(vehicle);
        }
        legacy.tick(this);
    }

    @Override public boolean doHurtTarget(Entity target) {
        if (!super.doHurtTarget(target)) return false;
        playSound(Tc4Legacy.sound("crabclaw"), 1.0F, 0.9F + random.nextFloat() * 0.2F);
        return true;
    }

    @Override public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (!level().isClientSide && hasHelm() && getHealth() / getMaxHealth() <= 0.5F) {
            setHelm(false);
            Tc4Legacy.drop(this, Tc4Legacy.stack("Thaumcraft:ItemChestplateCultistPlate", 0, 1), 0.0F);
        }
        return hurt;
    }

    @Override protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        if (recentlyHit && (random.nextInt(3) == 0 || random.nextInt(1 + looting) > 0)) spawnAtLocation(net.minecraft.world.item.Items.ENDER_PEARL);
    }

    @Override public int getAmbientSoundInterval() { return 160; }
    @Override protected SoundEvent getAmbientSound() { return Tc4Legacy.sound("crabtalk"); }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.HOSTILE_HURT; }
    @Override protected SoundEvent getDeathSound() { return Tc4Legacy.sound("crabdeath"); }
    @Override protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.SPIDER_STEP, 0.15F, 1.0F); }
    @Override public MobType getMobType() { return MobType.ARTHROPOD; }
    @Override public boolean canBeAffected(MobEffectInstance effect) { return effect.getEffect() != MobEffects.POISON && super.canBeAffected(effect); }
    @Override public boolean isAlliedTo(Entity other) { return other instanceof EldritchCrab || super.isAlliedTo(other); }

    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putBoolean("Tc4Helm", hasHelm()); legacy.save(this, tag); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); setHelm(tag.getBoolean("Tc4Helm")); legacy.load(tag); }

    @LegacyType(LEGACY_CLASS) public interface Old { @LegacyMethod(name = "hasHelm", descriptor = "()Z") boolean helm(); }
}
