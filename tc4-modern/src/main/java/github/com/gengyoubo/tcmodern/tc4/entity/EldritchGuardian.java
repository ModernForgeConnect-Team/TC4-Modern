package github.com.gengyoubo.tcmodern.tc4.entity;

import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.registration.TcRegistrations;
import github.com.gengyoubo.tcmodern.tc4.entity.projectile.EldritchOrb;
import github.com.gengyoubo.tcmodern.tc4.init.Tc4Entities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobType;
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
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

/**
 * The hooded eldritch guardian: dark orbs from its hands, a warping screech, and in the Outer Lands a regenerating
 * shield. Outside it is only half there, fading in with the dark fog it brings.
 */
public final class EldritchGuardian extends Monster implements RangedAttackMob, EldritchMob {
    static final String LEGACY_CLASS = "thaumcraft.common.entities.monster.EntityEldritchGuardian";
    private final LegacyHost legacy = new LegacyHost(LEGACY_CLASS);
    public float armLiftL, armLiftR;
    private boolean lastBlast;

    public EldritchGuardian(EntityType<? extends EldritchGuardian> type, Level level) { super(type, level); xpReward = 20; }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 50).add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.MOVEMENT_SPEED, 0.28).add(Attributes.ATTACK_DAMAGE, 7);
    }

    public static void host(LegacyRuntime runtime) {
        Tc4Legacy.host(runtime, LEGACY_CLASS, (level, original) -> {
            var guardian = Tc4Entities.ELDRITCH_GUARDIAN.get().create(level);
            if (guardian == null) return null;
            Tc4Legacy.copyHome(runtime, original, guardian);
            guardian.shieldUp();
            return guardian;
        });
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new Tc4Goals.LongRangeAttack<>(this, 8.0, 1.0, 20, 40, 24.0F));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, false));
        goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 0.8));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Cultist.class, true));
    }

    private void shieldUp() {
        if (Tc4Legacy.isOuterLands(level())) setAbsorptionAmount(getAbsorptionAmount() + (int) getAttributeBaseValue(Attributes.MAX_HEALTH) / 2.0F);
    }

    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
            @Nullable SpawnGroupData group, @Nullable CompoundTag tag) {
        var result = super.finalizeSpawn(level, difficulty, reason, group, tag);
        shieldUp();
        return result;
    }

    @Override public int getArmorValue() { return 4; }
    @Override public float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) { return 2.1F; }
    @Override public boolean hurt(DamageSource source, float amount) { return super.hurt(source, source.is(DamageTypeTags.IS_PROJECTILE) ? amount / 2.0F : amount); }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (armLiftL > 0) armLiftL -= 0.05F;
            if (armLiftR > 0) armLiftR -= 0.05F;
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> github.com.gengyoubo.tcmodern.tc4.client.entity.EldritchAuraParticle.spawn(this));
        }
        legacy.tick(this);
    }

    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        if (level() instanceof ServerLevel server && !Tc4Legacy.isOuterLands(level()) && tickCount % 100 == 0
                && level().getDifficulty() != net.minecraft.world.Difficulty.EASY) {
            double range = level().getDifficulty() == net.minecraft.world.Difficulty.HARD ? 24.0 : 16.0;
            for (var player : server.players()) if (player.isAlive() && player.distanceToSqr(this) < range * range)
                github.com.gengyoubo.tcmodern.tc4.network.DeskNetwork.guardianFog(player);
        }
        if (Tc4Legacy.isOuterLands(level()) && invulnerableTime <= 0 && tickCount % 25 == 0) {
            int shield = (int) getAttributeBaseValue(Attributes.MAX_HEALTH) / 2;
            if (getAbsorptionAmount() < shield) setAbsorptionAmount(getAbsorptionAmount() + 1.0F);
        }
    }

    @Override public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            int difficulty = level().getDifficulty().getId();
            if (getMainHandItem().isEmpty() && isOnFire() && random.nextFloat() < difficulty * 0.3F) target.setSecondsOnFire(2 * difficulty);
        }
        return hit;
    }

    @Override public void performRangedAttack(LivingEntity target, float power) {
        if (random.nextFloat() > 0.1F) {
            var orb = EldritchOrb.create(level(), this);
            lastBlast = !lastBlast;
            level().broadcastEntityEvent(this, (byte) (lastBlast ? 16 : 15));
            float angle = (getYRot() + (lastBlast ? 90 : 180)) % 360.0F * Mth.DEG_TO_RAD;
            orb.setPos(orb.getX() - Mth.cos(angle) * 0.5, orb.getY() - 0.057777777 * getBbHeight(), orb.getZ() - Mth.sin(angle) * 0.5);
            var motion = target.getDeltaMovement();
            orb.heading(target.getX() + motion.x - getX(), target.getY() - getY() - target.getBbHeight() / 2.0F, target.getZ() + motion.z - getZ(), 1.0F, 2.0F);
            playSound(Tc4Legacy.sound("egattack"), 2.0F, 1.0F + random.nextFloat() * 0.1F);
            level().addFreshEntity(orb);
        } else if (hasLineOfSight(target)) screech(this, target, 400, 1, false);
    }

    /** The guardian's and warden's sonic screech: wither (and for the warden weakness and a shove), plus temporary warp. */
    static void screech(LivingEntity screamer, LivingEntity target, int wither, int warpBase, boolean warden) {
        if (screamer.level() instanceof ServerLevel server) server.sendParticles(ParticleTypes.SONIC_BOOM, screamer.getX(), screamer.getEyeY(), screamer.getZ(), 1, 0, 0, 0, 0);
        if (warden) {
            float yaw = screamer.getYRot() * Mth.DEG_TO_RAD;
            target.push(-Mth.sin(yaw) * 1.5, 0.1, Mth.cos(yaw) * 1.5);
            target.hurtMarked = true;
        }
        target.addEffect(new MobEffectInstance(MobEffects.WITHER, wither, 0));
        if (warden) target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400, 0));
        if (target instanceof Player player) {
            int warp = warpBase + screamer.getRandom().nextInt(3);
            TcModernApi.runtime().ifPresent(runtime -> {
                try { TcRegistrations.playerWarp(runtime, player, warp, true); }
                catch (RuntimeException ignored) { }
            });
        }
        screamer.playSound(Tc4Legacy.sound("egscreech"), warden ? 4.0F : 3.0F, 1.0F + screamer.getRandom().nextFloat() * 0.1F);
    }

    @Override public void handleEntityEvent(byte event) {
        switch (event) {
            case 15 -> armLiftL = 0.5F;
            case 16 -> armLiftR = 0.5F;
            case 17 -> { armLiftL = 0.9F; armLiftR = 0.9F; }
            default -> super.handleEntityEvent(event);
        }
    }

    @Override protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        if (random.nextBoolean()) Tc4Legacy.drop(this, Tc4Legacy.withAspect("Thaumcraft:ItemWispEssence", "exanimis", 2), 1.0F);
        if (random.nextBoolean()) Tc4Legacy.drop(this, Tc4Legacy.withAspect("Thaumcraft:ItemWispEssence", "alienis", 2), 1.0F);
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        if (recentlyHit && random.nextFloat() < 0.025F + looting * 0.01F) Tc4Legacy.drop(this, Tc4Legacy.stack("Thaumcraft:ItemEldritchObject", 0, 1), 0.0F);
    }

    @Override protected SoundEvent getAmbientSound() { return Tc4Legacy.sound("egidle"); }
    @Override protected SoundEvent getDeathSound() { return Tc4Legacy.sound("egdeath"); }
    @Override public int getAmbientSoundInterval() { return 500; }
    @Override protected float getSoundVolume() { return 1.5F; }
    @Override public MobType getMobType() { return MobType.UNDEAD; }
    @Override public boolean canPickUpLoot() { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return !hasRestriction(); }
    @Override public boolean isAlliedTo(Entity other) { return other instanceof EldritchMob || super.isAlliedTo(other); }

    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); Tc4Legacy.saveHome(this, tag); legacy.save(this, tag); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); Tc4Legacy.loadHome(this, tag); legacy.load(tag); }
}
